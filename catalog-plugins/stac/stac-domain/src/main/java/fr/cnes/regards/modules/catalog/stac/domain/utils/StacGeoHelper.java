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

package fr.cnes.regards.modules.catalog.stac.domain.utils;

import com.google.gson.Gson;
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.Centroid;
import io.vavr.Tuple;
import io.vavr.Tuple3;
import io.vavr.control.Option;
import org.locationtech.spatial4j.context.jts.JtsSpatialContext;
import org.locationtech.spatial4j.context.jts.JtsSpatialContextFactory;
import org.locationtech.spatial4j.io.GeoJSONReader;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.jts.JtsShapeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Function;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.debug;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.warn;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;

/**
 * Provides utilities to compute geometry-related values.
 */
@Component
public class StacGeoHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(StacGeoHelper.class);

    private final Gson gson;

    @Autowired
    public StacGeoHelper(Gson gson) {
        this.gson = gson;
    }

    public Function<JtsSpatialContextFactory, JtsSpatialContextFactory> updateFactory(boolean geo) {
        return factory -> {
            factory.geo = geo;
            factory.shapeFactoryClass = JtsShapeFactory.class;
            return factory;
        };
    }

    public GeoJSONReader makeGeoJSONReader(Function<JtsSpatialContextFactory, JtsSpatialContextFactory> updateFactory) {
        JtsSpatialContextFactory factory = updateFactory.apply(new JtsSpatialContextFactory());
        return new GeoJSONReader(new JtsSpatialContext(factory), factory);
    }

    public Option<Tuple3<IGeometry, BBox, Centroid>> computeBBoxCentroid(IGeometry geometry, GeoJSONReader reader) {
        return trying(() -> {
            String json = putTypeInFirstPosition(gson.toJson(geometry));
            debug(LOGGER, "\n\tGeometry: {}\n\tJSON: {}", geometry, json);

            Shape shape = reader.read(json);

            Rectangle boundingBox = shape.getBoundingBox();
            BBox bbox = new BBox(boundingBox.getMinX(),
                                 boundingBox.getMinY(),
                                 boundingBox.getMaxX(),
                                 boundingBox.getMaxY());

            Point center = shape.getCenter();
            Centroid centroid = new Centroid(center.getX(), center.getY());

            return Tuple.of(geometry, bbox, centroid);
        }).onFailure(t -> warn(LOGGER, "Could not create BBox for geometry {}", geometry, t)).toOption();
    }

    /**
     * Without this dirty hack, GeoJSONReader can not read geometries where the type appears after the coordinates.
     * https://github.com/locationtech/spatial4j/issues/156
     */
    private String putTypeInFirstPosition(String json) {
        String type = json.replaceFirst("(.*)(\"type\"\\s*:\\s*\"[^\"]*?\")(.*)", "$2");
        return "{" + type + "," + json.replaceFirst("\\{", "");
    }

}
