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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build a timeline with a simple flag 0 or 1 for each timeline entry
 *
 * @deprecated
 */
@Deprecated
public class LegacyBinaryTimelineBuilder extends AbstractLegacyTimelineBuilder implements TimelineBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyBinaryTimelineBuilder.class);

    // Count number of timeline entry alteration
    private long reportingCount = 0;

    public LegacyBinaryTimelineBuilder(ICatalogSearchService catalogSearchService,
                                       PropertyExtractionService propertyExtractionService,
                                       TimelineCriteriaHelper timelineCriteriaHelper) {
        super(catalogSearchService, propertyExtractionService, timelineCriteriaHelper);
    }

    /**
     * Only mark timeline entry with binary flag 0 or 1
     */
    protected void doTimelineReport(java.util.Map<String, Long> timeline, String key) {
        if (timeline.get(key) == 0L) {
            reportingCount++;
            timeline.put(key, 1L);
        }
    }

    protected boolean continueReporting(java.util.Map<String, Long> timeline) {
        return reportingCount < timeline.size();
    }
}
