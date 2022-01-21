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

package fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll;

import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.DynCollLevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.DynCollLevelVal;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelVal;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.Value;
import lombok.With;

/**
 * Value for dynamic collections.
 */
@Value @With
public class DynCollVal {

    DynCollDef definition;
    List<DynCollLevelVal> levels;

    public Option<DynCollLevelDef<?>> firstMissingValue() {
        return definition.getLevels().zipWithIndex()
            .find(lvlIdx -> lvlIdx._2 == levels.size())
            .map(Tuple2::_1);
    }

    public Option<DynCollLevelVal> firstPartiallyValued() {
        return levels.find(DynCollLevelVal::isPartiallyValued);
    }

    public Option<DynCollVal> parentValue() {
        return firstPartiallyValued()
            .map(pv ->
                pv.getSublevels().length() == 1
                ? withLevels(getLevels().dropRight(1))
                : withLevels(getLevels().dropRight(1).append(pv.withSublevels(pv.getSublevels().dropRight(1))))
            )
            .orElse(() ->
                getLevels().length() <= 1
                ? Option.none()
                : Option.of(withLevels(getLevels().dropRight(1)))
            );
    }

    public boolean isFullyValued() {
        return firstPartiallyValued().isEmpty() && firstMissingValue().isEmpty();
    }

    public String getLowestLevelLabel() {
        return levels
            .lastOption()
            .flatMap(lval -> lval.getSublevels().lastOption())
            .map(DynCollSublevelVal::getSublevelLabel)
            .getOrElse("?");
    }

    public String toLabel() {
        return getLevels()
            .flatMap(l -> l.getSublevels().lastOption())
            .map(DynCollSublevelVal::getSublevelLabel)
            .reduceLeftOption((a,b) -> a + " & " + b)
            .getOrElse("?");
    }
}
