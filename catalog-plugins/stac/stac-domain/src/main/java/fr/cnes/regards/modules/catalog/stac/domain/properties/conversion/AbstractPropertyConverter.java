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

package fr.cnes.regards.modules.catalog.stac.domain.properties.conversion;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import io.vavr.control.Try;

/**
 * Provides the generic mechanics to convert from a value of type X to a value of type Y,
 * and vice-versa.
 *
 * @param <ST> the STAC type
 * @param <RT> the REGARDS type
 */
public abstract class AbstractPropertyConverter<ST, RT> {

    @SuppressWarnings("unused")
    private final StacPropertyType type;

    public AbstractPropertyConverter(StacPropertyType type) {
        this.type = type;
    }

    public abstract Try<ST> convertRegardsToStac(RT value);

    public abstract Try<RT> convertStacToRegards(ST value);

    public static AbstractPropertyConverter<?, ?> idConverter(StacPropertyType type) {
        return new IdentityPropertyConverter<>(type);
    }

}
