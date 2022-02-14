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

package fr.cnes.regards.modules.catalog.stac.service.criterion;

import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.framework.geojson.geometry.MultiPolygon;
import fr.cnes.regards.framework.geojson.geometry.Polygon;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.warn;

/**
 * Allows building criteria for geometries.
 *
 * <p>
 *   <strong>Notes:</strong>
 *   <ul>
 *     <li>Geometry criterion is only implemented for polygons and multipolygons.</li>
 *   </ul>
 * </p>
 */
@Component
public class GeometryCriterionBuilder implements CriterionBuilder<IGeometry> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeometryCriterionBuilder.class);

    @Override
    public Option<ICriterion> buildCriterion(List<StacProperty> properties, IGeometry geometry) {
        return Option.of(geometry)
            .flatMap(g -> {
                switch (geometry.getType()) {
                    case POLYGON:
                        return Option.of(ICriterion.intersectsPolygon(((Polygon)geometry).toArray()));
                    case MULTIPOLYGON:
                        return Option.of(ICriterion.or(Stream.of(((MultiPolygon)geometry).toArray()).map(ICriterion::intersectsPolygon)));
                    default:
                        warn(LOGGER, "Unsupported geometry type for STAC search criterion: {}", geometry.getType());
                        return Option.none();
                }
            });
    }
}
