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

package fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import io.vavr.control.Try;
import lombok.Value;

/**
 * A dynamic collection level value consists of a StacProperty, along with a value.
 */
@Value
public class DynCollLevelValue {

    StacProperty property;
    Object value;

    public <T> Try<T> getGenericValue() {
        return Try.of(() -> (T) value);
    }

}
