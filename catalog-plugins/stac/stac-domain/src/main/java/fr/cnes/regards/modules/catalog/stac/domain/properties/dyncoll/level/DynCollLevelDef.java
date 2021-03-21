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
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollVal;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelDef;
import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * Dynamic collection levels correspond to STAC properties.
 * They also have a list of sublevels, defined from the configuration
 * of the format for the level.
 */
public interface DynCollLevelDef<T extends DynCollSublevelDef> {

    StacProperty getStacProperty();
    List<T> getSublevels();

    DynCollLevelVal parseValues(String repr);
    String renderValue(DynCollLevelVal value);
    default String toLabel(String value) {
        return String.format("%s=%s", getStacProperty().getStacPropertyName(), value);
    }

    boolean isFullyValued(DynCollLevelVal val);

    default Option<DynCollLevelVal> valueIn(DynCollVal val) {
        return val.getLevels().find(lval -> lval.getDefinition().equals(this));
    }

    default boolean isValuedIn(DynCollVal val) {
        return valueIn(val).isDefined();
    }

}
