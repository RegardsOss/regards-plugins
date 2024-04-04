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

package fr.cnes.regards.modules.catalog.stac.service.criterion;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag.EODagParameters;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.springframework.stereotype.Component;

import java.util.StringJoiner;

/**
 * Builder for bbox criteria.
 */
@Component
public class BBoxCriterionBuilder implements CriterionBuilder<BBox> {

    /**
     * Point textual representation with longitude & latitude
     */
    private static final String POINT_LONG_LAT_FORMAT = "%,.5f %,.5f";

    private static final String COMMA = ",";

    @Override
    public Option<ICriterion> buildCriterion(List<StacProperty> properties, BBox bbox) {
        return Option.of(bbox)
                     .map(bb -> ICriterion.intersectsBbox(bb.getMinX(), bb.getMinY(), bb.getMaxX(), bb.getMaxY()));
    }

    @Override
    public void computeEODagParameters(EODagParameters parameters, List<StacProperty> properties, BBox bbox) {
        if (bbox != null) {
            // Propagate value as WKT
            StringJoiner joiner = new StringJoiner(COMMA, "POLYGON ((", "))");
            // 1 - min long / min lat
            String first = String.format(POINT_LONG_LAT_FORMAT, bbox.getMinX(), bbox.getMinY());
            joiner.add(first);
            // 2 - max long / min lat
            joiner.add(String.format(POINT_LONG_LAT_FORMAT, bbox.getMaxX(), bbox.getMinY()));
            // 3 - max long / max lat
            joiner.add(String.format(POINT_LONG_LAT_FORMAT, bbox.getMaxX(), bbox.getMaxY()));
            // 4 - min long / max lat
            joiner.add(String.format(POINT_LONG_LAT_FORMAT, bbox.getMinX(), bbox.getMaxY()));
            // Close polygon with first point
            joiner.add(first);

            parameters.setGeom(joiner.toString());
        }
    }
}
