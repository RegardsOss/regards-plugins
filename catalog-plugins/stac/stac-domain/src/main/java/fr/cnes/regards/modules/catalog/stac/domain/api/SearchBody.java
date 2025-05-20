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
package fr.cnes.regards.modules.catalog.stac.domain.api;

import com.google.gson.annotations.SerializedName;
import io.vavr.collection.List;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

/**
 * Describe search body objects
 */
public class SearchBody {

    @Value
    public static class SortBy {

        public enum Direction {
            @SerializedName("asc") ASC, @SerializedName("desc") DESC
        }

        String field;

        Direction direction;
    }

    public interface QueryObject {

    }

    @Value
    @Builder
    public static class BooleanQueryObject implements QueryObject {

        Boolean eq;

        Boolean neq;
    }

    @Value
    @Builder
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
    @Builder
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
    @Builder
    public static class StringQueryObject implements QueryObject {

        String eq;

        String neq;

        String startsWith;

        String endsWith;

        String contains;

        List<String> containsAll;

        List<String> in;

        String matchType;
    }
}
