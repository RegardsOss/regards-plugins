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

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.*;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DatePartSublevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelType;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.warn;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;

/**
 * Base implementation for {@link DynCollLevelValToQueryObjectConverter}.
 */
@Component
public class DynCollLevelValToQueryObjectConverterImpl implements DynCollLevelValToQueryObjectConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynCollLevelValToQueryObjectConverterImpl.class);

    @Override
    public Option<Tuple2<String, ItemSearchBody.QueryObject>> toQueryObject(DynCollLevelVal levelVal) {
        return trying(() -> {
            DynCollLevelDef<?> definition = levelVal.getDefinition();
            String stacPropertyName = definition.getStacProperty().getStacPropertyName();
            if (definition instanceof ExactValueLevelDef) {
                return Tuple.of(stacPropertyName, exactQueryObject(levelVal));
            }
            else if (definition instanceof NumberRangeLevelDef) {
                return Tuple.of(stacPropertyName, numberRangeQueryObject(levelVal, (NumberRangeLevelDef)definition));
            }
            else if (definition instanceof DatePartsLevelDef) {
                return Tuple.of(stacPropertyName, datePartsQueryObject(levelVal, (DatePartsLevelDef)definition));
            }
            else if (definition instanceof StringPrefixLevelDef){
                return Tuple.of(stacPropertyName, stringPrefixQueryObject(levelVal, (StringPrefixLevelDef)definition));
            }
            else {
                throw new NotImplementedException("Unknown level def type: " + definition.getClass().getName());
            }
        })
        .onFailure(t -> warn(LOGGER, "Failed to create query object from levelVal: {}", levelVal, t))
        .toOption();

    }

    protected ItemSearchBody.QueryObject stringPrefixQueryObject(
            DynCollLevelVal levelVal,
            StringPrefixLevelDef definition
    ) {
        String startsWith = definition.renderValue(levelVal);
        return ItemSearchBody.StringQueryObject.builder().startsWith(startsWith).build();
    }

    protected ItemSearchBody.QueryObject datePartsQueryObject(
            DynCollLevelVal levelVal,
            DatePartsLevelDef definition
    ) {
        DynCollSublevelType.DatetimeBased lastLevel = ((DatePartSublevelDef)levelVal.getSublevels().last().getSublevelDefinition()).getType();
        String rendered = levelVal.renderValue();

        OffsetDateTime gte = getDateLowerBound(lastLevel, rendered);
        OffsetDateTime lt = getDateHigherBound(lastLevel, gte);

        return ItemSearchBody.DatetimeQueryObject.builder()
            .gte(gte)
            .lt(lt)
            .build();
    }

    private OffsetDateTime getDateHigherBound(DynCollSublevelType.DatetimeBased lastLevel, OffsetDateTime gte) {
        switch (lastLevel) {
            case YEAR:
                return gte.plusYears(1L);
            case MONTH:
                return gte.plusMonths(1L);
            case DAY:
                return gte.plusDays(1L);
            case HOUR:
                return gte.plusHours(1L);
            case MINUTE:
                return gte.plusMinutes(1L);
            default: throw new NotImplementedException("Missing switch case for level " + lastLevel);
        }
    }

    private OffsetDateTime getDateLowerBound(DynCollSublevelType.DatetimeBased lastLevel, String rendered) {
        switch (lastLevel) {
            case YEAR:
                return OffsetDateTime.parse(rendered + "-01-01T00:00:00.000Z");
            case MONTH:
                return OffsetDateTime.parse(rendered + "-01T00:00:00.000Z");
            case DAY:
                return OffsetDateTime.parse(rendered + "T00:00:00.000Z");
            case HOUR:
                return OffsetDateTime.parse(rendered + ":00:00.000Z");
            case MINUTE:
                return OffsetDateTime.parse(rendered + ":00.000Z");
            default: throw new NotImplementedException("Missing switch case for level " + lastLevel);
        }
    }

    protected ItemSearchBody.QueryObject numberRangeQueryObject(
            DynCollLevelVal levelVal,
            NumberRangeLevelDef definition
    ) {
        String value = levelVal.getSublevels().get(0).getSublevelValue();
        if (value.startsWith("<")) {
            double lt = Double.parseDouble(value.replace("<", ""));
            return ItemSearchBody.NumberQueryObject.builder().lt(lt).build();
        }
        else if (value.startsWith(">")) {
            double gt = Double.parseDouble(value.replace(">", ""));
            return ItemSearchBody.NumberQueryObject.builder().gt(gt).build();
        }
        else if (value.contains(";")) {
            Double gte = Double.parseDouble(value.replaceFirst(";.*", ""));
            Double lte = Double.parseDouble(value.replaceFirst(".*;", ""));
            return ItemSearchBody.NumberQueryObject.builder().lte(lte).gte(gte).build();
        }
        else {
            throw new NotImplementedException("Unparsable number range level format");
        }
    }

    protected ItemSearchBody.QueryObject exactQueryObject(DynCollLevelVal levelVal) {
        StacPropertyType stacType = levelVal.getDefinition().getStacProperty().getStacType();
        switch (stacType) {
            case STRING:
                return ItemSearchBody.StringQueryObject.builder()
                    .eq(levelVal.getSublevels().head().getSublevelValue())
                    .build();
            case NUMBER: case PERCENTAGE: case ANGLE: case LENGTH:
                return ItemSearchBody.NumberQueryObject.builder()
                    .eq(Double.parseDouble(levelVal.getSublevels().head().getSublevelValue()))
                    .build();
            default:
                throw new NotImplementedException("Unsupported exact level definition for type " + stacType.name());
        }
    }

}
