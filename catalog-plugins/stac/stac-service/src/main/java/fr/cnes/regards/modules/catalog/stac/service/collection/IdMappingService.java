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

import fr.cnes.regards.framework.urn.UniformResourceName;
import io.vavr.collection.List;

/**
 * STAC identifier service, allows switching between business provider id and internal URN
 */
public interface IdMappingService {

    /**
     * Get the URN from the STAC id for entities of type :
     * <ul>
     *     <li>{@link fr.cnes.regards.framework.urn.EntityType#DATASET}</li>
     *     <li>{@link fr.cnes.regards.framework.urn.EntityType#COLLECTION}</li>
     * </ul>
     */
    String getUrnByStacId(String stacId);

    /**
     * Get the URNs from the STAC ids for entities of type :
     * <ul>
     *     <li>{@link fr.cnes.regards.framework.urn.EntityType#DATASET}</li>
     *     <li>{@link fr.cnes.regards.framework.urn.EntityType#COLLECTION}</li>
     * </ul>
     */
    List<String> getUrnsByStacIds(List<String> stacIds);

    /**
     * Get the STAC id from the URN for entities of type :
     * <ul>
     *     <li>{@link fr.cnes.regards.framework.urn.EntityType#DATASET}</li>
     *     <li>{@link fr.cnes.regards.framework.urn.EntityType#COLLECTION}</li>
     * </ul>
     */
    String getStacIdByUrn(String urn);

    /**
     * Initialize or update multitenant cache for the mapping between STAC id and URN for entities of type :
     * <ul>
     *     <li>{@link fr.cnes.regards.framework.urn.EntityType#DATASET}</li>
     *     <li>{@link fr.cnes.regards.framework.urn.EntityType#COLLECTION}</li>
     * </ul>
     */
    void initOrUpdateCache();

    /**
     * Initialize or update tenant cache for the mapping between STAC id and URN for entities of type :
     * <ul>
     *     <li>{@link fr.cnes.regards.framework.urn.EntityType#DATASET}</li>
     *     <li>{@link fr.cnes.regards.framework.urn.EntityType#COLLECTION}</li>
     * </ul>
     *
     * @param tenant the tenant for which the cache should be initialized or updated
     */
    void initOrUpdateCache(String tenant);

    /**
     * For an <b>item</b>, compute the STAC id from the URN and the provider id according to the
     * humanReadable flag
     *
     * @param urn           the URN
     * @param providerId    the provider id
     * @param humanReadable the humanReadable flag
     * @return the STAC id
     */
    String getItemId(UniformResourceName urn, String providerId, boolean humanReadable);

    /**
     * For an <b>item</b>, rebuild the URN from the STAC id
     * automatically detecting if it's a human-readable id or not
     *
     * @param itemId the STAC id
     */
    String geItemUrnFromId(String itemId);

    /**
     * For a list of <b>items</b>, rebuild the URNs from the STAC ids
     * automatically detecting if it's human-readable ids or not
     */
    List<String> getItemUrnsFromIds(List<String> itemIds);
}