package fr.cnes.regards.modules.catalog.stac.service.collection.timeline.builder;

import fr.cnes.regards.modules.search.service.ICatalogSearchService;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;

public class ElasticsearchMultipleHistogramTimelineBuilder extends AbstractElasticsearchMultipleTimelineBuilder {

    public ElasticsearchMultipleHistogramTimelineBuilder(String propertyPath,
                                                         ICatalogSearchService catalogSearchService,
                                                         TimelineCriteriaHelper timelineCriteriaHelper) {
        super(propertyPath, catalogSearchService, timelineCriteriaHelper);
    }

    @Override
    long getBucketValue(Histogram.Bucket bucket) {
        return bucket.getDocCount();
    }
}
