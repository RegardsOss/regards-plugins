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

package fr.cnes.regards.modules.catalog.stac.domain.properties;

import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.AbstractPropertyConverter;
import lombok.Value;
import lombok.With;

/**
 * Represents a configured STAC property.
 */
@Value
@With
public class StacProperty {

    RegardsPropertyAccessor regardsPropertyAccessor;

    /**
     * Optional object wrapper
     */
    String stacPropertyNamespace;

    String stacPropertyName;

    String extension;

    Boolean computeSummary;

    Integer dynamicCollectionLevel;

    String dynamicCollectionFormat;

    StacPropertyType stacType;

    @SuppressWarnings("rawtypes")
    AbstractPropertyConverter converter;

    Boolean virtual;

    public boolean isDynamicCollectionLevel() {
        return dynamicCollectionLevel != null && dynamicCollectionLevel >= 0;
    }

    /**
     * Normalize the input value according to the expected STAC property type
     * In some cases, a STAC property of type PERCENTAGE may be created from an Integer model attribute
     * Since percentages are expected to be represented as Double values in STAC, this method
     * converts the value to Double if applicable.
     */
    public Object normalizeValue(Object value) {
        if (StacPropertyType.PERCENTAGE.equals(stacType) && value instanceof Integer intValue) {
            return intValue.doubleValue();
        }
        return value;
    }
}
