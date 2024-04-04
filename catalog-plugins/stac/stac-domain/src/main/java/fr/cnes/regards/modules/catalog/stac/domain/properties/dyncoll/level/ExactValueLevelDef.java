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

package fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelVal;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.ExactValueSublevelDef;
import io.vavr.collection.List;
import lombok.Value;

/**
 * Exact-value specific level definition.
 */
@Value
public class ExactValueLevelDef implements DynCollLevelDef<ExactValueSublevelDef> {

    private static final ExactValueSublevelDef SUBLEVEL_DEF = new ExactValueSublevelDef();

    StacProperty stacProperty;

    @Override
    public List<ExactValueSublevelDef> getSublevels() {
        return List.of(SUBLEVEL_DEF);
    }

    @Override
    public DynCollLevelVal parseValues(String repr) {
        return new DynCollLevelVal(this, List.of(new DynCollSublevelVal(SUBLEVEL_DEF, repr, toLabel(repr))));
    }

    @Override
    public String renderValue(DynCollLevelVal value) {
        return value.getSublevels().headOption().map(DynCollSublevelVal::getSublevelValue).getOrElse(toLabel("?"));
    }

    @Override
    public boolean isFullyValued(DynCollLevelVal val) {
        return true;
    }
}
