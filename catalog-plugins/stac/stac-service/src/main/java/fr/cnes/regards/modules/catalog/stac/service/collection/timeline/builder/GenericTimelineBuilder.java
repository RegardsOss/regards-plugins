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
package fr.cnes.regards.modules.catalog.stac.service.collection.timeline.builder;

import fr.cnes.regards.framework.gson.adapters.OffsetDateTimeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.TimelineByCollectionResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.TimelineFiltersByCollection;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacException;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.TreeMap;

public interface GenericTimelineBuilder {

    default java.util.Map<String, Long> initTimeline(String from, String to, ZoneId zoneId) {
        // Locate the bounds to 00:00:00.
        OffsetDateTime timelineStart = parseODT(from, zoneId);
        OffsetDateTime timelineEnd = parseODT(to, zoneId);
        long timelineNbDays = ChronoUnit.DAYS.between(timelineStart, timelineEnd);

        // Initialize result map with 0
        java.util.Map<String, Long> timeline = new TreeMap<>();
        java.util.stream.Stream.iterate(timelineStart, currentDate -> currentDate.plusDays(1))
                               .limit(timelineNbDays + 1)
                               .forEach(currentDate -> timeline.put(getMapKey(currentDate), 0L));
        return timeline;
    }

    default OffsetDateTime parseODT(String odt) {
        // Locate the bounds to 00:00:00.
        return OffsetDateTimeAdapter.parse(odt).with(LocalTime.MIDNIGHT);
    }

    default OffsetDateTime parseODT(String odt, ZoneId zoneId) {
        // Parse the date with the given zoneId.
        // And locate the bounds to 00:00:00.
        return OffsetDateTimeAdapter.parse(odt).atZoneSameInstant(zoneId).toOffsetDateTime().with(LocalTime.MIDNIGHT);
    }

    default TimelineByCollectionResponse.CollectionTimeline formatTimelineOutput(java.util.Map<String, Long> timeline,
                                                                                 String collectionId,
                                                                                 String correlationId,
                                                                                 Boolean isFailure,
                                                                                 String failureMessage,
                                                                                 TimelineFiltersByCollection.TimelineMode mode) {
        return switch (mode) {
            case BINARY, ES_BINARY, HISTOGRAM, ES_HISTOGRAM, ES_PARALLEL_BINARY, ES_PARALLEL_HISTOGRAM ->
                new TimelineByCollectionResponse.CollectionTimeline(collectionId,
                                                                    correlationId,
                                                                    isFailure,
                                                                    failureMessage,
                                                                    timeline.values());
            case BINARY_MAP, ES_BINARY_MAP, HISTOGRAM_MAP, ES_HISTOGRAM_MAP, ES_PARALLEL_BINARY_MAP, ES_PARALLEL_HISTOGRAM_MAP ->
                new TimelineByCollectionResponse.CollectionTimeline(collectionId,
                                                                    correlationId,
                                                                    isFailure,
                                                                    failureMessage,
                                                                    timeline);
            default -> throw new StacException(String.format("Unexpected timeline mode %s", mode),
                                               null,
                                               StacFailureType.TIMELINE_RETRIEVE_MODE);
        };
    }

    /**
     * @return key according to the context. At the moment, the current day!
     */
    default String getMapKey(OffsetDateTime offsetDateTime) {
        return offsetDateTime.toLocalDate().toString();
    }
}
