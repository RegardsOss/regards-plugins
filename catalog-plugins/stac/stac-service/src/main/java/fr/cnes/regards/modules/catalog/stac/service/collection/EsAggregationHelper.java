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

package fr.cnes.regards.modules.catalog.stac.service.collection;

import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import java.time.OffsetDateTime;

/**
 * provides methods to get Elasticsearch aggregations.
 */
public interface EsAggregationHelper {

    Aggregations getAggregationsFor(ICriterion criterion, List<AggregationBuilder> aggDefs);

    Tuple2<OffsetDateTime, OffsetDateTime> dateRange(ICriterion criterion, String regardsAttributePath);

    Long getDatasetTotalCount();

    Aggregations getDatasetAggregations(String aggregationName, ICriterion itemCriteria, Long size);
}
