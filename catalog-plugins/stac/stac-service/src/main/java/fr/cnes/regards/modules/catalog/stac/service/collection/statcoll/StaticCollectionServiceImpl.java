package fr.cnes.regards.modules.catalog.stac.service.collection.statcoll;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.COLLECTION_CONSTRUCTION;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.URN_PARSING;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.error;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.warn;
import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.CHILD;
import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.ITEMS;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;
import static java.lang.String.format;

import org.elasticsearch.search.aggregations.Aggregation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Provider;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.service.collection.IdMappingService;
import fr.cnes.regards.modules.catalog.stac.service.collection.ExtentSummaryService;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.indexer.domain.aggregation.QueryableAttribute;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.StringMatchType;
import fr.cnes.regards.modules.indexer.service.Searches;
import fr.cnes.regards.modules.opensearch.service.exception.OpenSearchUnknownParameter;
import fr.cnes.regards.modules.search.domain.plugin.CollectionWithStats;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.CatalogSearchService;
import fr.cnes.regards.modules.search.service.SearchException;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;

@Component
public class StaticCollectionServiceImpl implements StaticCollectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaticCollectionServiceImpl.class);

    private final CatalogSearchService catalogSearchService;
    private final ExtentSummaryService extentSummaryService;
    private IdMappingService idMappingService;

    @Autowired
    public StaticCollectionServiceImpl(
            CatalogSearchService catalogSearchService,
            ExtentSummaryService extentSummaryService,
            IdMappingService idMappingService
    ) {
        this.catalogSearchService = catalogSearchService;
        this.extentSummaryService = extentSummaryService;
        this.idMappingService = idMappingService;
    }

    @Override
    public Try<Collection> convertRequest(String urn, OGCFeatLinkCreator linkCreator, ConfigurationAccessor config) {
        return extractURN(urn)
                .flatMap(resourceName -> convertRequest(resourceName, linkCreator, config))
                .onFailure(t -> error(LOGGER, t.getMessage(), t));
    }

    @Override
    public List<Tuple2<String, String>> staticRootCollectionsIdsAndLabels() {
        return trying(() -> getRootCollectionsDatasets()
            .map(e -> Tuple.of(e.getIpId().toString(), e.getLabel()))
        )
        .onFailure(t -> warn(LOGGER, "Failed to load the root static collections", t))
        .getOrElse(List::empty);
    }

    @Override
    public List<Collection> staticRootCollections(OGCFeatLinkCreator linkCreator, ConfigurationAccessor config) {
        return trying(() -> getRootCollectionsDatasets()
            .flatMap(entity -> convertRequest(entity.getIpId(), linkCreator, config))
        )
        .onFailure(t -> warn(LOGGER, "Failed to load the root static collections", t))
        .getOrElse(List::empty);
    }

    public List<AbstractEntity<?>> getRootCollectionsDatasets() throws SearchException, OpenSearchUnknownParameter {
        ICriterion criterion = ICriterion.not(ICriterion.startsWith("tags", "URN", StringMatchType.KEYWORD));
        List<AbstractEntity<?>> collections = searchCriterion(criterion, EntityType.COLLECTION);
        List<AbstractEntity<?>> datasets = searchCriterion(criterion, EntityType.DATASET);
        return collections.appendAll(datasets);
    }

    private Try<UniformResourceName> extractURN(String urnStr) {
        return trying(() -> UniformResourceName.fromString(urnStr))
            .mapFailure(URN_PARSING, () -> format("Failed to parse URN from %s", urnStr))
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
        return trying(() -> {

            String urn = resourceName.toString();

            StacProperty datetimeStacProp = config.getDatetimeStacProperty();
            List<StacProperty> stacProps = config.getStacProperties();
            List<StacProperty> nonDatetimeStacProps = Try.of(() -> stacProps.remove(datetimeStacProp)).getOrElse(stacProps);

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

            AbstractEntity<?> regardsCollection = collectionWithStats.getCollection();

            return new Collection(
                    StacSpecConstants.Version.STAC_SPEC_VERSION, HashSet.empty(),
                    regardsCollection.getLabel(),
                    idMappingService.getStacIdByUrn(regardsCollection.getIpId().toString()),
                    "",
                    links,
                    config.getKeywords(urn),
                    config.getLicense(urn),
                    providers,
                    extent,
                    summary,
                    null,
                    null
            );
        })
        .mapFailure(
            COLLECTION_CONSTRUCTION,
            () -> format("Failed to build collection for URN %s", resourceName)
        );
    }

    private List<Link> getLinks(
            UniformResourceName resourceName,
            OGCFeatLinkCreator linkCreator,
            String urn
    ) throws SearchException, OpenSearchUnknownParameter {
        final List<Link> links;

        if (resourceName.getEntityType().equals(EntityType.COLLECTION)) {
            List<AbstractEntity<?>> children = getSubCollectionsOrDatasets(urn, EntityType.COLLECTION)
                    .appendAll(getSubCollectionsOrDatasets(urn, EntityType.DATASET));

            List<Link> parentCollectionId = getParentCollectionId(urn, linkCreator);

            links = List.of(
                linkCreator.createRootLink(),
                parentCollectionId,
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

    private Option<Link> getItemsLinks(UniformResourceName resourceName, OGCFeatLinkCreator linkCreator) {
        return linkCreator.createCollectionItemsLinkWithRel(idMappingService.getStacIdByUrn(resourceName.toString()), ITEMS);
    }

    private List<AbstractEntity<?>> getSubCollectionsOrDatasets(String urn, EntityType entityType)
            throws SearchException, OpenSearchUnknownParameter {
        ICriterion tags = ICriterion.contains("tags", urn, StringMatchType.KEYWORD);
        FacetPage<AbstractEntity<?>> subCollections = catalogSearchService.search(tags,
                Searches.onSingleEntity(entityType),
                null,
                PageRequest.of(0, 10000));
        return List.ofAll(subCollections.getContent());
    }

    private List<Link> getParentCollectionId(String urn, OGCFeatLinkCreator linkCreator)
            throws SearchException, OpenSearchUnknownParameter {
        List<String> parentCollectionsId = getParentCollectionsId(urn);

        return parentCollectionsId.map(x ->
                linkCreator.createCollectionLinkWithRel(x, "", "parent"))
                .flatMap(t -> t);
    }

    private List<String> getParentCollectionsId(String urn)
            throws SearchException, OpenSearchUnknownParameter {
            ICriterion tags = ICriterion.contains("ipId", urn, StringMatchType.KEYWORD);
            return searchCriterion(tags, EntityType.COLLECTION)
                .map(AbstractEntity::getIpId)
                .map(Object::toString)
                    .map(idMappingService::getStacIdByUrn);
    }

    private List<AbstractEntity<?>> searchCriterion(ICriterion criterion, EntityType type)
            throws SearchException, OpenSearchUnknownParameter {
        FacetPage<AbstractEntity<?>> page = catalogSearchService.search(
            criterion,
            Searches.onSingleEntity(type),
            null,
            PageRequest.of(0, 100)
        );
        return List.of(page).map(x -> x.getContent()).flatMap(x -> x);
    }


}
