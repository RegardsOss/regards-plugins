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

package fr.cnes.regards.modules.catalog.stac.domain.properties.conversion;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import io.vavr.control.Try;

/**
 * Use this property converter when there is no need for conversion.
 */
public class IdentityPropertyConverter<X> extends AbstractPropertyConverter<X, X> {

    public IdentityPropertyConverter(StacPropertyType type) {
        super(type);
    }

    @Override
    public Try<X> convertRegardsToStac(X value) {
        return Try.success(value);
    }

    @Override
    public Try<X> convertStacToRegards(X value) {
        return Try.success(value);
    }
}
