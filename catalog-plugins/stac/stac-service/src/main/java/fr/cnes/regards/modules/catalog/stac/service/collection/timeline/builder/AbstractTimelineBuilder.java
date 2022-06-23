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

import com.google.common.collect.Sets;
import fr.cnes.regards.framework.gson.adapters.OffsetDateTimeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacException;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.opensearch.service.exception.OpenSearchUnknownParameter;
import fr.cnes.regards.modules.search.domain.PropertyBound;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.ICatalogSearchService;
import fr.cnes.regards.modules.search.service.SearchException;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractTimelineBuilder {

    private final ICatalogSearchService catalogSearchService;

    public AbstractTimelineBuilder(ICatalogSearchService catalogSearchService) {
        this.catalogSearchService = catalogSearchService;
    }

    protected FacetPage<AbstractEntity<?>> getTimelineFacetPaged(ICriterion itemCriteria,
                                                                 Pageable pageable,
                                                                 String collectionId) {
        try {
            return catalogSearchService.search(itemCriteria, SearchType.DATAOBJECTS, null, pageable);
        } catch (SearchException | OpenSearchUnknownParameter ex) {
            throw new StacException(String.format("Can not retrieve items of collection %s", collectionId),
                                    ex,
                                    StacFailureType.TIMELINE_RETRIEVE);
        }
    }

    protected Option<Tuple2<OffsetDateTime, OffsetDateTime>> getPropertyBound(ICriterion itemCriteria,
                                                                              String collectionId,
                                                                              io.vavr.collection.List<StacProperty> datetimeStacProperties) {
        return (Option<Tuple2<OffsetDateTime, OffsetDateTime>>) Try.of(() -> {

            // STAC properties start_datetime & end_datetime must exist
            if (datetimeStacProperties.size() == 2) {
                Map<String, StacProperty> stacPropertyMap = datetimeStacProperties.toJavaMap(HashMap::new,
                                                                                             StacProperty::getStacPropertyName,
                                                                                             s -> s);
                String startPropertyName = stacPropertyMap.get(StacSpecConstants.PropertyName.START_DATETIME_PROPERTY_NAME)
                                                          .getRegardsPropertyAccessor()
                                                          .getAttributeModel()
                                                          .getJsonPath();
                String endPropertyName = stacPropertyMap.get(StacSpecConstants.PropertyName.END_DATETIME_PROPERTY_NAME)
                                                        .getRegardsPropertyAccessor()
                                                        .getAttributeModel()
                                                        .getJsonPath();
                // Search property bound
                java.util.List<PropertyBound<?>> bounds = catalogSearchService.retrievePropertiesBounds(Sets.newHashSet(
                    startPropertyName,
                    endPropertyName), itemCriteria, SearchType.DATAOBJECTS);
                Map<String, PropertyBound<?>> boundMap = bounds.stream()
                                                               .collect(Collectors.toMap(p -> p.getPropertyName(),
                                                                                         Function.identity()));
                PropertyBound<String> startBound = (PropertyBound<String>) boundMap.get(startPropertyName);
                PropertyBound<String> endBound = (PropertyBound<String>) boundMap.get(endPropertyName);
                return Option.of(Tuple.of(OffsetDateTimeAdapter.parse(startBound.getLowerBound()),
                                          OffsetDateTimeAdapter.parse(endBound.getUpperBound())));
            } else {
                return Option.none();
            }
        }).getOrElse(Option.none());
    }
}
