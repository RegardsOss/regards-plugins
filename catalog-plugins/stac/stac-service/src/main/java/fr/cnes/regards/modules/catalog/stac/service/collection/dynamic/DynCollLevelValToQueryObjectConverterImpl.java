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

package fr.cnes.regards.modules.catalog.stac.service.collection.dynamic;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.*;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelVal;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Base implementation for {@link DynCollLevelValToQueryObjectConverter}.
 */
@Component
public class DynCollLevelValToQueryObjectConverterImpl implements DynCollLevelValToQueryObjectConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynCollLevelValToQueryObjectConverterImpl.class);

    @Override
    public Option<Tuple2<String, ItemSearchBody.QueryObject>> toQueryObject(DynCollLevelVal levelVal) {
        return Try.of(() -> {
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
        .onFailure(t -> LOGGER.warn("Failed to create query object from levelVal: {}", levelVal, t))
        .toOption();

    }

    private ItemSearchBody.QueryObject stringPrefixQueryObject(
            DynCollLevelVal levelVal,
            StringPrefixLevelDef definition
    ) {
        String startsWith = levelVal.getSublevels()
                .map(DynCollSublevelVal::getSublevelValue)
                .foldLeft("", String::concat);

        return new ItemSearchBody.StringQueryObject(
            null, null,
                startsWith,
            null, null, null
        );
    }

    private ItemSearchBody.QueryObject datePartsQueryObject(
            DynCollLevelVal levelVal,
            DatePartsLevelDef definition
    ) {
        return null;
    }

    private ItemSearchBody.QueryObject numberRangeQueryObject(
            DynCollLevelVal levelVal,
            NumberRangeLevelDef definition
    ) {
        String value = null;
        Double gte = 0d; // TODO
        Double lte = 0d;
        return new ItemSearchBody.NumberQueryObject(
            null, null, null, null,
                gte, lte, null
        );
    }

    private ItemSearchBody.QueryObject exactQueryObject(DynCollLevelVal levelVal) {
        StacPropertyType stacType = levelVal.getDefinition().getStacProperty().getStacType();
        switch (stacType) {
            case STRING:
                return new ItemSearchBody.StringQueryObject(
                    levelVal.getSublevels().head().getSublevelValue(),
                    null, null, null, null, null
                );
            case NUMBER: case PERCENTAGE: case ANGLE: case LENGTH:
                return new ItemSearchBody.NumberQueryObject(
                    Double.parseDouble(levelVal.getSublevels().head().getSublevelValue()),
                    null, null, null, null, null, null
                );
            default:
                throw new NotImplementedException("Unsupported exact level definition for type " + stacType.name());
        }
    }

}
