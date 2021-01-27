/*
 * Copyright 2017-2020 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

package fr.cnes.regards.modules.catalog.stac.plugin.configuration;

import io.vavr.collection.HashMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.context.SpatialContextFactory;
import org.locationtech.spatial4j.io.GeoJSONReader;

import java.util.Map;

/**
 * Represents the configuration to be passed to Spatial4j context factory.
 *
 * @author gandrieu
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class Spatial4jConfiguration {

    private boolean geo;

    public Map<String, String> toConfigMap() {
        return HashMap.of("geo", "" + geo).toJavaMap();
    }

    public SpatialContextFactory contextFactory() {
        SpatialContextFactory factory = new SpatialContextFactory();
        factory.geo = this.geo;
        return factory;
    }
    public GeoJSONReader geoJsonReader() {
        SpatialContextFactory factory = contextFactory();
        return new GeoJSONReader(new SpatialContext(factory), factory);
    }

}
