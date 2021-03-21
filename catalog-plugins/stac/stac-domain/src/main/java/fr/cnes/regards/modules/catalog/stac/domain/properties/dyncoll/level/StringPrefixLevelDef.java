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

package fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelVal;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.StringPrefixSublevelDef;
import io.vavr.collection.List;
import lombok.Value;

/**
 * String prefix specific level definition.
 */
@Value
public class StringPrefixLevelDef implements DynCollLevelDef<StringPrefixSublevelDef> {

    StacProperty stacProperty;
    List<StringPrefixSublevelDef> sublevels;

    @Override
    public DynCollLevelVal parseValues(String repr) {
        List<DynCollSublevelVal> vals = sublevels
            .zip(List.range(1, repr.length() + 1).map(i -> repr.substring(0, i)))
            .map(kv -> {
                String sublevelValue = kv._2.substring(kv._2.length() - 1);
                return new DynCollSublevelVal(kv._1, sublevelValue, toLabel(kv._2) + "...");
            });
        return new DynCollLevelVal(this, vals);
    }

    @Override
    public String renderValue(DynCollLevelVal value) {
        return value.getSublevels()
            .map(DynCollSublevelVal::getSublevelValue)
            .foldLeft("", String::concat);
    }

    @Override
    public boolean isFullyValued(DynCollLevelVal val) {
        return val.getSublevels().size() == sublevels.size();
    }
}
