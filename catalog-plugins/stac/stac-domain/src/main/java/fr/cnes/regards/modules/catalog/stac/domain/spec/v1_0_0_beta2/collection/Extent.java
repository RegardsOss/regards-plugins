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

package fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection;

import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import lombok.Value;
import lombok.With;

import java.time.OffsetDateTime;

import static fr.cnes.regards.modules.catalog.stac.domain.utils.OffsetDatetimeUtils.lowestBound;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.OffsetDatetimeUtils.uppestBound;

/**
 * The object describes the spatio-temporal extents of the Collection.
 * Both spatial and temporal extents are required to be specified.
 *
 * @see <a href="https://github.com/radiantearth/stac-spec/blob/v1.0.0-beta.2/collection-spec/collection-spec.md#extent-object">description</a>
 * @see <a href="https://github.com/radiantearth/stac-spec/blob/v1.0.0-beta.2/collection-spec/json-schema/collection.json#L87">json schema</a>
 */
@Value @With
public class Extent {

    Spatial spatial;
    Temporal temporal;

    @Value @With
    public static class Spatial {
        List<BBox> bbox;
    }
    @Value @With
    public static class Temporal {
        List<Tuple2<OffsetDateTime, OffsetDateTime>> interval;
    }

    public static Extent maximalExtent() {
        return new Extent(
                new Spatial(List.of(new BBox(-180, -90, 180, 90))),
                new Temporal(List.of(Tuple.of(lowestBound(), uppestBound())))
        );
    }
}
