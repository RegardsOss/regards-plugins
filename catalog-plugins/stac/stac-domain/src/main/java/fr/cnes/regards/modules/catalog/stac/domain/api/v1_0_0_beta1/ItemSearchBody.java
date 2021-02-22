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

package fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1;

import com.google.gson.annotations.SerializedName;
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import lombok.Value;
import lombok.With;

import java.time.OffsetDateTime;

/**
 * Describes the body of "POST /search" request.
 *
 * @see <a href="">Description</a>
 */
@Value
public class ItemSearchBody {

    BBox bbox;
    DateInterval datetime;
    IGeometry intersects;
    List<String> collections;
    List<String> ids;
    Integer limit;
    Fields fields;
    Map<String, QueryObject> query;
    List<SortBy> sortBy;

    @Value
    public static class SortBy {
        public enum Direction {
            @SerializedName("asc") ASC, @SerializedName("desc") DESC;
        }
        String field;
        Direction direction;
    }

    @Value @With
    public static class Fields {
        List<String> includes;
        List<String> excludes;
    }

    public interface QueryObject {}

    @Value
    public static class BooleanQueryObject implements QueryObject {
        Boolean eq;
        Boolean neq;
    }
    @Value
    public static class NumberQueryObject implements QueryObject {
        Double eq;
        Double neq;
        Double gt;
        Double lt;
        Double gte;
        Double lte;
        List<Double> in;
    }
    @Value
    public static class DatetimeQueryObject implements QueryObject {
        OffsetDateTime eq;
        OffsetDateTime neq;
        OffsetDateTime gt;
        OffsetDateTime lt;
        OffsetDateTime gte;
        OffsetDateTime lte;
        List<OffsetDateTime> in;
    }
    @Value
    public static class StringQueryObject implements QueryObject {
        String eq;
        String neq;
        String startsWith;
        String endsWith;
        String contains;
        List<String> in;
    }


}
