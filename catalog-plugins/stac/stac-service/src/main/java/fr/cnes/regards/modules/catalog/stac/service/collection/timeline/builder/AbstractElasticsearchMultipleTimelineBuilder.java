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

import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.catalog.stac.domain.api.extension.searchcol.FiltersByCollection;
import fr.cnes.regards.modules.catalog.stac.domain.api.extension.searchcol.TimelineByCollectionResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.extension.searchcol.TimelineFiltersByCollection;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.service.Searches;
import fr.cnes.regards.modules.search.domain.ParsedDateHistogramResponse;
import fr.cnes.regards.modules.search.service.ICatalogSearchService;
import io.vavr.collection.List;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedDateHistogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Build multiple timeline using Elasticsearch aggregation (one per collection)
 */
public abstract class AbstractElasticsearchMultipleTimelineBuilder implements MultipleTimelineBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractElasticsearchMultipleTimelineBuilder.class);

    protected final ICatalogSearchService catalogSearchService;

    private final String propertyPath;

    private final TimelineCriteriaHelper timelineCriteriaHelper;

    public AbstractElasticsearchMultipleTimelineBuilder(String propertyPath,
                                                        ICatalogSearchService catalogSearchService,
                                                        TimelineCriteriaHelper timelineCriteriaHelper) {
        this.catalogSearchService = catalogSearchService;
        this.propertyPath = propertyPath;
        this.timelineCriteriaHelper = timelineCriteriaHelper;
    }

    @Override
    public java.util.List<TimelineByCollectionResponse.CollectionTimeline> buildTimelines(TimelineFiltersByCollection.TimelineMode mode,
                                                                                          List<FiltersByCollection.CollectionFilters> collectionFilters,
                                                                                          List<StacProperty> itemStacProperties,
                                                                                          String from,
                                                                                          String to,
                                                                                          ZoneId zoneId) {
        long requestStart = System.currentTimeMillis();
        Map<String, Map<String, Long>> timelines = new HashMap<>(collectionFilters.size());
        java.util.List<TimelineByCollectionResponse.CollectionTimeline> results = new ArrayList<>();

        // Init correlationId -> collectionId map
        Map<String, String> collectionIdByCorrelationId = collectionFilters.toMap(FiltersByCollection.CollectionFilters::getCorrelationId,
                                                                                  FiltersByCollection.CollectionFilters::getCollectionId)
                                                                           .toJavaMap();

        // Locate the bounds to 00:00:00.
        OffsetDateTime timelineStart = parseODT(from, zoneId);
        OffsetDateTime timelineEnd = parseODT(to, zoneId);

        // Initialize all result maps with 0
        for (FiltersByCollection.CollectionFilters collectionFilter : collectionFilters) {
            timelines.put(collectionFilter.getCorrelationId(), initTimeline(from, to, zoneId));
        }
        LOGGER.trace("---> All {} timelines initialized in {} ms",
                     collectionFilters.size(),
                     System.currentTimeMillis() - requestStart);

        // Init criteria for each collection
        Map<String, ICriterion> criteria = new HashMap<>(collectionFilters.size());
        for (FiltersByCollection.CollectionFilters collectionFilter : collectionFilters) {
            try {
                criteria.put(collectionFilter.getCorrelationId(),
                             timelineCriteriaHelper.getTimelineCriteria(collectionFilter, itemStacProperties));
            } catch (Exception e) {
                LOGGER.error("Error while computing timeline for correlation id {} : {}",
                             collectionFilter.getCorrelationId(),
                             e.getMessage());
                results.add(new TimelineByCollectionResponse.CollectionTimeline(collectionIdByCorrelationId.get(
                    collectionFilter.getCorrelationId()), collectionFilter.getCorrelationId(), true, e.getMessage(), null));
            }
        }

        if (!criteria.isEmpty()) {
            // Delegate aggregation
            Map<String, ParsedDateHistogramResponse> response = catalogSearchService.getDateHistograms(Searches.onSingleEntity(
                                                                                                           EntityType.DATA),
                                                                                                       propertyPath,
                                                                                                       criteria,
                                                                                                       DateHistogramInterval.DAY,
                                                                                                       timelineStart,
                                                                                                       timelineEnd,
                                                                                                       zoneId);

            // Handle response
            response.forEach((correlationId, histogramResponse) -> {
                if (histogramResponse.isFailure()) {
                    results.add(new TimelineByCollectionResponse.CollectionTimeline(collectionIdByCorrelationId.get(
                        correlationId), correlationId, true, histogramResponse.failureMessage(), null));
                    LOGGER.error("Error while computing timeline for correlation id {} : {}",
                                 correlationId,
                                 histogramResponse.failureMessage());
                } else {
                    ParsedDateHistogram parsedDateHistogram = histogramResponse.histogram();
                    Map<String, Long> timeline = timelines.get(correlationId);
                    LOGGER.trace("---> Bucket size for correlation id {} : {}",
                                 correlationId,
                                 parsedDateHistogram.getBuckets().size());
                    parsedDateHistogram.getBuckets().forEach(bucket -> {
                        OffsetDateTime bucketDateTime = parseODT(bucket.getKeyAsString());
                        // Only report if bucket intersects timeline
                        if ((bucketDateTime.isAfter(timelineStart) || bucketDateTime.equals(timelineStart)) && (
                            bucketDateTime.isBefore(timelineEnd)
                            || bucketDateTime.isEqual(timelineEnd))) {
                            timeline.put(getMapKey(bucketDateTime), getBucketValue(bucket));
                        }
                    });
                    results.add(formatTimelineOutput(timeline,
                                                     collectionIdByCorrelationId.get(correlationId),
                                                     correlationId,
                                                     false,
                                                     null,
                                                     mode));
                    LOGGER.trace("---> Timeline computed in {} ms for correlation id {}",
                                 System.currentTimeMillis() - requestStart,
                                 correlationId);
                }
            });
        }

        LOGGER.trace("---> All {} timelines computed in {} ms",
                     collectionFilters.size(),
                     System.currentTimeMillis() - requestStart);
        return results;
    }

    abstract long getBucketValue(Histogram.Bucket bucket);
}
