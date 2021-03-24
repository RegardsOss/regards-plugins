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

package fr.cnes.regards.modules.catalog.stac.service.collection.dynamic.helpers;

import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollVal;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.*;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.*;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelType.DatetimeBased;
import fr.cnes.regards.modules.catalog.stac.service.StacSearchCriterionBuilder;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.indexer.dao.IEsRepository;
import fr.cnes.regards.modules.indexer.dao.spatial.ProjectGeoSettings;
import fr.cnes.regards.modules.indexer.domain.SimpleSearchKey;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.service.Searches;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Try;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelType.DatetimeBased.MONTH;

/**
 * Base implementation for {@link DynCollValNextSublevelHelper}.
 */
@Component
public class DynCollValNextSublevelHelperImpl implements DynCollValNextSublevelHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynCollValNextSublevelHelperImpl.class);
    private static final int TERMS_AGGREGATION_MAX_SIZE = 500;

    private final IEsRepository esRepository;
    private final DynCollLevelValToQueryObjectConverter levelValToQueryObjectConverter;
    private final StacSearchCriterionBuilder criterionBuilder;
    private final IRuntimeTenantResolver tenantResolver;
    private final ProjectGeoSettings projectGeoSettings;

    @Autowired
    public DynCollValNextSublevelHelperImpl(
            IEsRepository esRepository,
            DynCollLevelValToQueryObjectConverter levelValToQueryObjectConverter,
            StacSearchCriterionBuilder criterionBuilder,
            IRuntimeTenantResolver tenantResolver,
            ProjectGeoSettings projectGeoSettings
    ) {
        this.esRepository = esRepository;
        this.levelValToQueryObjectConverter = levelValToQueryObjectConverter;
        this.criterionBuilder = criterionBuilder;
        this.tenantResolver = tenantResolver;
        this.projectGeoSettings = projectGeoSettings;
    }

    @Override
    public List<DynCollVal> nextSublevels(DynCollVal val) {

        if (val.isFullyValued()) {
            LOGGER.debug("Val is fully valued");
            return List.empty();
        }

        return val.firstPartiallyValued()
                .map(pLVal -> extractExistingSublevels(val, pLVal))
                .orElse(() -> val.firstMissingValue()
                    .map(missingLDef -> extractNonExistingLevel(val, missingLDef)))
                .getOrElse(List::empty);
    }

    private List<DynCollVal> extractNonExistingLevel(DynCollVal val, DynCollLevelDef<?> definition) {
        if (definition instanceof ExactValueLevelDef) {
            return extractExactValueLevels(val, (ExactValueLevelDef)definition);
        }
        else if (definition instanceof NumberRangeLevelDef) {
            return extractNumberRangeLevels(val, (NumberRangeLevelDef) definition);
        }
        else if (definition instanceof StringPrefixLevelDef) {
            return extractStringPrefixFirstSublevel(val, (StringPrefixLevelDef)definition);
        }
        else if (definition instanceof DatePartsLevelDef) {
            return extractDatePartsFirstSublevel(val, (DatePartsLevelDef)definition);
        }
        else {
            LOGGER.error("Missing case for a dynamic collection level next levels extraction: {}", val);
            return List.empty();
        }
    }

    private List<DynCollVal> extractExactValueLevels(DynCollVal val, ExactValueLevelDef definition) {
        StacProperty prop = definition.getStacProperty();
        ICriterion criterion = computeCriterion(val, prop);
        String regardsAttributePath = getFullJsonPath(prop);

        LOGGER.error("extractExactValueLevels regardsAttributePath {}", regardsAttributePath);

        String termsAggName = "terms";
        AggregationBuilder termsAggBuilder = AggregationBuilders
                .terms(termsAggName)
                .field(regardsAttributePath)
                .size(TERMS_AGGREGATION_MAX_SIZE);

        Aggregations aggs = esRepository.getAggregations(searchKey(), criterion, termsAggBuilder);
        Terms termsAgg = aggs.get(termsAggName);
        return List.ofAll(termsAgg.getBuckets())
            .sortBy(MultiBucketsAggregation.Bucket::getKeyAsString)
            .map(bucket -> {
                String keyString = bucket.getKeyAsString();

                String exactValue = Try.of(() ->
                        prop.getConverter().convertRegardsToStac(bucket.getKey()).toString()
                ).getOrElse(keyString);

                String label = String.format("%s=%s (%d elements)", prop.getStacPropertyName(), exactValue, bucket.getDocCount());

                DynCollSublevelVal sublevelVal = new DynCollSublevelVal(new ExactValueSublevelDef(), exactValue, label);
                DynCollLevelVal levelVal = new DynCollLevelVal(definition, List.of(sublevelVal));
                List<DynCollLevelVal> newLevels = val.getLevels().append(levelVal);

                return val.withLevels(newLevels);
            });
    }

    private List<DynCollVal> extractNumberRangeLevels(DynCollVal val, NumberRangeLevelDef definition) {
        NumberRangeSublevelDef sublevel = definition.getSublevel();
        double step = sublevel.getStep();
        return List.rangeBy(sublevel.getMin(), sublevel.getMax() + (step / 100d), step)
                .prepend(null).append(null)
                .sliding(2).toList()
                .map(ls -> Tuple.of(ls.get(0), ls.get(1)))
                .map(fromTo -> new DynCollSublevelVal(
                        sublevel,
                        toNumberRangeValue(definition, fromTo),
                        toNumberRangeLabel(definition, fromTo)
                ))
                .map(sublevelVal -> new DynCollLevelVal(definition, List.of(sublevelVal)))
                .map(levelVal -> val.withLevels(val.getLevels().append(levelVal)));
    }

    private String toNumberRangeLabel(NumberRangeLevelDef definition, Tuple2<Double,Double> fromTo) {
        return definition.toRangeLabel(fromTo._1, fromTo._2);
    }

    private String toNumberRangeValue(NumberRangeLevelDef definition, Tuple2<Double,Double> fromTo) {
        return definition.toRangeValue(fromTo._1, fromTo._2);
    }

    private List<DynCollVal> extractDatePartsFirstSublevel(DynCollVal val, DatePartsLevelDef definition) {
        StacProperty prop = definition.getStacProperty();
        ICriterion criterion = computeCriterion(val, prop);
        String regardsAttributePath = getFullJsonPath(prop);

        LOGGER.error("extractDatePartsFirstSublevel regardsAttributePath {}", regardsAttributePath);

        SimpleSearchKey<AbstractEntity<?>> searchKey = searchKey();
        OffsetDateTime minDate = Try.of(() -> esRepository.minDate(searchKey, criterion, regardsAttributePath))
                .onFailure(t -> LOGGER.warn("Could not find lowest date for {}, using 1970", regardsAttributePath, t))
                .getOrElse(() -> OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC")));

        LOGGER.error("extractDatePartsFirstSublevel minDate {}", minDate);

        OffsetDateTime maxDate = Try.of(() -> esRepository.maxDate(searchKey, criterion, regardsAttributePath))
                .onFailure(t -> LOGGER.warn("Could not find highest date for {}, using now", regardsAttributePath, t))
                .getOrElse(() -> OffsetDateTime.now(ZoneId.of("UTC")));

        LOGGER.error("extractDatePartsFirstSublevel maxDate {}", maxDate);

        int minYear = minDate.getYear();
        int maxYear = maxDate.getYear();

        return List.range(minYear, maxYear+1)
            .map(y -> new DynCollSublevelVal(
                    definition.getSublevels().head(),
                    "" + y,
                    prop.getStacPropertyName() + "=" + y))
            .map(sublevelVal -> new DynCollLevelVal(definition, List.of(sublevelVal)))
            .map(levelVal -> val.withLevels(val.getLevels().append(levelVal)));
    }

    private List<DynCollVal> extractStringPrefixFirstSublevel(DynCollVal val, StringPrefixLevelDef definition) {
        StacProperty prop = definition.getStacProperty();
        String propName = prop.getStacPropertyName();
        StringPrefixSublevelDef sublevelDef = definition.getSublevels().get(0);
        return sublevelDef.allowedCharacters()
            .map(c -> new DynCollSublevelVal(sublevelDef, c, propName + "=" + c + "..."))
            .map(sublevelVal -> new DynCollLevelVal(definition, List.of(sublevelVal)))
            .map(levelVal -> val.withLevels(val.getLevels().append(levelVal)));
    }

    private List<DynCollVal> extractExistingSublevels(DynCollVal val, DynCollLevelVal lval) {
        DynCollLevelDef<?> definition = lval.getDefinition();
        if (definition instanceof StringPrefixLevelDef) {
            return extractStringPrefixNextSublevels(val, lval, (StringPrefixLevelDef)definition);
        }
        else if (definition instanceof DatePartsLevelDef) {
            return extractDatePartsNextSublevels(val, lval, (DatePartsLevelDef)definition);
        }
        else {
            LOGGER.error("Missing case for a dynamic collection level next levels extraction: {}", lval);
            return List.empty();
        }
    }

    private List<DynCollVal> extractStringPrefixNextSublevels(DynCollVal val, DynCollLevelVal lval, StringPrefixLevelDef definition) {
        StacProperty prop = definition.getStacProperty();
        String propName = prop.getStacPropertyName();
        String prevValue = lval.getSublevels().last().getSublevelValue();
        StringPrefixSublevelDef nextUndefined = definition.getSublevels().get(lval.getSublevels().length());
        return nextUndefined.allowedCharacters()
            .map(c -> {
                String newValue = prevValue + c;
                return new DynCollSublevelVal(nextUndefined, newValue, propName + "=" + prevValue + c + "...");
            })
            .map(sublevelVal -> val.withLevels(val.getLevels().init().append(lval.withSublevels(lval.getSublevels().append(sublevelVal)))));
    }

    private List<DynCollVal> extractDatePartsNextSublevels(DynCollVal val, DynCollLevelVal lval, DatePartsLevelDef definition) {
        String prevValue = lval.getSublevels().last().getSublevelValue();
        DatePartSublevelDef nextUndefined = definition.getSublevels().get(lval.getSublevels().length());
        DatetimeBased newPartType = nextUndefined.getType();
        List<Integer> newParts = getDatepartPossibleValues(lval, newPartType);
        return newParts
            .map(i -> {
                String newValue = String.format("%s%s%02d", prevValue, definition.partPrefix(newPartType), i);
                return new DynCollSublevelVal(nextUndefined, newValue, definition.toLabel(newValue));
            })
            .map(sublevelVal -> val.withLevels(val.getLevels().init().append(lval.withSublevels(lval.getSublevels().append(sublevelVal)))));
    }

    private List<Integer> getDatepartPossibleValues(DynCollLevelVal lval, DatetimeBased newPartType) {
        switch (newPartType) {
            case MONTH: return List.range(1, 13);
            case HOUR: return List.range(0, 24);
            case MINUTE: return List.range(0, 60);
            case DAY:
                DynCollSublevelVal monthSublevelVal = lval.getSublevels()
                    .find(v -> v.getSublevelDefinition().type() == MONTH)
                    .getOrElse(() -> lval.getSublevels().get(1));
                return List.range(1, numberOfDays(monthSublevelVal.getSublevelValue()));
            default: throw new RuntimeException("Unmanaged date sublevel: " + newPartType);
        }
    }

    private int numberOfDays(String sublevelValue) {
        String[] parts = sublevelValue.split("-");
        int year = Integer.parseInt(parts[0], 10);
        int month = Integer.parseInt(parts[1], 10);
        LocalDate ld = LocalDate.of(year, month, 1);
        return ld.lengthOfMonth();
    }

    private ICriterion computeCriterion(DynCollVal val, StacProperty prop) {
        Map<String, ItemSearchBody.QueryObject> queryObjects = val.getLevels()
                .map(levelValToQueryObjectConverter::toQueryObject)
                .flatMap(o -> o)
                .toMap(t -> t);
        ItemSearchBody isb = ItemSearchBody.builder().query(queryObjects).build();

        return criterionBuilder.toCriterion(List.of(prop), isb);
    }

    private String getFullJsonPath(StacProperty prop) {
        String suffix = "";
        if (prop.getRegardsPropertyAccessor().getAttributeModel().getType() == PropertyType.STRING) {
            suffix = ".keyword";
        }
        return prop.getRegardsPropertyAccessor().getAttributeModel().getFullJsonPath() + suffix;
    }

    private SimpleSearchKey<AbstractEntity<?>> searchKey() {
        SimpleSearchKey<AbstractEntity<?>> result = Searches.onSingleEntity(EntityType.DATA);
        result.setSearchIndex(tenantResolver.getTenant());
        result.setCrs(projectGeoSettings.getCrs());
        return result;
    }

}
