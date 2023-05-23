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
package fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol;

import io.vavr.collection.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Defines the filters for searching the timeline of collections.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class TimelineFiltersByCollection extends FiltersByCollection {

    private String from;

    private String to;

    private TimelineMode mode;

    @Builder(builderMethodName = "timelineCollectionFiltersBuilder")
    public TimelineFiltersByCollection(List<CollectionFilters> collections, String from, String to, TimelineMode mode) {
        super(collections, true);
        this.from = from;
        this.to = to;
        this.mode = mode;
    }

    public String getFrom() {
        return from == null ?
            OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE) :
            from;
    }

    public String getTo() {
        return to == null ? OffsetDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE) : to;
    }

    public TimelineMode getMode() {
        return mode == null ? TimelineMode.BINARY : mode;
    }

    public enum TimelineMode {
        // Compute a sorted array of 0/1 to specify if at least one item exists for each day of the timeline
        BINARY, // Compute a sorted map of 0/1 to specify if at least one item exists for each day of the timeline
        BINARY_MAP, // Compute a sorted array of 0/N to specify the number of items per day
        HISTOGRAM, // Compute a sorted map of 0/N to specify the number of items per day
        HISTOGRAM_MAP
    }
}
