/*
 * Copyright 2017-2021 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

package fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils;

/**
 * TODO: Path description
 *
 * @author gandrieu
 */
public interface StacApiConstants {

    String COLLECTION_ID_PARAM = "collectionId";

    String ITEM_ID_PARAM = "itemId";

    String SEARCH_ITEMBODY_QUERY_PARAM = "itemBody";

    String SEARCH_COLLECTIONBODY_QUERY_PARAM = "collectionBody";

    String LIMIT_QUERY_PARAM = "limit";

    String PAGE_QUERY_PARAM = "page";

    String BBOX_QUERY_PARAM = "bbox";

    String DATETIME_QUERY_PARAM = "datetime";

    String QUERY_QUERY_PARAM = "query";

    String FIELDS_QUERY_PARAM = "fields";

    String IDS_QUERY_PARAM = "ids";

    String COLLECTIONS_QUERY_PARAM = "collections";

    String SORTBY_QUERY_PARAM = "sortBy";

    String STAC_PATH = "/stac";

    String STAC_CONFORMANCE_PATH = STAC_PATH + "/conformance";

    String STAC_COLLECTIONS_PATH = STAC_PATH + "/collections";

    String STAC_COLLECTION_PATH_SUFFIX = "/{" + COLLECTION_ID_PARAM + "}";

    String STAC_ITEMS_PATH_SUFFIX = STAC_COLLECTION_PATH_SUFFIX + "/items";

    String STAC_ITEM_PATH_SUFFIX = STAC_ITEMS_PATH_SUFFIX + "/{" + ITEM_ID_PARAM + "}";

    String STAC_SEARCH_PATH = STAC_PATH + "/search";

    // Extension to get collection from an item search 

    String STAC_COLLECTION_SEARCH_PATH = STAC_COLLECTIONS_PATH + "/search";

}
