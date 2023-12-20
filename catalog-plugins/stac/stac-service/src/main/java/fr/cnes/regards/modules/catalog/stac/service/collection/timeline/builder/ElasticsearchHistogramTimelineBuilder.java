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

import fr.cnes.regards.modules.catalog.stac.service.collection.EsAggregationHelper;
import fr.cnes.regards.modules.catalog.stac.service.collection.IdMappingService;
import fr.cnes.regards.modules.search.service.ICatalogSearchService;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ElasticsearchHistogramTimelineBuilder extends AbstractElasticsearchTimelineBuilder
    implements TimelineBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchHistogramTimelineBuilder.class);

    public ElasticsearchHistogramTimelineBuilder(String propertyPath, ICatalogSearchService catalogSearchService) {
        super(propertyPath, catalogSearchService);

    }

    @Override
    long getBucketValue(Histogram.Bucket bucket) {
        return bucket.getDocCount();
    }
}
