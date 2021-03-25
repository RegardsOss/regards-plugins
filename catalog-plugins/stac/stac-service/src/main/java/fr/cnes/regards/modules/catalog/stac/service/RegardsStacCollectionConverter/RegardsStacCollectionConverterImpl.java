package fr.cnes.regards.modules.catalog.stac.service.RegardsStacCollectionConverter;

import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.framework.module.rest.exception.EntityOperationForbiddenException;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Provider;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.indexer.domain.aggregation.QueryableAttribute;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.CatalogSearchService;
import fr.cnes.regards.modules.search.domain.plugin.CollectionWithStats;
import fr.cnes.regards.modules.search.service.SearchException;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.elasticsearch.search.aggregations.bucket.range.ParsedDateRange;
import org.elasticsearch.search.aggregations.metrics.geobounds.ParsedGeoBounds;
import org.elasticsearch.search.aggregations.metrics.stats.ParsedStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.*;

@Component
public class RegardsStacCollectionConverterImpl implements IRegardsStacCollectionConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegardsStacCollectionConverterImpl.class);


    @Autowired
    private CatalogSearchService catalogSearchService;

    @Autowired
    private ConfigurationAccessorFactory configurationAccessorFactory;

    @Override
    public Try<Collection> convertRequest(String urn) {

        UniformResourceName resourceName = UniformResourceName.fromString(urn);

        CollectionWithStats collectionWithStats = null;
        if (resourceName.getEntityType().equals(EntityType.DATASET) ||
                resourceName.getEntityType().equals(EntityType.COLLECTION)) {
            try {
                List<QueryableAttribute> creationDate =
                        List.of(new QueryableAttribute("creationDate", null, false, 500, false),
                                new QueryableAttribute("nwPoint", null, false, 0, false, true),
                                new QueryableAttribute("sePoint", null, false, 0, false, true));
                collectionWithStats = catalogSearchService.getCollectionWithDataObjectsStats(resourceName, SearchType.DATAOBJECTS, creationDate.toJavaList());
            } catch (EntityOperationForbiddenException | EntityNotFoundException | SearchException e) {
                LOGGER.error("Failed on retreiving entity");
                return Try.failure(e);
            }
        } else {
            LOGGER.error("The entity is neither a DATASET nor a COLLECTION");
            return Try.success(null);
        }

        ConfigurationAccessor configurationAccessor = configurationAccessorFactory.makeConfigurationAccessor();
        List<Provider> providers = configurationAccessor.getProviders(urn)
                .map(x -> new Provider(x.getName(), x.getDescription(), x.getUrl(), x.getRoles()));
        ParsedStats parsedDateRange = (ParsedStats) collectionWithStats.getAggregationList().get(0);
        ParsedGeoBounds parsedNWBound = (ParsedGeoBounds) collectionWithStats.getAggregationList().get(1);
        ParsedGeoBounds parsedSEBound = (ParsedGeoBounds) collectionWithStats.getAggregationList().get(2);
        Long dateTimeFrom = ((Double)parsedDateRange.getMin()).longValue();
        Option<OffsetDateTime> from = Option.of(OffsetDateTime.ofInstant(java.time.Instant
                .ofEpochMilli(dateTimeFrom), ZoneId.systemDefault()));
        Long dateTimeTo = ((Double) parsedDateRange.getMax()).longValue();
        Option<OffsetDateTime> to = Option.of(OffsetDateTime.ofInstant(java.time.Instant
                .ofEpochMilli(dateTimeTo), ZoneId.systemDefault()));
        Extent extent = new Extent(new Extent.Spatial(List.of(new BBox(parsedNWBound.topLeft().getLon(),
                parsedSEBound.bottomRight().getLat(),
                parsedSEBound.bottomRight().getLon(),
                parsedNWBound.topLeft().getLat()))),
                new Extent.Temporal(List.of(new Tuple2<>(from, to))));
        Collection collection = new Collection(StacSpecConstants.Version.STAC_SPEC_VERSION,
                List.empty(),
                collectionWithStats.getCollection().getLabel(),
                collectionWithStats.getCollection().getId().toString(),
                collectionWithStats.getCollection().getModel().getDescription(),
                List.empty(),
                configurationAccessor.getKeywords(urn),
                configurationAccessor.getLicense(urn),
                providers, extent, HashMap.of(Map.entry("", new Object())));

        return Try.success(collection);
    }

}
