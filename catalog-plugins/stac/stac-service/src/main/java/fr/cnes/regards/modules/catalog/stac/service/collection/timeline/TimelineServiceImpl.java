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
package fr.cnes.regards.modules.catalog.stac.service.collection.timeline;

import fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.CollectionSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.FiltersByCollection;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.TimelineByCollectionResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.TimelineFiltersByCollection;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacException;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.service.collection.IdMappingService;
import fr.cnes.regards.modules.catalog.stac.service.collection.timeline.builder.BinaryTimelineBuilder;
import fr.cnes.regards.modules.catalog.stac.service.collection.timeline.builder.HistogramTimelineBuilder;
import fr.cnes.regards.modules.catalog.stac.service.collection.timeline.builder.TimelineBuilder;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.criterion.StacSearchCriterionBuilder;
import fr.cnes.regards.modules.catalog.stac.service.item.properties.PropertyExtractionService;
import fr.cnes.regards.modules.catalog.stac.service.search.AbstractSearchService;
import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.StringMatchType;
import fr.cnes.regards.modules.search.service.ICatalogSearchService;
import io.vavr.collection.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class TimelineServiceImpl extends AbstractSearchService implements TimelineService {

    private static final String EXPANDED_DATE = "T00:00:00Z";

    @Value("${regards.timeline.two.many.results.threshold:30000}")
    private long twoManyResultsThreshold;

    @Autowired
    private ConfigurationAccessorFactory configurationAccessorFactory;

    @Autowired
    private StacSearchCriterionBuilder searchCriterionBuilder;

    @Autowired
    private ICatalogSearchService catalogSearchService;

    @Autowired
    private PropertyExtractionService propertyExtractionService;

    @Autowired
    private IdMappingService idMappingService;

    @Override
    public TimelineByCollectionResponse buildCollectionsTimeline(TimelineFiltersByCollection timelineFiltersByCollection) {

        java.util.List<TimelineByCollectionResponse.CollectionTimeline> collectionTimelines = new ArrayList<>();

        timelineFiltersByCollection.getCollections().forEach(collectionFilters -> {

            // Retrieve configured item properties
            ConfigurationAccessor configurationAccessor = configurationAccessorFactory.makeConfigurationAccessor();
            List<StacProperty> itemStacProperties = configurationAccessor.getStacProperties();

            ICriterion itemCriteria = getTimelineCriteria(collectionFilters, itemStacProperties);

            // Set pagination context (considering first page, index 1 in STAC paradigm)
            List<SearchBody.SortBy> sortBy = List.of(new SearchBody.SortBy(StacSpecConstants.PropertyName.START_DATETIME_PROPERTY_NAME,
                                                                           SearchBody.SortBy.Direction.ASC),
                                                     new SearchBody.SortBy(StacSpecConstants.PropertyName.END_DATETIME_PROPERTY_NAME,
                                                                           SearchBody.SortBy.Direction.ASC));
            Pageable pageable = pageable(1000, 1, sortBy, itemStacProperties);

            // Init timeline builder
            TimelineBuilder timelineBuilder;
            switch (timelineFiltersByCollection.getMode()) {
                case BINARY:
                case BINARY_MAP:
                    timelineBuilder = new BinaryTimelineBuilder(catalogSearchService,
                                                                propertyExtractionService).withTwoManyResultsThreshold(
                        twoManyResultsThreshold);
                    break;
                case HISTOGRAM:
                case HISTOGRAM_MAP:
                    timelineBuilder = new HistogramTimelineBuilder(catalogSearchService,
                                                                   propertyExtractionService).withTwoManyResultsThreshold(
                        twoManyResultsThreshold);
                    break;
                default:
                    throw new StacException(String.format("Unexpected timeline mode %s",
                                                          timelineFiltersByCollection.getMode()),
                                            null,
                                            StacFailureType.TIMELINE_RETRIEVE_MODE);
            }

            java.util.Map<String, Long> timeline = timelineBuilder.buildTimeline(itemCriteria,
                                                                                 pageable,
                                                                                 collectionFilters.getCollectionId(),
                                                                                 itemStacProperties,
                                                                                 expandDatetime(
                                                                                     timelineFiltersByCollection.getFrom()),
                                                                                 expandDatetime(
                                                                                     timelineFiltersByCollection.getTo()));
            collectionTimelines.add(formatTimelineOutput(timeline,
                                                         collectionFilters.getCollectionId(),
                                                         collectionFilters.getCorrelationId(),
                                                         timelineFiltersByCollection.getMode()));
        });

        return new TimelineByCollectionResponse(collectionTimelines);
    }

    private TimelineByCollectionResponse.CollectionTimeline formatTimelineOutput(java.util.Map<String, Long> timeline,
                                                                                 String collectionId,
                                                                                 String correlationId,
                                                                                 TimelineFiltersByCollection.TimelineMode mode) {
        switch (mode) {
            case BINARY:
            case HISTOGRAM:
                return new TimelineByCollectionResponse.CollectionTimeline(collectionId,
                                                                           correlationId,
                                                                           timeline.values());
            case BINARY_MAP:
            case HISTOGRAM_MAP:
                return new TimelineByCollectionResponse.CollectionTimeline(collectionId, correlationId, timeline);
            default:
                throw new StacException(String.format("Unexpected timeline mode %s", mode),
                                        null,
                                        StacFailureType.TIMELINE_RETRIEVE_MODE);
        }
    }

    private ICriterion getTimelineCriteria(FiltersByCollection.CollectionFilters collectionFilters,
                                           List<StacProperty> itemStacProperties) {

        CollectionSearchBody.CollectionItemSearchBody collectionItemSearchBody = collectionFilters.getFilters()
                                                                                 == null ?
            CollectionSearchBody.CollectionItemSearchBody.builder().build() :
            collectionFilters.getFilters();

        String urn_tag = idMappingService.getUrnByStacId(collectionFilters.getCollectionId());
        if (urn_tag == null) {
            throw new StacException(String.format("Unknown collection identifier %s",
                                                  collectionFilters.getCollectionId()),
                                    null,
                                    StacFailureType.MAPPING_ID_FAILURE);
        }

        return ICriterion.and(ICriterion.eq(StaticProperties.FEATURE_TAGS, urn_tag, StringMatchType.KEYWORD),
                              searchCriterionBuilder.buildCriterion(itemStacProperties, collectionItemSearchBody)
                                                    .getOrElse(ICriterion.all()));

    }

    private String expandDatetime(String param) {
        return param.contains("T") ? param : param + EXPANDED_DATE;
    }
}
