/*
 * Copyright 2017-2021 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
 *
 * This file is part of REGARDS.
 *
 * REGARDS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * REGARDS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with REGARDS. If not, see <http://www.gnu.org/licenses/>.
 */

package fr.cnes.regards.modules.catalog.stac.service.collection;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.indexer.domain.aggregation.QueryableAttribute;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.metrics.geobounds.ParsedGeoBounds;
import org.elasticsearch.search.aggregations.metrics.stats.ParsedStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants.PropertyName.DATETIME_PROPERTY_NAME;
import static fr.cnes.regards.modules.catalog.stac.domain.properties.RegardsPropertyAccessor.accessor;
import static fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType.STRING;

/**
 * TODO: ExtentSummaryServiceImpl description
 *
 * @author gandrieu
 */
public class ExtentSummaryServiceImpl implements ExtentSummaryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtentSummaryServiceImpl.class);

    public static final String NWPOINT_AGGNAME = "nwPoint";
    public static final StacProperty NWPOINT_PROP = new StacProperty(accessor(NWPOINT_AGGNAME, STRING, ""), NWPOINT_AGGNAME, "", false, -1, "", STRING, null);
    public static final String SEPOINT_AGGNAME = "sePoint";
    public static final StacProperty SEPOINT_PROP = new StacProperty(accessor(SEPOINT_AGGNAME, STRING, ""), SEPOINT_AGGNAME, "", false, -1, "", STRING, null);

    @Override
    public List<QueryableAttribute> extentSummaryQueryableAttributes(
            StacProperty datetimeProp,
            List<StacProperty> otherProps
    ) {
        String datetimePropFullJsonPath = toAggregationName(datetimeProp);

        List<QueryableAttribute> summaryQueryableAttributes = summaryStacProps(otherProps)
                .map(sp -> new QueryableAttribute(
                        toAggregationName(sp),
                        null, false, 0, false, false)
                );

        List<QueryableAttribute> extentQueryableAttributes = List.of(
                new QueryableAttribute(datetimePropFullJsonPath, null, false, 0, false),
                new QueryableAttribute(NWPOINT_AGGNAME, null, false, 0, false, true),
                new QueryableAttribute(SEPOINT_AGGNAME, null, false, 0, false, true)
        );

        return extentQueryableAttributes.appendAll(summaryQueryableAttributes);
    }

    @Override
    public Map<StacProperty, Aggregation> toAggregationMap(List<StacProperty> props, List<Aggregation> aggs) {
        return aggs.flatMap(agg -> {
            switch(agg.getName()) {
                case NWPOINT_AGGNAME: return Option.of(Tuple.of(NWPOINT_PROP, agg));
                case SEPOINT_AGGNAME: return Option.of(Tuple.of(SEPOINT_PROP, agg));
                default: return findPropertyForAggregagtionName(props, agg.getName()).map(p -> Tuple.of(p, agg));
            }
        })
        .toMap(kv -> kv);
    }

    @Override
    public Extent extractExtent(Map<StacProperty, Aggregation> aggregationMap) {
        StacProperty datetimeProp = aggregationMap.keySet()
                .filter(p -> p.getStacPropertyName().equals(DATETIME_PROPERTY_NAME))
                .head();

        ParsedStats parsedDateRange = (ParsedStats) aggregationMap.get(datetimeProp);
        ParsedGeoBounds parsedNWBound = (ParsedGeoBounds) aggregationMap.get(NWPOINT_PROP);
        ParsedGeoBounds parsedSEBound = (ParsedGeoBounds) aggregationMap.get(SEPOINT_PROP);

        Long dateTimeFrom = ((Double)parsedDateRange.getMin()).longValue();
        Option<OffsetDateTime> from = Option.of(OffsetDateTime.ofInstant(java.time.Instant
                .ofEpochMilli(dateTimeFrom), ZoneId.systemDefault()));
        Long dateTimeTo = ((Double) parsedDateRange.getMax()).longValue();
        Option<OffsetDateTime> to = Option.of(OffsetDateTime.ofInstant(java.time.Instant
                .ofEpochMilli(dateTimeTo), ZoneId.systemDefault()));
        return new Extent(new Extent.Spatial(List.of(new BBox(parsedNWBound.topLeft().getLon(),
                parsedSEBound.bottomRight().getLat(),
                parsedSEBound.bottomRight().getLon(),
                parsedNWBound.topLeft().getLat()))),
                new Extent.Temporal(List.of(new Tuple2<>(from, to))));
    }


    @Override
    public Map<String, Object> extractSummary(Map<StacProperty, Aggregation> aggregationMap) {
        return aggregationMap
            .filterKeys(this::isNotExtentAggregation)
            .flatMap((prop, agg) ->
                Try.of(() ->
                    Tuple.of(prop.getStacPropertyName(), toMinMaxObject((ParsedStats) agg))
                )
                .onFailure(t -> LOGGER.error(t.getMessage(), t)));
    }

    public Object toMinMaxObject(ParsedStats parsedDateRange) {
        return HashMap.of(
                "min", parsedDateRange.getMin(),
                "max", parsedDateRange.getMax()
        );
    }

    private boolean isNotExtentAggregation(StacProperty prop) {
        return !isExtentAggregation(prop);
    }

    private boolean isExtentAggregation(StacProperty prop) {
        return prop == NWPOINT_PROP
            || prop == SEPOINT_PROP
            || prop.getStacPropertyName().equals(DATETIME_PROPERTY_NAME);
    }

    private List<StacProperty> summaryStacProps(List<StacProperty> otherProps) {
        return otherProps.filter(StacProperty::getComputeSummary)
                .filter(sp -> Number.class.isAssignableFrom(sp.getStacType().getValueType()));
    }

    private Option<StacProperty> findPropertyForAggregagtionName(List<StacProperty> props, String key) {
        return props.find(p -> key.equals(toAggregationName(p)));
    }

    private String toAggregationName(StacProperty sp) {
        return sp.getRegardsPropertyAccessor().getAttributeModel().getFullJsonPath();
    }
}