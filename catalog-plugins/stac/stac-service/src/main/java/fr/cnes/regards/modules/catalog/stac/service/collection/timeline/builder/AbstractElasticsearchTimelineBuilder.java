package fr.cnes.regards.modules.catalog.stac.service.collection.timeline.builder;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.search.service.ICatalogSearchService;
import io.vavr.collection.List;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedDateHistogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import fr.cnes.regards.modules.indexer.service.Searches;
import fr.cnes.regards.framework.urn.EntityType;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

public abstract class AbstractElasticsearchTimelineBuilder extends AbstractTimelineBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractElasticsearchTimelineBuilder.class);

    private final String propertyPath;

    public AbstractElasticsearchTimelineBuilder(String propertyPath, ICatalogSearchService catalogSearchService) {
        super(catalogSearchService);
        this.propertyPath = propertyPath;
    }

    @Override
    public Map<String, Long> buildTimeline(ICriterion itemCriteria,
                                           Pageable pageable,
                                           String collectionId,
                                           List<StacProperty> itemStacProperties,
                                           String from,
                                           String to,
                                           ZoneOffset timeZone) {

        long requestStart = System.currentTimeMillis();

        // Locate the bounds to 00:00:00.
        OffsetDateTime timelineStart = parseODT(from);
        OffsetDateTime timelineEnd = parseODT(to);

        // Initialize result map with 0
        java.util.Map<String, Long> timeline = initTimeline(from, to);
        LOGGER.trace("---> Timeline initialized in {} ms", System.currentTimeMillis() - requestStart);

        // Delegate aggregation
        ParsedDateHistogram parsedDateHistogram = catalogSearchService.getDateHistogram(Searches.onSingleEntity(
                                                                                            EntityType.DATA),
                                                                                        propertyPath,
                                                                                        itemCriteria,
                                                                                        DateHistogramInterval.DAY,
                                                                                        timelineStart,
                                                                                        timelineEnd,
                                                                                        timeZone);
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
        return timeline;
    }

    abstract long getBucketValue(Histogram.Bucket bucket);
}
