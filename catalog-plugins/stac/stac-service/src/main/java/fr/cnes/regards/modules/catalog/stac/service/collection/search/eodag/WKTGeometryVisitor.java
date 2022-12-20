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
package fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag;

import fr.cnes.regards.framework.geojson.coordinates.Position;
import fr.cnes.regards.framework.geojson.coordinates.Positions;
import fr.cnes.regards.framework.geojson.geometry.*;

import java.util.StringJoiner;

/**
 * Visitor to transform IGeometry in WKT
 */
public class WKTGeometryVisitor implements IGeometryVisitor<String> {

    private static final String POINT_LONG_LAT_FORMAT = "%,.2f %,.2f";

    private static final String COMMA = ",";

    @Override
    public String visitGeometryCollection(GeometryCollection geometryCollection) {
        // Not supported in search parameter
        return null;
    }

    @Override
    public String visitLineString(LineString lineString) {
        // Not supported in search parameter
        return null;
    }

    @Override
    public String visitMultiLineString(MultiLineString multiLineString) {
        // Not supported in search parameter
        return null;
    }

    @Override
    public String visitMultiPoint(MultiPoint multiPoint) {
        // Not supported in search parameter
        return null;
    }

    @Override
    public String visitMultiPolygon(MultiPolygon multiPolygon) {
        // Not supported in search parameter
        return null;
    }

    @Override
    public String visitPoint(Point point) {
        // Not supported in search parameter
        return null;
    }

    @Override
    public String visitPolygon(Polygon polygon) {
        // Polygon is the only GeoJSON geometry type supported in search parameter
        // Propagate value as WKT
        StringBuilder builder = new StringBuilder();
        builder.append("POLYGON (");
        // Build and append exterior ring
        builder.append(getPolygon(polygon.getCoordinates().getExteriorRing()));
        // Build and append holes
        if (polygon.containsHoles()) {
            for (Positions positions : polygon.getCoordinates().getHoles()) {
                builder.append(getPolygon(positions));
            }
        }
        builder.append(")");
        return builder.toString();
    }

    private String getPolygon(Positions positions) {
        StringBuilder builder = new StringBuilder();
        builder.append("(");
        StringJoiner joiner = new StringJoiner(COMMA);
        for (Position position : positions) {
            joiner.add(String.format(POINT_LONG_LAT_FORMAT, position.getLongitude(), position.getLatitude()));
        }
        builder.append(joiner);
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitUnlocated(Unlocated unlocated) {
        // Not supported in search parameter
        return null;
    }
}
