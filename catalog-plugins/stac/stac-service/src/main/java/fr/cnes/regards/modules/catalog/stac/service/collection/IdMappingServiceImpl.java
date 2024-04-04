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

import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.multitenant.ITenantResolver;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.indexer.domain.SimpleSearchKey;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.service.ISearchService;
import fr.cnes.regards.modules.indexer.service.Searches;
import io.vavr.collection.List;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * This service manages a cache that for each Collection and Dataset map the STAC identifier with the URN and vice versa.
 */
@Service
public class IdMappingServiceImpl implements IdMappingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdMappingServiceImpl.class);

    private final ConcurrentMap<String, BidiMap<String, String>> idMappingsByTenant = new ConcurrentHashMap<>();

    @Autowired
    private ISearchService searchService;

    @Autowired
    private ITenantResolver tenantResolver;

    /**
     * Runtime tenant resolver
     */
    @Autowired
    private IRuntimeTenantResolver runtimeTenantResolver;

    @Override
    public String getUrnByStacId(String stacId) {
        String urn = idMappingsByTenant.get(runtimeTenantResolver.getTenant()).get(stacId);
        LOGGER.trace("Found URN {} for STAC ID : {}", urn, stacId);
        return urn;
    }

    @Override
    public List<String> getUrnsByStacIds(List<String> stacIds) {
        return stacIds == null || stacIds.isEmpty() ?
            null :
            stacIds.map(idMappingsByTenant.get(runtimeTenantResolver.getTenant())::get);
    }

    @Override
    public String getStacIdByUrn(String urn) {
        String stacId = idMappingsByTenant.get(runtimeTenantResolver.getTenant()).getKey(urn);
        if (stacId == null) {
            LOGGER.debug("Cannot found STAC ID for URN: {}", urn);
            // Refresh cache for current tenant
            initOrUpdateCache(runtimeTenantResolver.getTenant());
            // Retry to get STAC ID (do not call current method to avoid infinite loop)
            // Even if STAC ID should always be retrieved now!
            stacId = idMappingsByTenant.get(runtimeTenantResolver.getTenant()).getKey(urn);
        }
        LOGGER.trace("Found STAC ID {} for URN: {}", stacId, urn);
        return stacId;
    }

    @Override
    public void initOrUpdateCache() {
        // Initialize mappings for all tenants
        for (String tenant : tenantResolver.getAllActiveTenants()) {
            initOrUpdateCache(tenant);
        }
    }

    /**
     * Manage ID mappings for specified tenant
     *
     * @param tenant tenant to be handled
     */
    @Override
    public void initOrUpdateCache(String tenant) {
        try {
            LOGGER.trace("Init or update ID (Collection & Dataset) cache for tenant {}.", tenant);
            runtimeTenantResolver.forceTenant(tenant);
            // Init tenant mappings
            BidiMap<String, String> idMappings = new DualHashBidiMap<>();
            // Init COLLECTION mappings
            idMappings.putAll(buildIdMappings(Searches.onSingleEntity(EntityType.COLLECTION)));
            // Init DATASET mappings
            idMappings.putAll(buildIdMappings(Searches.onSingleEntity(EntityType.DATASET)));
            // Store mappings
            idMappingsByTenant.put(tenant, idMappings);
            // Print cache values
            if (LOGGER.isTraceEnabled()) {
                idMappings.forEach((k, v) -> LOGGER.trace("STAC ID cache for tenant {} : {} <> {}", tenant, k, v));
            }
        } finally {
            runtimeTenantResolver.clearTenant();
        }
    }

    /**
     * Map the stac id with the URN of all entities defined by the {@code searchType} parameter.<br>
     * <b>The map format :</b>
     * <ul>
     * <li>Stac id as key with format : "providerId"_"version" </li>
     * <li>URN as value</li>
     * </ul>
     *
     * @param simpleSearchKey the type of the searched entities.
     * @return The map with IDs, or empty otherwise.
     */
    private <T extends AbstractEntity<?>> java.util.Map<String, String> buildIdMappings(SimpleSearchKey<T> simpleSearchKey) {

        java.util.Map<String, String> mappings = new java.util.HashMap<>();

        // This consumer map the stacId and the URN.
        Consumer<T> consumer = entity -> {
            if (entity.getIpId().getVersion() == 1) {
                // Map first version of provider id with URN without the addition of the latter
                mappings.put(entity.getProviderId(), entity.getIpId().toString());
            } else {
                mappings.put(entity.getProviderId() + "_" + entity.getIpId().getVersion(), entity.getIpId().toString());
            }
        };

        // Retrieves the entities and for each one map the ids.
        FacetPage<T> facetPage = searchService.search(simpleSearchKey, PageRequest.of(0, 1000), ICriterion.all(), null);
        facetPage.forEach(consumer);

        while (facetPage.hasNext()) {
            // Retrieves the entities and for each one map the ids.
            facetPage = searchService.search(simpleSearchKey, facetPage.nextPageable(), ICriterion.all(), null);
            facetPage.forEach(consumer);
        }
        return mappings;
    }
}
