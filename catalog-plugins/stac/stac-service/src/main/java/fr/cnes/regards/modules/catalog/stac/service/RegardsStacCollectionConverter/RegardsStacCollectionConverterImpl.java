package fr.cnes.regards.modules.catalog.stac.service.RegardsStacCollectionConverter;

import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Provider;
import fr.cnes.regards.modules.catalog.stac.service.collection.ExtentSummaryService;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.indexer.domain.aggregation.QueryableAttribute;
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
import org.springframework.stereotype.Component;

@Component
public class RegardsStacCollectionConverterImpl implements IRegardsStacCollectionConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegardsStacCollectionConverterImpl.class);


    private final CatalogSearchService catalogSearchService;
    private final ConfigurationAccessorFactory configurationAccessorFactory;
    private final ExtentSummaryService extentSummaryService;

    @Autowired
    public RegardsStacCollectionConverterImpl(
            CatalogSearchService catalogSearchService,
            ConfigurationAccessorFactory configurationAccessorFactory,
            ExtentSummaryService extentSummaryService
    ) {
        this.catalogSearchService = catalogSearchService;
        this.configurationAccessorFactory = configurationAccessorFactory;
        this.extentSummaryService = extentSummaryService;
    }

    @Override
    public Try<Collection> convertRequest(String urn) {
        return extractURN(urn)
            .flatMap(resourceName -> convertRequest(urn, resourceName))
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

    private Try<Collection> convertRequest(String urn, UniformResourceName resourceName) {
        return Try.of(() -> {
            ConfigurationAccessor config = configurationAccessorFactory.makeConfigurationAccessor();
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

            Collection collection = new Collection(
                    StacSpecConstants.Version.STAC_SPEC_VERSION,
                    List.empty(),
                    collectionWithStats.getCollection().getLabel(),
                    collectionWithStats.getCollection().getId().toString(),
                    collectionWithStats.getCollection().getModel().getDescription(),
                    List.empty(),
                    config.getKeywords(urn),
                    config.getLicense(urn),
                    providers, extent,
                    summary
            );

            return collection;
        });
    }

}
