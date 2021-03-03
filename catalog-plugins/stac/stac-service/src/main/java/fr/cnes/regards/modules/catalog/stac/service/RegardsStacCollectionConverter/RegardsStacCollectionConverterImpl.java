package fr.cnes.regards.modules.catalog.stac.service.RegardsStacCollectionConverter;

import com.google.common.collect.Maps;
import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.framework.module.rest.exception.EntityOperationForbiddenException;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Provider;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.feature.CollectionFeature;
import fr.cnes.regards.modules.dam.domain.entities.feature.EntityFeature;
import fr.cnes.regards.modules.indexer.domain.aggregation.QueryableAttribute;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.CatalogSearchService;
import fr.cnes.regards.modules.search.service.CollectionWithStats;
import fr.cnes.regards.modules.search.service.SearchException;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.search.aggregations.bucket.range.ParsedDateRange;
import org.elasticsearch.search.aggregations.metrics.stats.ParsedStats;
import org.joda.time.DateTime;
import org.joda.time.Instant;
import org.joda.time.tz.UTCProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;

@Component
public class RegardsStacCollectionConverterImpl implements IRegardsStacCollectionConverter {

    @Autowired
    private CatalogSearchService catalogSearchService;

    @Autowired
    private ConfigurationAccessorFactory configurationAccessorFactory;

    @Override
    public Try<Collection> convertRequest(String urn) {

        UniformResourceName resourceName = UniformResourceName.fromString(urn);

        CollectionWithStats damCollection = null;
        if (resourceName.getEntityType().equals(EntityType.DATASET) ||
                resourceName.getEntityType().equals(EntityType.COLLECTION)) {
            try {
                List<QueryableAttribute> creationDate =
                        List.of(new QueryableAttribute("creationDate", null, true, 0, false));
                damCollection = catalogSearchService.getCollectionWithDataObjectsStats(resourceName, SearchType.DATAOBJECTS, creationDate.toJavaList());
            } catch (EntityOperationForbiddenException | EntityNotFoundException | SearchException e) {
                return Try.failure(e);
            }
        } else {

            return Try.success(null);
        }

        ConfigurationAccessor configurationAccessor = configurationAccessorFactory.makeConfigurationAccessor();
        List<Provider> providers = configurationAccessor.getProviders(urn)
                .map(x -> new Provider(x.getName(), x.getDescription(), x.getUrl(), x.getRoles()));
        // At the moment, we'd choose to to setup the extent with maximal values
        ParsedDateRange parsedDateRange = (ParsedDateRange) damCollection.getAggregationList().get(0);
        Long dateTimeFrom = (Long) parsedDateRange.getBuckets().get(0).getFrom();
        Option<OffsetDateTime> from = Option.of(OffsetDateTime.ofInstant(java.time.Instant
                .ofEpochMilli(dateTimeFrom), ZoneId.systemDefault()));
        Long dateTimeTo = (Long) parsedDateRange.getBuckets().get(0).getTo();
        Option<OffsetDateTime> to = Option.of(OffsetDateTime.ofInstant(java.time.Instant
                .ofEpochMilli(dateTimeTo), ZoneId.systemDefault()));
        Extent extent = new Extent(new Extent.Spatial(List.of(new BBox(-180, -90, 180, 90))),
                new Extent.Temporal(List.of(new Tuple2<>(from, to))));
        Collection collection = new Collection("1.0.0-beta2",
                List.empty(),
                damCollection.getCollection().getLabel(),
                damCollection.getCollection().getId().toString(),
                damCollection.getCollection().getModel().getDescription(),
                List.empty(),
                configurationAccessor.getKeywords(urn),
                configurationAccessor.getLicense(urn),
                providers, extent, HashMap.of(Map.entry("", new Object())));

        return Try.success(collection);
    }

}
