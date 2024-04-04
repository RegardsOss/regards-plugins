/*
 * Copyright 2017-2024 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
import io.vavr.Function2;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.ParsedGeoBounds;
import org.elasticsearch.search.aggregations.metrics.ParsedStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.function.Supplier;

import static fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants.PropertyName.DATETIME_PROPERTY_NAME;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.error;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.warn;
import static fr.cnes.regards.modules.catalog.stac.domain.properties.RegardsPropertyAccessor.accessor;
import static fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType.NUMBER;
import static fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType.STRING;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.OffsetDatetimeUtils.extractTemporalBound;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;

/**
 * Base implementation for {@link ExtentSummaryService}.
 */
@Service
public class ExtentSummaryServiceImpl implements ExtentSummaryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtentSummaryServiceImpl.class);

    public static final String NWPOINT_AGGNAME = "nwPoint";

    public static final String NWPOINTLON_AGGNAME = NWPOINT_AGGNAME + ".lon";

    public static final String NWPOINTLAT_AGGNAME = NWPOINT_AGGNAME + ".lat";

    private static final StacProperty NWPOINT_PROP = new StacProperty(accessor(NWPOINT_AGGNAME, STRING, ""),
                                                                      null,
                                                                      NWPOINT_AGGNAME,
                                                                      "",
                                                                      false,
                                                                      -1,
                                                                      "",
                                                                      STRING,
                                                                      null,
                                                                      Boolean.FALSE);

    private static final StacProperty NWPOINTLON_PROP = new StacProperty(accessor(NWPOINTLON_AGGNAME, NUMBER, -180),
                                                                         null,
                                                                         NWPOINTLON_AGGNAME,
                                                                         "",
                                                                         false,
                                                                         -1,
                                                                         "",
                                                                         NUMBER,
                                                                         null,
                                                                         Boolean.FALSE);

    private static final StacProperty NWPOINTLAT_PROP = new StacProperty(accessor(NWPOINTLAT_AGGNAME, NUMBER, 90),
                                                                         null,
                                                                         NWPOINTLAT_AGGNAME,
                                                                         "",
                                                                         false,
                                                                         -1,
                                                                         "",
                                                                         NUMBER,
                                                                         null,
                                                                         Boolean.FALSE);

    public static final String SEPOINT_AGGNAME = "sePoint";

    public static final String SEPOINTLON_AGGNAME = SEPOINT_AGGNAME + ".lon";

    public static final String SEPOINTLAT_AGGNAME = SEPOINT_AGGNAME + ".lat";

    private static final StacProperty SEPOINT_PROP = new StacProperty(accessor(SEPOINT_AGGNAME, STRING, ""),
                                                                      null,
                                                                      SEPOINT_AGGNAME,
                                                                      "",
                                                                      false,
                                                                      -1,
                                                                      "",
                                                                      STRING,
                                                                      null,
                                                                      Boolean.FALSE);

    private static final StacProperty SEPOINTLON_PROP = new StacProperty(accessor(SEPOINTLON_AGGNAME, NUMBER, 180),
                                                                         null,
                                                                         SEPOINTLON_AGGNAME,
                                                                         "",
                                                                         false,
                                                                         -1,
                                                                         "",
                                                                         NUMBER,
                                                                         null,
                                                                         Boolean.FALSE);

    private static final StacProperty SEPOINTLAT_PROP = new StacProperty(accessor(SEPOINTLAT_AGGNAME, NUMBER, -90),
                                                                         null,
                                                                         SEPOINTLAT_AGGNAME,
                                                                         "",
                                                                         false,
                                                                         -1,
                                                                         "",
                                                                         NUMBER,
                                                                         null,
                                                                         Boolean.FALSE);

    @Override
    public List<QueryableAttribute> extentSummaryQueryableAttributes(StacProperty datetimeProp,
                                                                     List<StacProperty> otherProps) {
        String datetimePropFullJsonPath = toAggregationName(datetimeProp);

        List<QueryableAttribute> summaryQueryableAttributes = summaryStacProps(otherProps).map(sp -> new QueryableAttribute(
            toAggregationName(sp),
            null,
            false,
            0,
            false,
            false));

        List<QueryableAttribute> extentQueryableAttributes = List.of(new QueryableAttribute(datetimePropFullJsonPath,
                                                                                            null,
                                                                                            false,
                                                                                            0,
                                                                                            false),
                                                                     new QueryableAttribute(NWPOINT_AGGNAME,
                                                                                            null,
                                                                                            false,
                                                                                            0,
                                                                                            false,
                                                                                            true),
                                                                     new QueryableAttribute(NWPOINTLAT_AGGNAME,
                                                                                            null,
                                                                                            false,
                                                                                            0,
                                                                                            false,
                                                                                            false),
                                                                     new QueryableAttribute(NWPOINTLON_AGGNAME,
                                                                                            null,
                                                                                            false,
                                                                                            0,
                                                                                            false,
                                                                                            false),
                                                                     new QueryableAttribute(SEPOINT_AGGNAME,
                                                                                            null,
                                                                                            false,
                                                                                            0,
                                                                                            false,
                                                                                            true),
                                                                     new QueryableAttribute(SEPOINTLAT_AGGNAME,
                                                                                            null,
                                                                                            false,
                                                                                            0,
                                                                                            false,
                                                                                            false),
                                                                     new QueryableAttribute(SEPOINTLON_AGGNAME,
                                                                                            null,
                                                                                            false,
                                                                                            0,
                                                                                            false,
                                                                                            false));

        return extentQueryableAttributes.appendAll(summaryQueryableAttributes);
    }

    @Override
    public List<AggregationBuilder> extentSummaryAggregationBuilders(StacProperty datetimeProp,
                                                                     List<StacProperty> otherProps) {
        String datetimePath = toAggregationName(datetimeProp);
        return List.<AggregationBuilder>of(AggregationBuilders.stats(datetimePath).field(datetimePath),
                                           AggregationBuilders.geoBounds(NWPOINT_AGGNAME).field(NWPOINT_AGGNAME),
                                           AggregationBuilders.stats(NWPOINTLON_AGGNAME).field(NWPOINTLON_AGGNAME),
                                           AggregationBuilders.stats(NWPOINTLAT_AGGNAME).field(NWPOINTLAT_AGGNAME),
                                           AggregationBuilders.geoBounds(SEPOINT_AGGNAME).field(SEPOINT_AGGNAME),
                                           AggregationBuilders.stats(SEPOINTLON_AGGNAME).field(SEPOINTLON_AGGNAME),
                                           AggregationBuilders.stats(SEPOINTLAT_AGGNAME).field(SEPOINTLAT_AGGNAME))
                   .appendAll(summaryStacProps(otherProps).map(prop -> {
                       String name = toAggregationName(prop);
                       return AggregationBuilders.stats(name).field(name);
                   }));
    }

    @Override
    public Map<StacProperty, Aggregation> toAggregationMap(List<StacProperty> props, List<Aggregation> aggs) {
        return aggs.flatMap(agg -> {
            switch (agg.getName()) {
                case NWPOINT_AGGNAME:
                    return Option.of(Tuple.of(NWPOINT_PROP, agg));
                case NWPOINTLON_AGGNAME:
                    return Option.of(Tuple.of(NWPOINTLON_PROP, agg));
                case NWPOINTLAT_AGGNAME:
                    return Option.of(Tuple.of(NWPOINTLAT_PROP, agg));

                case SEPOINT_AGGNAME:
                    return Option.of(Tuple.of(SEPOINT_PROP, agg));
                case SEPOINTLON_AGGNAME:
                    return Option.of(Tuple.of(SEPOINTLON_PROP, agg));
                case SEPOINTLAT_AGGNAME:
                    return Option.of(Tuple.of(SEPOINTLAT_PROP, agg));

                default:
                    return findPropertyForAggregationName(props, agg.getName()).map(p -> Tuple.of(p, agg));
            }
        }).toMap(kv -> kv);
    }

    @Override
    public Extent extractExtent(Map<StacProperty, Aggregation> aggregationMap) {
        GeoPoint nwBound = extractBound(aggregationMap.get(NWPOINT_PROP),
                                        true).getOrElse(() -> extractBoundFromNumericAggs(aggregationMap.get(
            NWPOINTLON_PROP), aggregationMap.get(NWPOINTLAT_PROP), this::getNWPoint, () -> new GeoPoint(90d, -180d)));
        GeoPoint seBound = extractBound(aggregationMap.get(SEPOINT_PROP),
                                        false).getOrElse(() -> extractBoundFromNumericAggs(aggregationMap.get(
            SEPOINTLON_PROP), aggregationMap.get(SEPOINTLAT_PROP), this::getSEPoint, () -> new GeoPoint(-90d, 180d)));
        Extent.Spatial spatial = getSpatial(nwBound, seBound);

        Extent.Temporal temporal = extractTemporal(aggregationMap);

        return new Extent(spatial, temporal);
    }

    private GeoPoint extractBoundFromNumericAggs(Option<Aggregation> seLon,
                                                 Option<Aggregation> seLat,
                                                 Function2<ParsedStats, ParsedStats, GeoPoint> extractPointFn,
                                                 Supplier<GeoPoint> defaultValue) {
        return trying(() -> seLon.flatMap(lon -> seLat.map(lat -> extractPointFn.apply((ParsedStats) lon,
                                                                                       (ParsedStats) lat)))).onFailure(t -> warn(
            LOGGER,
            "Failed to parse NW bound from {} {}",
            seLon,
            seLat)).toOption().flatMap(t -> t).getOrElse(defaultValue);
    }

    private GeoPoint getNWPoint(ParsedStats lon, ParsedStats lat) {
        return new GeoPoint(finiteOr(lat.getMax(), 90d), finiteOr(lon.getMin(), -180d));
    }

    private GeoPoint getSEPoint(ParsedStats lon, ParsedStats lat) {
        return new GeoPoint(finiteOr(lat.getMin(), -90d), finiteOr(lon.getMax(), 180d));
    }

    private double finiteOr(double value, double finite) {
        return Double.isFinite(value) ? value : finite;
    }

    public Extent.Temporal extractTemporal(Map<StacProperty, Aggregation> aggregationMap) {
        Option<StacProperty> datetimeProp = aggregationMap.keySet()
                                                          .filter(p -> p.getStacPropertyName()
                                                                        .equals(DATETIME_PROPERTY_NAME))
                                                          .headOption();

        Option<ParsedStats> parsedStats = datetimeProp.flatMap(aggregationMap::get)
                                                      .flatMap(agg -> trying(() -> (ParsedStats) agg).onFailure(t -> error(
                                                          LOGGER,
                                                          t.getMessage(),
                                                          t)).toOption());

        OffsetDateTime dateTimeFrom = extractTemporalBound(parsedStats.map(ParsedStats::getMin)).getOrNull();
        OffsetDateTime dateTimeTo = extractTemporalBound(parsedStats.map(ParsedStats::getMax)).getOrNull();

        return new Extent.Temporal(List.of(new Tuple2<>(dateTimeFrom, dateTimeTo)));
    }

    private Option<GeoPoint> extractBound(Option<Aggregation> optAgg, boolean topLeft) {
        return optAgg.flatMap(agg -> trying(() -> (ParsedGeoBounds) agg).onFailure(t -> warn(LOGGER, t.getMessage(), t))
                                                                        .toOption())
                     .flatMap(pgb -> topLeft ? Option.of(pgb.topLeft()) : Option.of(pgb.bottomRight()));
    }

    public Extent.Spatial getSpatial(GeoPoint nwBound, GeoPoint seBound) {
        BBox bbox = new BBox(nwBound.getLon(), seBound.getLat(), seBound.getLon(), nwBound.getLat());
        return new Extent.Spatial(List.of(bbox));
    }

    @Override
    public Map<String, Object> extractSummary(Map<StacProperty, Aggregation> aggregationMap) {
        return aggregationMap.filterKeys(this::isNotExtentAggregation)
                             .flatMap((prop, agg) -> trying(() -> Tuple.of(prop.getStacPropertyName(),
                                                                           toMinMaxObject((ParsedStats) agg))).onFailure(
                                 t -> error(LOGGER, t.getMessage(), t)));
    }

    public Object toMinMaxObject(ParsedStats parsedDateRange) {
        Double min = parsedDateRange.getMin();
        min = Double.isInfinite(min) || Double.isNaN(min) ? null : min;
        Double max = parsedDateRange.getMax();
        max = Double.isInfinite(max) || Double.isNaN(max) ? null : max;
        return HashMap.of("min", min, "max", max);
    }

    private boolean isNotExtentAggregation(StacProperty prop) {
        return !isExtentAggregation(prop);
    }

    private boolean isExtentAggregation(StacProperty prop) {
        return (prop == NWPOINT_PROP)
               || (prop == NWPOINTLAT_PROP)
               || (prop == NWPOINTLON_PROP)
               || (prop
                   == SEPOINT_PROP)
               || (prop == SEPOINTLAT_PROP)
               || (prop == SEPOINTLON_PROP)
               || prop.getStacPropertyName().equals(DATETIME_PROPERTY_NAME);
    }

    private List<StacProperty> summaryStacProps(List<StacProperty> otherProps) {
        return otherProps.filter(o -> !o.getVirtual())
                         .filter(StacProperty::getComputeSummary)
                         .filter(sp -> Number.class.isAssignableFrom(sp.getStacType().getValueType()));
    }

    private Option<StacProperty> findPropertyForAggregationName(List<StacProperty> props, String key) {
        return props.find(p -> key.equals(toAggregationName(p)));
    }

    private String toAggregationName(StacProperty sp) {
        return trying(() -> sp.getRegardsPropertyAccessor().getAttributeModel().getFullJsonPath()).onFailure(t -> error(
            LOGGER,
            "Failed to get aggregation name for {}",
            sp,
            t)).getOrElse(sp.getStacPropertyName());
    }
}
