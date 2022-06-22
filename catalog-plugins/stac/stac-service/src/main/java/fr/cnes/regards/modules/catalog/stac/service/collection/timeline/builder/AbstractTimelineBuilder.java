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

import fr.cnes.regards.modules.catalog.stac.domain.error.StacException;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.opensearch.service.exception.OpenSearchUnknownParameter;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.ICatalogSearchService;
import fr.cnes.regards.modules.search.service.SearchException;
import org.springframework.data.domain.Pageable;

public abstract class AbstractTimelineBuilder {

    private final ICatalogSearchService catalogSearchService;

    public AbstractTimelineBuilder(ICatalogSearchService catalogSearchService) {
        this.catalogSearchService = catalogSearchService;
    }

    protected FacetPage<AbstractEntity<?>> getTimelineFacetPaged(ICriterion itemCriteria, Pageable pageable, String collectionId) {
        try {
            return catalogSearchService.search(itemCriteria, SearchType.DATAOBJECTS, null, pageable);
        } catch (SearchException | OpenSearchUnknownParameter ex) {
            throw new StacException(String.format("Can not retrieve items of collection %s", collectionId), ex, StacFailureType.TIMELINE_RETRIEVE);
        }
    }
}
