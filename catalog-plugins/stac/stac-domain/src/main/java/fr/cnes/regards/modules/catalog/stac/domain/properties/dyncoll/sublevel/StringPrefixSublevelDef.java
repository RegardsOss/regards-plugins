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

package fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel;

import io.vavr.collection.List;
import lombok.Value;
import lombok.With;

/**
 * Prefix sublevel, at the given position, applicable to alpha/numeric characters.
 */
@Value
@With
public class StringPrefixSublevelDef implements DynCollSublevelDef {

    int position;

    boolean alpha;

    boolean digits;

    @Override
    public DynCollSublevelType type() {
        return DynCollSublevelType.StringBased.PREFIX;
    }

    public List<String> allowedCharacters() {
        String allowedCharsStr = "" + (alpha ? "ABCDEFGHIJKLMNOPQRSTUVWXYZ" : "") + (digits ? "0123456789" : "");
        return List.ofAll(allowedCharsStr.toCharArray()).map(c -> "" + c);
    }

}
