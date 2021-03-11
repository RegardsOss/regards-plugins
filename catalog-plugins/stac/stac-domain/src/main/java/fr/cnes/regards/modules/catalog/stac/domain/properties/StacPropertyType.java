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

package fr.cnes.regards.modules.catalog.stac.domain.properties;

import com.google.gson.JsonObject;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import io.vavr.control.Option;
import io.vavr.control.Try;

import java.time.OffsetDateTime;

/**
 * This enumeration lists all the supported STAC property types.
 *
 * For now, we do not attend to properties represented as arrays,
 * because there are no such properties defined in the standard.
 *
 * The standard recommends to avoid arrays because arrays are not easily queryable.
 * See <a href="https://github.com/radiantearth/stac-spec/tree/v1.0.0-beta.2/extensions#use-of-arrays-and-objects">
 *   this paragraph in the standard
 * </a>.
 */
public enum StacPropertyType {

    // REPRESENTED AS STRINGS IN JSON

    DATETIME(true, PropertyType.DATE_ISO8601, OffsetDateTime.class),
    URL(PropertyType.URL, java.net.URL.class),
    /** Default value */
    STRING(PropertyType.STRING, String.class),

    // REPRESENTED AS NUMBERS IN JSON

    ANGLE(true, PropertyType.DOUBLE, Double.class),
    LENGTH(true, PropertyType.DOUBLE, Double.class),
    PERCENTAGE(true, PropertyType.DOUBLE, Double.class),
    NUMBER(true, PropertyType.DOUBLE, Double.class),

    // REPRESENTED AS BOOLEANS IN JSON

    BOOLEAN(PropertyType.BOOLEAN, Boolean.class),

    // REPRESENTED AS OBJECTS IN JSON

    JSON_OBJECT(PropertyType.JSON, JsonObject.class),
    ;

    private final boolean canBeSummarized;
    private final PropertyType propertyType;
    private final Class<?> valueType;

    StacPropertyType(boolean canBeSummarized, PropertyType propertyType, Class<?> valueType) {
        this.canBeSummarized = canBeSummarized;
        this.propertyType = propertyType;
        this.valueType = valueType;
    }

    StacPropertyType(PropertyType propertyType, Class<?> valueType) {
        this(false, propertyType, valueType);
    }

    public boolean canBeSummarized() {
        return canBeSummarized;
    }

    public PropertyType getPropertyType() {
        return propertyType;
    }

    public Class<?> getValueType() {
        return valueType;
    }

    public static StacPropertyType parse(String stacType) {
        return Option.of(stacType)
            .map(s -> Try.of(() -> StacPropertyType.valueOf(s.trim().toUpperCase())).getOrElse(STRING))
            .getOrElse(STRING);
    }

}
