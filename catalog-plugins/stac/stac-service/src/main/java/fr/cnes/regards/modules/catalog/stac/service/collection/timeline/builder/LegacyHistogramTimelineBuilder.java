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

import fr.cnes.regards.modules.catalog.stac.service.item.properties.PropertyExtractionService;
import fr.cnes.regards.modules.search.service.ICatalogSearchService;

import java.util.Map;

/**
 * Build a timeline with a number of item matching for each timeline entry
 */
@Deprecated
public class LegacyHistogramTimelineBuilder extends AbstractLegacyTimelineBuilder implements TimelineBuilder {

    public LegacyHistogramTimelineBuilder(ICatalogSearchService catalogSearchService,
                                          PropertyExtractionService propertyExtractionService,
                                          TimelineCriteriaHelper timelineCriteriaHelper) {
        super(catalogSearchService, propertyExtractionService, timelineCriteriaHelper);
    }

    @Override
    protected void doTimelineReport(Map<String, Long> timeline, String key) {
        // Increment entry
        timeline.put(key, timeline.get(key) + 1L);
    }

    @Override
    protected boolean continueReporting(Map<String, Long> timeline) {
        // Continue anyway
        return true;
    }
}
