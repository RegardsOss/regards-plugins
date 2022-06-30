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
package fr.cnes.regards.modules.catalog.stac.service.collection.timeline.builder;

import fr.cnes.regards.framework.gson.adapters.OffsetDateTimeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.service.item.properties.PropertyExtractionService;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.search.service.ICatalogSearchService;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;

/**
 * Build a timeline with a simple flag 0 or 1 for each timeline entry
 */
public class BinaryTimelineBuilder extends AbstractTimelineBuilder implements TimelineBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(BinaryTimelineBuilder.class);

    private final PropertyExtractionService propertyExtractionService;

    // Count number of timeline entry alteration
    private long reportingCount = 0;

    // Fallback activation threshold
    private long twoManyResultsThreshold = 30000;

    public BinaryTimelineBuilder(ICatalogSearchService catalogSearchService,
                                 PropertyExtractionService propertyExtractionService) {
        super(catalogSearchService);
        this.propertyExtractionService = propertyExtractionService;
    }

    @Override
    public Map<String, Long> buildTimeline(ICriterion itemCriteria,
                                           Pageable pageable,
                                           String collectionId,
                                           List<StacProperty> itemStacProperties,
                                           String from,
                                           String to) {

        // Locate the bounds to 00:00:00.
        OffsetDateTime timelineStart = OffsetDateTimeAdapter.parse(from).with(LocalTime.MIDNIGHT);
        OffsetDateTime timelineEnd = OffsetDateTimeAdapter.parse(to).with(LocalTime.MIDNIGHT);
        long timelineNbDays = ChronoUnit.DAYS.between(timelineStart, timelineEnd);

        // Initialize result map with 0
        java.util.Map<String, Long> timeline = new TreeMap<>();
        java.util.stream.Stream.iterate(timelineStart, currentDate -> currentDate.plusDays(1))
                               .limit(timelineNbDays + 1)
                               .forEach(currentDate -> timeline.put(getMapKey(currentDate), 0L));

        // Define STAC properties to extract
        List<StacProperty> datetimeStacProperties = itemStacProperties.filter(p -> p.getStacPropertyName()
                                                                                    .equals(StacSpecConstants.PropertyName.START_DATETIME_PROPERTY_NAME)
                                                                                   || p.getStacPropertyName()
                                                                                       .equals(StacSpecConstants.PropertyName.END_DATETIME_PROPERTY_NAME));

        // Build timeline
        long requestDuration = buildTimelineByPage(itemCriteria,
                                                   pageable,
                                                   collectionId,
                                                   timeline,
                                                   timelineStart,
                                                   timelineEnd,
                                                   datetimeStacProperties);
        LOGGER.trace("---> All pages reported in {} ms", requestDuration);

        return timeline;
    }

    private long buildTimelineByPage(ICriterion itemCriteria,
                                     Pageable pageable,
                                     String collectionId,
                                     Map<String, Long> timeline,
                                     OffsetDateTime timelineStart,
                                     OffsetDateTime timelineEnd,
                                     List<StacProperty> datetimeStacProperties) {

        long requestStart = System.currentTimeMillis();
        // Search data
        FacetPage<AbstractEntity<?>> page = getTimelineFacetPaged(itemCriteria, pageable, collectionId);
        LOGGER.trace("---> Page retrieved in {} ms", System.currentTimeMillis() - requestStart);

        // Fallback if too many results reached
        if (page.getTotalElements() > twoManyResultsThreshold) {
            // Enable fallback to avoid HTTP timeout
            LOGGER.info("Too many results detected ({} > {})! Fallback enabled for current timeline computing!",
                        page.getTotalElements(),
                        twoManyResultsThreshold);
            // Get collection temporal extent
            Option<Tuple2<OffsetDateTime, OffsetDateTime>> temporalExtent = getCollectionTemporalExtent(itemCriteria,
                                                                                                        collectionId,
                                                                                                        datetimeStacProperties);
            if (temporalExtent.isDefined()) {
                // Report collection temporal extent into timeline
                reportTemporalExtent(timelineStart,
                                     timelineEnd,
                                     temporalExtent.get()._1,
                                     temporalExtent.get()._2,
                                     timeline);
            }
            return System.currentTimeMillis() - requestStart;
        }

        // Analyse data
        page.forEach(entity -> {
            if (continueReporting(timeline)) {
                // Extract start_datetime & end_datetime from current feature
                io.vavr.collection.Map<String, Object> datetimeExtent = propertyExtractionService.extractStacProperties(
                    entity,
                    datetimeStacProperties);
                // Check both properties are defined
                if (datetimeExtent.get(StacSpecConstants.PropertyName.START_DATETIME_PROPERTY_NAME).isDefined()
                    && datetimeExtent.get(StacSpecConstants.PropertyName.END_DATETIME_PROPERTY_NAME).isDefined()) {
                    // Get datetime properties
                    OffsetDateTime itemStart = (OffsetDateTime) datetimeExtent.get(StacSpecConstants.PropertyName.START_DATETIME_PROPERTY_NAME)
                                                                              .get();
                    OffsetDateTime itemEnd = (OffsetDateTime) datetimeExtent.get(StacSpecConstants.PropertyName.END_DATETIME_PROPERTY_NAME)
                                                                            .get();
                    // Report item temporal extent into timeline
                    reportTemporalExtent(timelineStart, timelineEnd, itemStart, itemEnd, timeline);
                }
            }
        });
        long requestDuration = System.currentTimeMillis() - requestStart;
        LOGGER.trace("---> Page reported in {} ms", requestDuration);

        if (page.hasNext() && continueReporting(timeline)) {
            Pageable next = page.getPageable().next();
            // Dereference page to be available for garbage collecting
            page = null;
            requestDuration += buildTimelineByPage(itemCriteria,
                                                   next,
                                                   collectionId,
                                                   timeline,
                                                   timelineStart,
                                                   timelineEnd,
                                                   datetimeStacProperties);
        }
        return requestDuration;
    }

    private void reportTemporalExtent(OffsetDateTime timelineStart,
                                      OffsetDateTime timelineEnd,
                                      OffsetDateTime extentStart,
                                      OffsetDateTime extentEnd,
                                      Map<String, Long> timeline) {
        // Get intersection between feature temporal extent and timeline temporal extent
        Option<Tuple2<OffsetDateTime, OffsetDateTime>> intersection = getIntersection(timelineStart,
                                                                                      timelineEnd,
                                                                                      extentStart,
                                                                                      extentEnd);
        // Update timeline entries for each date of the intersection if any
        reportIntersection(intersection, timeline);
    }

    private Option<Tuple2<OffsetDateTime, OffsetDateTime>> getCollectionTemporalExtent(ICriterion itemCriteria,
                                                                                       String collectionId,
                                                                                       List<StacProperty> datetimeStacProperties) {
        return getPropertyBound(itemCriteria, collectionId, datetimeStacProperties);
    }

    private Option<Tuple2<OffsetDateTime, OffsetDateTime>> getIntersection(OffsetDateTime timelineStart,
                                                                           OffsetDateTime timelineEnd,
                                                                           OffsetDateTime itemStart,
                                                                           OffsetDateTime itemEnd) {
        if ((itemEnd.isAfter(timelineStart) || itemEnd.equals(timelineStart)) && (itemStart.isBefore(timelineEnd)
                                                                                  || itemStart.equals(timelineEnd))) {
            // Return max of start & min of end
            return Option.of(Tuple.of(timelineStart.isAfter(itemStart) ? timelineStart : itemStart,
                                      timelineEnd.isBefore(itemEnd) ? timelineEnd : itemEnd));
        }
        LOGGER.trace("Intersection not defined for item temporal extent : {} -> {}", itemStart, itemEnd);
        return Option.none();
    }

    private void reportIntersection(Option<Tuple2<OffsetDateTime, OffsetDateTime>> intersection,
                                    java.util.Map<String, Long> timeline) {
        if (intersection.isDefined()) {
            // Locate the bounds to 00:00:00.
            OffsetDateTime start = intersection.get()._1.with(LocalTime.MIDNIGHT);
            OffsetDateTime end = intersection.get()._2.with(LocalTime.MIDNIGHT);
            // LOGGER.trace("Reporting intersection for item with temporal extent : {} -> {}", start, end);
            long timelineNbDays = ChronoUnit.DAYS.between(start, end);

            java.util.stream.Stream.iterate(start, currentDate -> currentDate.plusDays(1))
                                   .limit(timelineNbDays + 1)
                                   .forEach(currentDate -> doTimelineReport(timeline, getMapKey(currentDate)));
        }
    }

    /**
     * Only mark timeline entry with binary flag 0 or 1
     */
    protected void doTimelineReport(java.util.Map<String, Long> timeline, String key) {
        if (timeline.get(key) == 0L) {
            reportingCount++;
            timeline.put(key, 1L);
        }
    }

    protected boolean continueReporting(java.util.Map<String, Long> timeline) {
        return reportingCount < timeline.size();
    }

    /**
     * @return key according to the context. At the moment, the current day!
     */
    private String getMapKey(OffsetDateTime offsetDateTime) {
        return offsetDateTime.toLocalDate().toString();
    }

    /**
     * Allows to alter the default threshold
     */
    public BinaryTimelineBuilder withTwoManyResultsThreshold(long twoManyResultsThreshold) {
        this.twoManyResultsThreshold = twoManyResultsThreshold;
        return this;
    }
}
