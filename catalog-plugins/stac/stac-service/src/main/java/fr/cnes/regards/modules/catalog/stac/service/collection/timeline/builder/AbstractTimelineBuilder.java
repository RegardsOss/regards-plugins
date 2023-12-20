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

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractTimelineBuilder implements TimelineBuilder {

    protected final ICatalogSearchService catalogSearchService;

    public AbstractTimelineBuilder(ICatalogSearchService catalogSearchService) {
        this.catalogSearchService = catalogSearchService;
    }

    protected java.util.Map<String, Long> initTimeline(String from, String to) {
        // Locate the bounds to 00:00:00.
        OffsetDateTime timelineStart = parseODT(from);
        OffsetDateTime timelineEnd = parseODT(to);
        long timelineNbDays = ChronoUnit.DAYS.between(timelineStart, timelineEnd);

        // Initialize result map with 0
        java.util.Map<String, Long> timeline = new TreeMap<>();
        java.util.stream.Stream.iterate(timelineStart, currentDate -> currentDate.plusDays(1))
                               .limit(timelineNbDays + 1)
                               .forEach(currentDate -> timeline.put(getMapKey(currentDate), 0L));
        return timeline;
    }

    protected OffsetDateTime parseODT(String odt) {
        // Locate the bounds to 00:00:00.
        return OffsetDateTimeAdapter.parse(odt).with(LocalTime.MIDNIGHT);
    }

    /**
     * @return key according to the context. At the moment, the current day!
     */
    protected String getMapKey(OffsetDateTime offsetDateTime) {
        return offsetDateTime.toLocalDate().toString();
    }
}
