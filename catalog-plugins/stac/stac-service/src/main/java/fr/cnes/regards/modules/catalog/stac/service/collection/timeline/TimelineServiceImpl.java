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
package fr.cnes.regards.modules.catalog.stac.service.collection.timeline;

import fr.cnes.regards.modules.catalog.stac.domain.StacProperties;
import fr.cnes.regards.modules.catalog.stac.domain.api.SearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.extension.searchcol.TimelineByCollectionResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.extension.searchcol.TimelineFiltersByCollection;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacException;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.service.collection.timeline.builder.*;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.item.properties.PropertyExtractionService;
import fr.cnes.regards.modules.catalog.stac.service.search.AbstractSearchService;
import fr.cnes.regards.modules.search.service.ICatalogSearchService;
import io.vavr.collection.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;

@Service
public class TimelineServiceImpl extends AbstractSearchService implements TimelineService {

    private static final String EXPANDED_DATE = "T00:00:00Z";

    @Value("${regards.timeline.two.many.results.threshold:30000}")
    private long twoManyResultsThreshold;

    private final ConfigurationAccessorFactory configurationAccessorFactory;

    private final ICatalogSearchService catalogSearchService;

    private final PropertyExtractionService propertyExtractionService;

    private final TimelineCriteriaHelper timelineCriteriaHelper;

    public TimelineServiceImpl(ConfigurationAccessorFactory configurationAccessorFactory,
                               ICatalogSearchService catalogSearchService,
                               PropertyExtractionService propertyExtractionService,
                               TimelineCriteriaHelper timelineCriteriaHelper) {
        this.configurationAccessorFactory = configurationAccessorFactory;
        this.catalogSearchService = catalogSearchService;
        this.propertyExtractionService = propertyExtractionService;
        this.timelineCriteriaHelper = timelineCriteriaHelper;
    }

    @Override
    public TimelineByCollectionResponse buildCollectionTimelines(TimelineFiltersByCollection timelineFiltersByCollection) {

        java.util.List<TimelineByCollectionResponse.CollectionTimeline> collectionTimelines;

        // Handle parallel computation of timelines
        if (timelineFiltersByCollection.getMode().isParallel) {
            collectionTimelines = buildParallelCollectionTimelines(timelineFiltersByCollection);
        } else {
            collectionTimelines = buildSimpleCollectionTimelines(timelineFiltersByCollection);
        }

        return new TimelineByCollectionResponse(collectionTimelines);
    }

    private java.util.List<TimelineByCollectionResponse.CollectionTimeline> buildSimpleCollectionTimelines(
        TimelineFiltersByCollection timelineFiltersByCollection) {

        java.util.List<TimelineByCollectionResponse.CollectionTimeline> collectionTimelines = new ArrayList<>();

        timelineFiltersByCollection.getCollections().forEach(collectionFilters -> {

            // Retrieve configured item properties
            ConfigurationAccessor configurationAccessor = configurationAccessorFactory.makeConfigurationAccessor();
            List<StacProperty> itemStacProperties = configurationAccessor.getStacProperties();

            // Set pagination context (considering first page, index 1 in STAC paradigm)
            List<SearchBody.SortBy> sortBy = List.of(new SearchBody.SortBy(StacProperties.START_DATETIME_PROPERTY_NAME,
                                                                           SearchBody.SortBy.Direction.ASC),
                                                     new SearchBody.SortBy(StacProperties.END_DATETIME_PROPERTY_NAME,
                                                                           SearchBody.SortBy.Direction.ASC));
            Pageable pageable = pageable(1000, 1, sortBy, itemStacProperties);

            // Init timeline builder
            TimelineBuilder timelineBuilder = switch (timelineFiltersByCollection.getMode()) {
                case BINARY, BINARY_MAP -> new LegacyBinaryTimelineBuilder(catalogSearchService,
                                                                           propertyExtractionService,
                                                                           timelineCriteriaHelper).withTwoManyResultsThreshold(
                    twoManyResultsThreshold);
                case HISTOGRAM, HISTOGRAM_MAP -> new LegacyHistogramTimelineBuilder(catalogSearchService,
                                                                                    propertyExtractionService,
                                                                                    timelineCriteriaHelper).withTwoManyResultsThreshold(
                    twoManyResultsThreshold);
                case ES_BINARY, ES_BINARY_MAP ->
                    new ElasticsearchBinaryTimelineBuilder(configurationAccessor.getHistogramProperyPath(),
                                                           catalogSearchService,
                                                           timelineCriteriaHelper);
                case ES_HISTOGRAM, ES_HISTOGRAM_MAP ->
                    new ElasticsearchHistogramTimelineBuilder(configurationAccessor.getHistogramProperyPath(),
                                                              catalogSearchService,
                                                              timelineCriteriaHelper);
                default -> throw new StacException(String.format("Unexpected timeline mode %s",
                                                                 timelineFiltersByCollection.getMode()),
                                                   null,
                                                   StacFailureType.TIMELINE_RETRIEVE_MODE);
            };

            TimelineByCollectionResponse.CollectionTimeline timeline = timelineBuilder.buildTimeline(
                timelineFiltersByCollection.getMode(),
                collectionFilters,
                pageable,
                itemStacProperties,
                expandDatetime(timelineFiltersByCollection.getFrom()),
                expandDatetime(timelineFiltersByCollection.getTo()),
                ZoneId.of(timelineFiltersByCollection.getTimezone()));
            collectionTimelines.add(timeline);
        });

        return collectionTimelines;
    }

    private java.util.List<TimelineByCollectionResponse.CollectionTimeline> buildParallelCollectionTimelines(
        TimelineFiltersByCollection timelineFiltersByCollection) {

        // Retrieve configured item properties
        ConfigurationAccessor configurationAccessor = configurationAccessorFactory.makeConfigurationAccessor();
        List<StacProperty> itemStacProperties = configurationAccessor.getStacProperties();

        // Init timeline builder
        MultipleTimelineBuilder timelineBuilder = switch (timelineFiltersByCollection.getMode()) {
            case ES_PARALLEL_BINARY, ES_PARALLEL_BINARY_MAP -> new ElasticsearchMultipleBinaryTimelineBuilder(
                configurationAccessor.getHistogramProperyPath(),
                catalogSearchService,
                timelineCriteriaHelper);
            case ES_PARALLEL_HISTOGRAM, ES_PARALLEL_HISTOGRAM_MAP -> new ElasticsearchMultipleHistogramTimelineBuilder(
                configurationAccessor.getHistogramProperyPath(),
                catalogSearchService,
                timelineCriteriaHelper);
            default -> throw new StacException(String.format("Unexpected timeline mode %s",
                                                             timelineFiltersByCollection.getMode()),
                                               null,
                                               StacFailureType.TIMELINE_RETRIEVE_MODE);
        };

        // Delegate aggregation
        return timelineBuilder.buildTimelines(timelineFiltersByCollection.getMode(),
                                              timelineFiltersByCollection.getCollections(),
                                              itemStacProperties,
                                              expandDatetime(timelineFiltersByCollection.getFrom()),
                                              expandDatetime(timelineFiltersByCollection.getTo()),
                                              ZoneId.of(timelineFiltersByCollection.getTimezone()));
    }

    private String expandDatetime(String param) {
        return param.contains("T") ? param : param + EXPANDED_DATE;
    }
}
