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
import fr.cnes.regards.modules.indexer.service.Searches;
import fr.cnes.regards.modules.search.service.ICatalogSearchService;
import io.vavr.collection.List;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedDateHistogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Build a single timeline using Elasticsearch aggregation
 *
 * @author Marc SORDI
 *
 */
public abstract class AbstractElasticsearchTimelineBuilder implements TimelineBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractElasticsearchTimelineBuilder.class);

    protected final ICatalogSearchService catalogSearchService;

    private final String propertyPath;

    private final TimelineCriteriaHelper timelineCriteriaHelper;

    public AbstractElasticsearchTimelineBuilder(String propertyPath,
                                                ICatalogSearchService catalogSearchService,
                                                TimelineCriteriaHelper timelineCriteriaHelper) {
        this.catalogSearchService = catalogSearchService;
        this.propertyPath = propertyPath;
        this.timelineCriteriaHelper = timelineCriteriaHelper;
    }

    @Override
    public TimelineByCollectionResponse.CollectionTimeline buildTimeline(TimelineFiltersByCollection.TimelineMode mode,
                                                                         FiltersByCollection.CollectionFilters collectionFilters,
                                                                         Pageable pageable,
                                                                         List<StacProperty> itemStacProperties,
                                                                         String from,
                                                                         String to,
                                                                         ZoneId zoneId) {

        long requestStart = System.currentTimeMillis();

        // Locate the bounds to 00:00:00.
        OffsetDateTime timelineStart = parseODT(from, zoneId);
        OffsetDateTime timelineEnd = parseODT(to, zoneId);

        // Initialize result map with 0
        java.util.Map<String, Long> timeline = initTimeline(from, to, zoneId);
        LOGGER.trace("---> Timeline initialized in {} ms", System.currentTimeMillis() - requestStart);

        // Delegate aggregation
        ParsedDateHistogram parsedDateHistogram = catalogSearchService.getDateHistogram(Searches.onSingleEntity(
                                                                                            EntityType.DATA),
                                                                                        propertyPath,
                                                                                        timelineCriteriaHelper.getTimelineCriteria(
                                                                                            collectionFilters,
                                                                                            itemStacProperties),
                                                                                        DateHistogramInterval.DAY,
                                                                                        timelineStart,
                                                                                        timelineEnd,
                                                                                        zoneId);
        LOGGER.trace("---> Bucket size : {}", parsedDateHistogram.getBuckets().size());
        parsedDateHistogram.getBuckets().forEach(bucket -> {
            OffsetDateTime bucketDateTime = parseODT(bucket.getKeyAsString());
            // Only report if bucket intersects timeline
            if ((bucketDateTime.isAfter(timelineStart) || bucketDateTime.equals(timelineStart))
                && (bucketDateTime.isBefore(timelineEnd) || bucketDateTime.isEqual(timelineEnd))) {
                timeline.put(getMapKey(bucketDateTime), getBucketValue(bucket));
            }
        });
        LOGGER.trace("---> Timeline computed in {} ms", System.currentTimeMillis() - requestStart);
        return formatTimelineOutput(timeline,
                                    collectionFilters.getCorrelationId(),
                                    collectionFilters.getCorrelationId(),
                                    false,
                                    null,
                                    mode);
    }

    abstract long getBucketValue(Histogram.Bucket bucket);
}
