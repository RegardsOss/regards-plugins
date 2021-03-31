package fr.cnes.regards.modules.catalog.stac.service.collection.Static;

import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollVal;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Provider;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.service.collection.ExtentSummaryService;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.indexer.domain.SearchKey;
import fr.cnes.regards.modules.indexer.domain.aggregation.QueryableAttribute;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.service.Searches;
import fr.cnes.regards.modules.search.domain.plugin.CollectionWithStats;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.CatalogSearchService;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.elasticsearch.search.aggregations.Aggregation;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.*;

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

            List<Link> baseLinks = List.empty();

            if (resourceName.getEntityType().equals(EntityType.COLLECTION)) {
                List<java.util.List<AbstractEntity>> listOfEntities = getEntitiesList(urn);
                List<AbstractEntity> filterItem = getAbstractEntitiesItems(listOfEntities);
                List<Link> itemsLinks = getItemsLinks(filterItem, resourceName, linkCreator);


                List<AbstractEntity> filterChild = listOfEntities
                        .flatMap(x -> x)
                        .removeAll(filterItem);

                baseLinks = List.of(
                        linkCreator.createRootLink(),
                        itemsLinks,
                        filterChild.map(child -> linkCreator.createCollectionLinkWithRel(
                                child.getIpId().toString(),
                                "collection",
                                "child").get())
                        ).flatMap(t -> t);

            } else if (!resourceName.getEntityType().equals(EntityType.DATASET)) {
                List<java.util.List<AbstractEntity>> listOfEntities = getEntitiesList(urn);
                List<AbstractEntity> filterItem = getAbstractEntitiesItems(listOfEntities);
                List<Link> itemsLinks = getItemsLinks(filterItem, resourceName, linkCreator);
                baseLinks = List.of(
                        linkCreator.createRootLink(),
                        itemsLinks)
                .flatMap(t -> t);

            }

            List<Provider> providers = config.getProviders(urn)
                    .map(x -> new Provider(x.getName(), x.getDescription(), x.getUrl(), x.getRoles()));

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
                    baseLinks,
                    config.getKeywords(urn),
                    config.getLicense(urn),
                    providers, extent,
                    summary
            );

            return collection;
        });
    }

    @NotNull
    private List<Link> getItemsLinks(List<AbstractEntity> filterItem, UniformResourceName resourceName, OGCFeatLinkCreator linkCreator) {

        return filterItem
                .map(entity -> linkCreator.createItemLink(resourceName.toString(), entity.getIpId().toString()).get())
                .map(x -> x.withRel("item"));
    }

    @NotNull
    private List<java.util.List<AbstractEntity>> getEntitiesList(String urn) throws fr.cnes.regards.modules.search.service.SearchException, fr.cnes.regards.modules.opensearch.service.exception.OpenSearchUnknownParameter {
        ICriterion tags = ICriterion.contains("tags", urn);

        FacetPage<AbstractEntity> page = catalogSearchService.search(tags,
                Searches.onAllEntities(),
                null,
                PageRequest.of(0, 10000));

        return List.of(page.getContent());
    }

    private List<AbstractEntity> getAbstractEntitiesItems(List<java.util.List<AbstractEntity>> listOfEntities) {
        return listOfEntities
                .flatMap(x -> x)
                .filter(x -> x.getIpId().getEntityType().equals(EntityType.DATA));
    }

}
