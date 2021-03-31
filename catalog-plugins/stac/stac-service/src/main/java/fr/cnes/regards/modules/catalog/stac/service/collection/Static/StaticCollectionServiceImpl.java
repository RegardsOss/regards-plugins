package fr.cnes.regards.modules.catalog.stac.service.collection.Static;

import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Provider;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.service.collection.ExtentSummaryService;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.indexer.domain.aggregation.QueryableAttribute;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.service.Searches;
import fr.cnes.regards.modules.search.domain.plugin.CollectionWithStats;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.CatalogSearchService;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Try;
import org.elasticsearch.search.aggregations.Aggregation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.CHILD;
import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.ITEMS;

@Component
public class StaticCollectionServiceImpl implements IStaticCollectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaticCollectionServiceImpl.class);
    private static final String DEFAULT_STATIC_ID = "static";


    private final CatalogSearchService catalogSearchService;
    private final ExtentSummaryService extentSummaryService;

    @Autowired
    public StaticCollectionServiceImpl(
            CatalogSearchService catalogSearchService,
            ConfigurationAccessorFactory configurationAccessorFactory,
            ExtentSummaryService extentSummaryService
    ) {
        this.catalogSearchService = catalogSearchService;
        this.extentSummaryService = extentSummaryService;
    }

    @Override
    public Try<Collection> convertRequest(String urn, OGCFeatLinkCreator linkCreator, ConfigurationAccessor config) {
        return extractURN(urn)
                .flatMap(resourceName -> convertRequest(resourceName, linkCreator, config))
                .onFailure(t -> LOGGER.error(t.getMessage(), t));
    }

    private Try<UniformResourceName> extractURN(String urnStr) {
        return Try.of(() -> UniformResourceName.fromString(urnStr))
                .flatMap(urn -> {
                    if (urn.getEntityType().equals(EntityType.DATASET) ||
                            urn.getEntityType().equals(EntityType.COLLECTION)) {
                        return Try.success(urn);
                    } else {
                        return Try.failure(new RuntimeException("The entity is neither a DATASET nor a COLLECTION"));
                    }
                });
    }

    private Try<Collection> convertRequest(UniformResourceName resourceName,
                                           OGCFeatLinkCreator linkCreator,
                                           ConfigurationAccessor config) {
        return Try.of(() -> {

            String urn = resourceName.toString();

            StacProperty datetimeStacProp = config.getDatetimeStacProperty();
            List<StacProperty> stacProps = config.getStacProperties();
            List<StacProperty> nonDatetimeStacProps = stacProps.remove(datetimeStacProp);

            List<QueryableAttribute> creationDate = extentSummaryService.extentSummaryQueryableAttributes(
                    datetimeStacProp,
                    nonDatetimeStacProps
            );

            CollectionWithStats collectionWithStats = catalogSearchService.getCollectionWithDataObjectsStats(
                    resourceName,
                    SearchType.DATAOBJECTS,
                    creationDate.toJavaList()
            );


            List<Link> links = getLinks(resourceName, linkCreator, urn);

            List<Provider> providers = config.getProviders(urn)
                    .map(x -> new Provider(x.getName(), x.getDescription(), x.getUrl(), x.getRoles()));

            List<Aggregation> aggs = List.ofAll(collectionWithStats.getAggregationList());
            Map<StacProperty, Aggregation> aggregationMap = extentSummaryService.toAggregationMap(stacProps, aggs);
            Extent extent = extentSummaryService.extractExtent(aggregationMap);
            Map<String, Object> summary = extentSummaryService.extractSummary(aggregationMap);

            AbstractEntity regardsCollection = collectionWithStats.getCollection();

            Collection collection = new Collection(
                    StacSpecConstants.Version.STAC_SPEC_VERSION,
                    List.empty(),
                    regardsCollection.getLabel(),
                    regardsCollection.getId().toString(),
//                    regardsCollection.getModel().getDescription(),
                    "",
                    links,
                    config.getKeywords(urn),
                    config.getLicense(urn),
                    providers,
                    extent,
                    summary
            );

            return collection;
        });
    }

    private List<Link> getLinks(UniformResourceName resourceName, OGCFeatLinkCreator linkCreator, String urn) throws fr.cnes.regards.modules.search.service.SearchException, fr.cnes.regards.modules.opensearch.service.exception.OpenSearchUnknownParameter {
        final List<Link> links;

        if (resourceName.getEntityType().equals(EntityType.COLLECTION)) {
            List<AbstractEntity> children = getSubCollectionsOrDatasets(urn, EntityType.COLLECTION)
                    .appendAll(getSubCollectionsOrDatasets(urn, EntityType.DATASET));

            links = List.of(
                linkCreator.createRootLink(),
                getItemsLinks(resourceName, linkCreator),
                children.flatMap(child ->
                    linkCreator.createCollectionLinkWithRel(
                        child.getIpId().toString(),
                        child.getLabel(),
                        CHILD
                    )
                )
            ).flatMap(t -> t);

        } else if (resourceName.getEntityType().equals(EntityType.DATASET)) {
            links = List.of(
                linkCreator.createRootLink(),
                getItemsLinks(resourceName, linkCreator)
            ).flatMap(t -> t);
        }
        else {
            links = List.empty();
        }
        return links;
    }

    private Try<Link> getItemsLinks(UniformResourceName resourceName, OGCFeatLinkCreator linkCreator) {
        return linkCreator.createCollectionItemsLinkWithRel(resourceName.toString(), ITEMS);
    }

    private List<AbstractEntity> getSubCollectionsOrDatasets(String urn, EntityType entityType) throws fr.cnes.regards.modules.search.service.SearchException, fr.cnes.regards.modules.opensearch.service.exception.OpenSearchUnknownParameter {
        ICriterion tags = ICriterion.contains("tags", urn);
        FacetPage<AbstractEntity> subCollections = catalogSearchService.search(tags,
                Searches.onSingleEntity(entityType),
                null,
                PageRequest.of(0, 10000));
        return List.ofAll(subCollections.getContent());
    }

}
