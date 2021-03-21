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
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DatePartSublevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelType.DatetimeBased;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelVal;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import lombok.Value;

import static fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelType.DatetimeBased.YEAR;


/**
 * Date parts specific level definition.
 */
@Value
public class DatePartsLevelDef implements DynCollLevelDef<DatePartSublevelDef> {

    StacProperty stacProperty;
    DatetimeBased deepestDatepart;

    @Override
    public List<DatePartSublevelDef> getSublevels() {
        return deepestDatepart.allPrevious().map(DatePartSublevelDef::new);
    }

    @Override
    public DynCollLevelVal parseValues(String repr) {
        List<List<Tuple2<DatetimeBased, String>>> levels = getSublevels().map(DatePartSublevelDef::getType)
                .zip(List.of(repr.split("[-T:]")))
                .scanLeft(List.<Tuple2<DatetimeBased, String>>empty(), List::append)
                .tail();

        List<DynCollSublevelVal> sublevels = levels
                .zip(getSublevels())
                .map(ls -> {
                    String sublevelValue = toValue(ls._1);
                    return new DynCollSublevelVal(ls._2, sublevelValue, toLabel(sublevelValue));
                });

        return new DynCollLevelVal(this, sublevels);
    }

    private String toValue(List<Tuple2<DatetimeBased, String>> valueParts) {
        return valueParts
                .map(kv -> kv.map1(this::partPrefix).apply(String::concat))
                .foldLeft("", String::concat)
                .substring(1);
    }

    public String partPrefix(DatetimeBased part) {
        switch (part) {
            case HOUR: return "T";
            case MINUTE: return ":";
            default: return "-";
        }
    }

    @Override
    public String renderValue(DynCollLevelVal value) {
        return toValue(value.getSublevels()
            .map(sval -> Tuple.of((DatetimeBased) sval.getSublevelDefinition().type(), sval.getSublevelValue())));
    }

    @Override
    public boolean isFullyValued(DynCollLevelVal val) {
        DynCollSublevelType deepestReachedinVal = val.getSublevels().lastOption().map(sval -> sval.getSublevelDefinition().type()).getOrElse(YEAR);
        return deepestDatepart == deepestReachedinVal;
    }


}
