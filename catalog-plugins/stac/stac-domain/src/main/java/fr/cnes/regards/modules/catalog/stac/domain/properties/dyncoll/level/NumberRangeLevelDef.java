/*
 * Copyright 2017-2022 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

package fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelVal;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.NumberRangeSublevelDef;
import io.vavr.collection.List;
import lombok.Value;

/**
 * Number range specific level definition.
 */
@Value
public class NumberRangeLevelDef implements DynCollLevelDef<NumberRangeSublevelDef> {

    StacProperty stacProperty;

    NumberRangeSublevelDef sublevel;

    @Override
    public List<NumberRangeSublevelDef> getSublevels() {
        return List.of(sublevel);
    }

    @Override
    public DynCollLevelVal parseValues(String repr) {
        return new DynCollLevelVal(this, List.of(new DynCollSublevelVal(sublevel, repr, toLabel(repr))));
    }

    @Override
    public String renderValue(DynCollLevelVal value) {
        return value.getSublevels().headOption().map(DynCollSublevelVal::getSublevelValue).getOrElse(toLabel("?"));
    }

    @Override
    public boolean isFullyValued(DynCollLevelVal val) {
        return true;
    }

    public String toRangeValue(Double from, Double to) {
        return from == null ? "<" + to : to == null ? ">" + from : from + ";" + to;
    }

    public String toRangeLabel(Double from, Double to) {
        String propName = stacProperty.getStacPropertyName();
        return from == null ?
            propName + " < " + to :
            to == null ? propName + " > " + from : from + " < " + propName + " < " + to;
    }
}
