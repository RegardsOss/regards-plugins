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

package fr.cnes.regards.modules.catalog.stac.plugin.domain;

import com.google.gson.Gson;
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.Spatial4jConfiguration;
import io.vavr.control.Try;
import org.locationtech.spatial4j.io.GeoJSONReader;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.Shape;

/**
 * Provides utilities to compute geometry-related values.
 */
public class StacGeoHelper {

    private final Gson gson;
    private final Spatial4jConfiguration spatial4jConfig;

    public StacGeoHelper(Gson gson, Spatial4jConfiguration spatial4jConfig) {
        this.gson = gson;
        this.spatial4jConfig = spatial4jConfig;
    }

    public Try<BBox> computeBBox(IGeometry p) {
        return Try.of(() -> {
            String json = gson.toJson(p);
            GeoJSONReader reader = spatial4jConfig.geoJsonReader();
            Shape shape = reader.read(json);
            Rectangle boundingBox = shape.getBoundingBox();
            return new BBox(
                boundingBox.getMinX(),
                boundingBox.getMaxX(),
                boundingBox.getMinY(),
                boundingBox.getMaxY()
            );
        });
    }

}
