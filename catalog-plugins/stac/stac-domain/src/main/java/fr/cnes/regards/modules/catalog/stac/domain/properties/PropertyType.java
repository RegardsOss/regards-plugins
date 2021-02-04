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

import com.google.gson.JsonElement;
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import io.vavr.control.Option;
import io.vavr.control.Try;

import java.time.OffsetDateTime;

/**
 * This enumeration lists all the supported STAC property types.
 *
 * @author gandrieu
 */
public enum PropertyType {

    DATETIME(String.class, OffsetDateTime.class),
    URL(String.class, java.net.URL.class),
    /** Default value */
    STRING(String.class, String.class),

    ANGLE(Double.class, Double.class),
    LENGTH(Double.class, Double.class),
    PERCENTAGE(Double.class, Double.class),
    NUMBER(Double.class, Double.class),

    GEOMETRY(String.class, IGeometry.class),
    BBOX(String.class, BBox.class),

    BOOLEAN(Boolean.class, Boolean.class),

    OBJECT(String.class, JsonElement.class),
    ;

    private final Class<?> stacJsonRepr;
    private final Class<?> regardsRepr;

    PropertyType(Class<?> jsonRepr, Class<?> regardsRepr) {
        this.stacJsonRepr = jsonRepr;
        this.regardsRepr = regardsRepr;
    }

    public static PropertyType parse(String stacType) {
        return Option.of(stacType)
            .map(s -> Try.of(() -> PropertyType.valueOf(s.trim().toUpperCase())).getOrElse(STRING))
            .getOrElse(STRING);
    }

    public Class<?> getStacJsonRepr() {
        return stacJsonRepr;
    }

    public Class<?> getRegardsRepr() {
        return regardsRepr;
    }
}
