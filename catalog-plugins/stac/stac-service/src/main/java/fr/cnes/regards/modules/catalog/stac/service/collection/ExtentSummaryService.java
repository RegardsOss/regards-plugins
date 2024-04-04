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

package fr.cnes.regards.modules.catalog.stac.service.collection;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.indexer.domain.aggregation.QueryableAttribute;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;

/**
 * TODO: ExtentSummaryService description
 *
 * @author gandrieu
 */
public interface ExtentSummaryService {

    /**
     * Allows generating {@link QueryableAttribute} instance for the current list of {@link StacProperty}.
     *
     * @param datetimeProp the date time property, mandatory for temporal extent
     * @param otherProps   other properties for the summaries
     * @return the list of {@link QueryableAttribute} to use for ES aggregations
     */
    List<QueryableAttribute> extentSummaryQueryableAttributes(StacProperty datetimeProp, List<StacProperty> otherProps);

    List<AggregationBuilder> extentSummaryAggregationBuilders(StacProperty datetimeProp, List<StacProperty> otherProps);

    /**
     * Helper utility to transform the list of aggregations to a map associating the corresponding {@link StacProperty}.
     *
     * @param props               the list of all {@link StacProperty}
     * @param collectionWithStats aggregate collection with stats
     */
    Map<StacProperty, Aggregation> toAggregationMap(List<StacProperty> props, List<Aggregation> collectionWithStats);

    Extent extractExtent(Map<StacProperty, Aggregation> aggregationMap);

    Map<String, Object> extractSummary(Map<StacProperty, Aggregation> aggregationMap);

}
