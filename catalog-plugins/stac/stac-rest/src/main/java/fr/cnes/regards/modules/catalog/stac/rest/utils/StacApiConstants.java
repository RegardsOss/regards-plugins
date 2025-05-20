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

package fr.cnes.regards.modules.catalog.stac.rest.utils;

/**
 * Constants for the STAC API.
 *
 * @author gandrieu
 */
public final class StacApiConstants {

    public static final String COLLECTION_ID_PARAM = "collectionId";

    public static final String ITEM_ID_PARAM = "itemId";

    public static final String SEARCH_ITEM_BODY_QUERY_PARAM = "itemBody";

    public static final String SEARCH_COLLECTION_BODY_QUERY_PARAM = "collectionBody";

    public static final String LIMIT_QUERY_PARAM = "limit";

    public static final String PAGE_QUERY_PARAM = "page";

    public static final String BBOX_QUERY_PARAM = "bbox";

    public static final String DATETIME_QUERY_PARAM = "datetime";

    public static final String QUERY_QUERY_PARAM = "query";

    public static final String FIELDS_QUERY_PARAM = "fields";

    public static final String IDS_QUERY_PARAM = "ids";

    public static final String COLLECTIONS_QUERY_PARAM = "collections";

    public static final String SORT_BY_QUERY_PARAM = "sortby";

    public static final String STAC_PATH = "/stac";

    public static final String ITEM_PATH = "/items";

    public static final String STAC_CONFORMANCE_PATH = STAC_PATH + "/conformance";

    public static final String STAC_COLLECTIONS_PATH = STAC_PATH + "/collections";

    // Extension to get collection from both item and collection searches
    public static final String STAC_COLLECTION_SEARCH_PATH = STAC_COLLECTIONS_PATH + "/search";

    public static final String STAC_COLLECTION_PATH_SUFFIX = "/{" + COLLECTION_ID_PARAM + "}";

    public static final String STAC_ITEMS_PATH_SUFFIX = STAC_COLLECTION_PATH_SUFFIX + ITEM_PATH;

    public static final String STAC_ITEM_PATH_SUFFIX = STAC_ITEMS_PATH_SUFFIX + "/{" + ITEM_ID_PARAM + "}";

    public static final String STAC_SEARCH_PATH = STAC_PATH + "/search";
    // Extension to get download information for a set of collections

    public static final String STAC_COLLECTION_INFORMATION_PATH = STAC_COLLECTIONS_PATH + "/information";

    public static final String STAC_DOWNLOAD_BY_COLLECTION_PATH = STAC_PATH + "/download/collections";

    public static final String STAC_DOWNLOAD_AS_ZIP_SUFFIX = "/zip";

    public static final String STAC_DOWNLOAD_SAMPLE_AS_ZIP_SUFFIX = "/sample";

    public static final String STAC_DOWNLOAD_SCRIPT_SUFFIX = "/script";

    public static final String STAC_DOWNLOAD_AS_ZIPSTREAM_SUFFIX = "/zipstream";

    public static final String STAC_DOWNLOAD_AS_ZIP_PREPARE_PATH_SUFFIX = STAC_DOWNLOAD_AS_ZIP_SUFFIX + "/prepare";

    public static final String STAC_DOWNLOAD_ALL_COLLECTIONS_AS_ZIP_SUFFIX = STAC_DOWNLOAD_AS_ZIP_SUFFIX;

    public static final String STAC_DOWNLOAD_ALL_COLLECTIONS_SCRIPT_SUFFIX = STAC_DOWNLOAD_SCRIPT_SUFFIX;

    public static final String STAC_DOWNLOAD_ALL_COLLECTIONS_AS_ZIPSTREAM_SUFFIX = STAC_DOWNLOAD_AS_ZIPSTREAM_SUFFIX;

    public static final String STAC_COLLECTION_PLACEHOLDER_PREFIX = "/{collectionId}";

    public static final String STAC_DOWNLOAD_BY_COLLECTION_AS_ZIP_SUFFIX = STAC_COLLECTION_PLACEHOLDER_PREFIX
                                                                           + STAC_DOWNLOAD_AS_ZIP_SUFFIX;

    public static final String STAC_DOWNLOAD_BY_COLLECTION_SCRIPT_SUFFIX = STAC_COLLECTION_PLACEHOLDER_PREFIX
                                                                           + STAC_DOWNLOAD_SCRIPT_SUFFIX;

    public static final String STAC_DOWNLOAD_SAMPLE_BY_COLLECTION_AS_ZIP_SUFFIX = STAC_COLLECTION_PLACEHOLDER_PREFIX
                                                                                  + STAC_DOWNLOAD_SAMPLE_AS_ZIP_SUFFIX
                                                                                  + STAC_DOWNLOAD_AS_ZIP_SUFFIX;

    public static final String STAC_DOWNLOAD_BY_COLLECTION_AS_ZIP_STREAM_SUFFIX = STAC_COLLECTION_PLACEHOLDER_PREFIX
                                                                                  + STAC_DOWNLOAD_AS_ZIPSTREAM_SUFFIX;

    public static final String STAC_DOWNLOAD_SAMPLE_BY_COLLECTION_AS_ZIP_STREAM_SUFFIX =
        STAC_COLLECTION_PLACEHOLDER_PREFIX
        + STAC_DOWNLOAD_SAMPLE_AS_ZIP_SUFFIX
        + STAC_DOWNLOAD_AS_ZIPSTREAM_SUFFIX;

    /**
     * Parameter to pass query parameters on items on a collection search request
     */
    public static final String STAC_COLLECTION_ITEM_QUERY_PARAM_PREFIX = "item_";

    public static final String COLLECTIONS_TIMELINE = "/timeline";

    private StacApiConstants() {
        // Utility class
    }
}
