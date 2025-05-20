/*
 * Copyright 2017-2025 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.stac.service.collection.common;

import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.framework.module.rest.exception.EntityOperationForbiddenException;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.indexer.domain.aggregation.QueryableAttribute;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.StringMatchType;
import fr.cnes.regards.modules.indexer.service.Searches;
import fr.cnes.regards.modules.opensearch.service.exception.OpenSearchUnknownParameter;
import fr.cnes.regards.modules.search.domain.plugin.CollectionWithStats;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.CatalogSearchService;
import fr.cnes.regards.modules.search.service.SearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Proxy for searching in the catalog.
 *
 * @author Marc SORDI
 */
@Service
public class CatalogSearchProxyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogSearchProxyService.class);

    private static final int PAGE_SIZE = 1000;

    private final CatalogSearchService catalogSearchService;

    public CatalogSearchProxyService(CatalogSearchService catalogSearchService) {
        this.catalogSearchService = catalogSearchService;
    }

    /**
     * Get entity by urn.
     *
     * @param urn urn
     * @return entity
     */
    public <T extends AbstractEntity<?>> T getEntity(UniformResourceName urn)
        throws EntityNotFoundException, EntityOperationForbiddenException {
        return catalogSearchService.get(urn);
    }

    /**
     * Search entities by criterion and return the first page only. If the number of elements is greater than 1000, a
     * warning is logged for now.
     *
     * @param criterion criterion
     * @param type      entity type
     * @return list of entities (with a max size according to page size)
     */
    public <T extends AbstractEntity<?>> List<T> getEntities(ICriterion criterion, EntityType type)
        throws SearchException, OpenSearchUnknownParameter {
        FacetPage<AbstractEntity<?>> page = catalogSearchService.search(criterion,
                                                                        Searches.onSingleEntity(type),
                                                                        null,
                                                                        PageRequest.of(0, PAGE_SIZE));
        if (page.getTotalElements() > PAGE_SIZE) {
            LOGGER.warn("The number of elements is greater than 1000 and won't be returned for now!");
        }
        return (List<T>) page.getContent();
    }

    /**
     * Get first page of entities with this type and matching the criterion
     *
     * @param type entity type
     * @return list of entities (with a max size according to page size)
     */
    public <T extends AbstractEntity<?>> List<T> getEntitiesByType(EntityType type)
        throws SearchException, OpenSearchUnknownParameter {
        ICriterion criterion = ICriterion.not(ICriterion.startsWith("tags", "URN", StringMatchType.KEYWORD));
        return getEntities(criterion, type);
    }

    /**
     * Get collection with data objects stats.
     *
     * @param urn        collection urn
     * @param attributes attributes
     * @return collection with stats
     */
    public CollectionWithStats getCollectionWithDataObjectsStats(UniformResourceName urn,
                                                                 Collection<QueryableAttribute> attributes)
        throws EntityOperationForbiddenException, EntityNotFoundException {
        return catalogSearchService.getCollectionWithDataObjectsStats(urn, SearchType.DATAOBJECTS, attributes);
    }

    /**
     * Get sub collections or datasets for a given urn.
     *
     * @param urn urn
     * @return list of entities
     */
    public List<AbstractEntity<?>> getSubCollectionsOrDatasets(String urn)
        throws SearchException, OpenSearchUnknownParameter {
        ICriterion tags = ICriterion.contains("tags", urn, StringMatchType.KEYWORD);
        FacetPage<AbstractEntity<?>> datasets = catalogSearchService.search(tags,
                                                                            Searches.onSingleEntity(EntityType.DATASET),
                                                                            null,
                                                                            PageRequest.of(0, 1000));
        FacetPage<AbstractEntity<?>> collections = catalogSearchService.search(tags,
                                                                               Searches.onSingleEntity(EntityType.COLLECTION),
                                                                               null,
                                                                               PageRequest.of(0, 1000));
        // Concatenate the two immutable lists
        List<AbstractEntity<?>> datasetsAndCollections = new ArrayList<>();
        datasets.getContent().stream().forEach(datasetsAndCollections::add);
        collections.getContent().stream().forEach(datasetsAndCollections::add);
        return datasetsAndCollections;
    }
}
