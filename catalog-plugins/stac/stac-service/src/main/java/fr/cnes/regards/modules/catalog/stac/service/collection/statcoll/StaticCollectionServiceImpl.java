package fr.cnes.regards.modules.catalog.stac.service.collection.statcoll;

import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.catalog.stac.domain.StacConstants;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.collection.Provider;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Link;
import fr.cnes.regards.modules.catalog.stac.service.collection.ExtentSummaryService;
import fr.cnes.regards.modules.catalog.stac.service.collection.IdMappingService;
import fr.cnes.regards.modules.catalog.stac.service.collection.common.CatalogSearchProxyService;
import fr.cnes.regards.modules.catalog.stac.service.collection.common.CollectionLinksMapper;
import fr.cnes.regards.modules.catalog.stac.service.collection.common.CollectionMapper;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.collection.CollectionConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.indexer.domain.aggregation.QueryableAttribute;
import fr.cnes.regards.modules.search.domain.plugin.CollectionWithStats;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Stream;
import io.vavr.control.Try;
import org.elasticsearch.search.aggregations.Aggregation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.COLLECTION_CONSTRUCTION;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.URN_PARSING;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.error;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.warn;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;
import static java.lang.String.format;

@Component
public class StaticCollectionServiceImpl implements StaticCollectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaticCollectionServiceImpl.class);

    private static final String FAILED_TO_LOAD_STATIC_COLLECTIONS = "Failed to load the static collections";

    private final ExtentSummaryService extentSummaryService;

    private final IdMappingService idMappingService;

    private final CatalogSearchProxyService catalogSearchProxyService;

    private final CollectionMapper collectionMapper;

    private final CollectionLinksMapper collectionLinksMapper;

    @Autowired
    public StaticCollectionServiceImpl(ExtentSummaryService extentSummaryService,
                                       IdMappingService idMappingService,
                                       CatalogSearchProxyService catalogSearchProxyService,
                                       CollectionMapper collectionMapper,
                                       CollectionLinksMapper collectionLinksMapper) {
        this.extentSummaryService = extentSummaryService;
        this.idMappingService = idMappingService;
        this.catalogSearchProxyService = catalogSearchProxyService;
        this.collectionMapper = collectionMapper;
        this.collectionLinksMapper = collectionLinksMapper;
    }

    @Override
    public Try<Collection> convertRequest(String urn, OGCFeatLinkCreator linkCreator, ConfigurationAccessor config) {
        return extractURN(urn).flatMap(resourceName -> convertRequest(resourceName, linkCreator, config))
                              .onFailure(t -> error(LOGGER, t.getMessage(), t));
    }

    public Try<Collection> convertRequest(String urn,
                                          OGCFeatLinkCreator linkCreator,
                                          CollectionConfigurationAccessor config) {
        return trying(() -> {
            AbstractEntity<?> entity = catalogSearchProxyService.getEntity(UniformResourceName.fromString(urn));
            return collectionMapper.buildFromEntity(entity,
                                                    config.getStacProperties(),
                                                    linkCreator,
                                                    null,
                                                    config,
                                                    false).get();
        }).onFailure(t -> error(LOGGER, t.getMessage(), t));
    }

    @Override
    public List<Tuple2<String, String>> staticRootCollectionsIdsAndLabels() {
        return trying(() -> {
            List<AbstractEntity<?>> collections = List.ofAll(catalogSearchProxyService.getEntitiesByType(EntityType.COLLECTION));
            List<AbstractEntity<?>> datasets = List.ofAll(catalogSearchProxyService.getEntitiesByType(EntityType.DATASET));
            List<AbstractEntity<?>> entities = collections.appendAll(datasets);
            return entities.map(e -> Tuple.of(e.getIpId().toString(), e.getLabel()));
        }).onFailure(t -> warn(LOGGER, FAILED_TO_LOAD_STATIC_COLLECTIONS, t)).getOrElse(List::empty);
    }

    @Override
    public List<Collection> staticRootCollections(OGCFeatLinkCreator linkCreator, ConfigurationAccessor config) {
        return trying(() -> {
            List<AbstractEntity<?>> collections = List.ofAll(catalogSearchProxyService.getEntitiesByType(EntityType.COLLECTION));
            List<AbstractEntity<?>> datasets = List.ofAll(catalogSearchProxyService.getEntitiesByType(EntityType.DATASET));
            List<AbstractEntity<?>> entities = collections.appendAll(datasets);
            return entities.flatMap(entity -> convertRequest(entity.getIpId(), linkCreator, config));
        }).onFailure(t -> warn(LOGGER, FAILED_TO_LOAD_STATIC_COLLECTIONS, t)).getOrElse(List::empty);
    }

    @Override
    public List<Collection> staticRootCollections(OGCFeatLinkCreator linkCreator,
                                                  CollectionConfigurationAccessor collectionConfigurationAccessor) {

        // Retrieve configured collection properties
        List<StacProperty> collectionStacProperties = collectionConfigurationAccessor.getStacProperties();

        return trying(() -> {
            List<AbstractEntity<?>> collections = List.ofAll(catalogSearchProxyService.getEntitiesByType(EntityType.COLLECTION));
            List<AbstractEntity<?>> datasets = List.ofAll(catalogSearchProxyService.getEntitiesByType(EntityType.DATASET));
            List<AbstractEntity<?>> entities = collections.appendAll(datasets);
            return collectionMapper.buildCollections(Stream.ofAll(entities),
                                                     collectionStacProperties,
                                                     linkCreator,
                                                     null,
                                                     collectionConfigurationAccessor,
                                                     false);
        }).onFailure(t -> warn(LOGGER, FAILED_TO_LOAD_STATIC_COLLECTIONS, t)).getOrElse(List::empty);
    }

    private Try<UniformResourceName> extractURN(String urnStr) {
        return trying(() -> UniformResourceName.fromString(urnStr)).mapFailure(URN_PARSING,
                                                                               () -> format(
                                                                                   "Failed to parse URN from %s",
                                                                                   urnStr)).flatMap(urn -> {
            if (urn.getEntityType().equals(EntityType.DATASET) || urn.getEntityType().equals(EntityType.COLLECTION)) {
                return Try.success(urn);
            } else {
                return Try.failure(new RuntimeException("The entity is neither a DATASET nor a COLLECTION"));
            }
        });
    }

    /**
     * Convert request to collection using StacSearchEngine plugin configuration
     *
     * @param urn         urn of the collection
     * @param linkCreator link creator
     * @param config      configuration accessor
     */
    private Try<Collection> convertRequest(UniformResourceName urn,
                                           OGCFeatLinkCreator linkCreator,
                                           ConfigurationAccessor config) {

        return trying(() -> {

            StacProperty datetimeStacProp = config.getDatetimeStacProperty();
            List<StacProperty> stacProps = config.getStacProperties();
            List<StacProperty> nonDatetimeStacProps = Try.of(() -> stacProps.remove(datetimeStacProp))
                                                         .getOrElse(stacProps);

            List<QueryableAttribute> creationDate = extentSummaryService.extentSummaryQueryableAttributes(
                datetimeStacProp,
                nonDatetimeStacProps);

            CollectionWithStats collectionWithStats = catalogSearchProxyService.getCollectionWithDataObjectsStats(urn,
                                                                                                                  creationDate.toJavaList());

            List<Link> links = collectionLinksMapper.getLinks(urn,
                                                              linkCreator,
                                                              collectionWithStats.getCollection().getProviderId(),
                                                              false);

            List<Provider> providers = config.getProviders(urn.toString())
                                             .map(x -> new Provider(x.getName(),
                                                                    x.getDescription(),
                                                                    x.getUrl(),
                                                                    x.getRoles()));

            List<Aggregation> aggs = List.ofAll(collectionWithStats.getAggregationList());
            Map<StacProperty, Aggregation> aggregationMap = extentSummaryService.toAggregationMap(stacProps, aggs);
            Extent extent = extentSummaryService.extractExtent(aggregationMap);
            Map<String, Object> summary = extentSummaryService.extractSummary(aggregationMap);

            AbstractEntity<?> regardsCollection = collectionWithStats.getCollection();

            return new Collection(StacConstants.STAC_SPEC_VERSION,
                                  HashSet.empty(),
                                  idMappingService.getStacIdByUrn(regardsCollection.getIpId().toString()),
                                  regardsCollection.getLabel(),
                                  "",
                                  config.getKeywords(urn.toString()),
                                  config.getLicense(urn.toString()),
                                  providers,
                                  extent,
                                  summary,
                                  links,
                                  null,
                                  null,
                                  null);
        }).mapFailure(COLLECTION_CONSTRUCTION, () -> format("Failed to build collection for URN %s", urn));
    }
}
