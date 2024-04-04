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

package fr.cnes.regards.modules.catalog.stac.domain.error;

import org.springframework.http.HttpStatus;

/**
 * Determines the types of error which can happen in the STAC plugin.
 */
public enum StacFailureType {

    PLUGIN_CONFIGURATION_ACCESS,

    SEARCH,
    SEARCH_ITEM,
    PERCENTAGE_CONVERSION,

    URI_PARAM_ADDING,
    URI_AUTH_PARAM_ADDING,

    FIELDS_PARSING(HttpStatus.BAD_REQUEST),
    SORTBY_PARSING(HttpStatus.BAD_REQUEST),
    OFFSETDATETIME_PARSING(HttpStatus.BAD_REQUEST),
    ITEMSEARCHBODY_PARSING(HttpStatus.BAD_REQUEST),
    COLLECTIONSEARCHBODY_PARSING(HttpStatus.BAD_REQUEST),
    RESTDYNCOLLVAL_PARSING(HttpStatus.BAD_REQUEST),
    URN_PARSING(HttpStatus.BAD_REQUEST),
    DATEINTERVAL_PARSING(HttpStatus.BAD_REQUEST),

    DOWNLOAD_PREPARATION(HttpStatus.BAD_REQUEST),
    DOWNLOAD_COLLECTION_ID_PARSING(HttpStatus.BAD_REQUEST),
    DOWNLOAD_COLLECTION_SUMMARY(HttpStatus.BAD_REQUEST),
    DOWNLOAD_COLLECTION_SAMPLE_SUMMARY(HttpStatus.BAD_REQUEST),
    DOWNLOAD_COLLECTION_EMPTY(HttpStatus.BAD_REQUEST),
    DOWNLOAD_UNKNOWN_TINYURL(HttpStatus.BAD_REQUEST),
    DOWNLOAD_UNKNOWN_CLASS_OF_TINYURL(HttpStatus.BAD_REQUEST),
    DOWNLOAD_RETRIEVE_FILES(HttpStatus.BAD_REQUEST),
    DOWNLOAD_RETRIEVE_SAMPLE_FILES(HttpStatus.BAD_REQUEST),
    DOWNLOAD_IO_EXCEPTION(HttpStatus.BAD_REQUEST),
    DONWLOAD_BAD_FILE_LOCATION(HttpStatus.BAD_REQUEST),
    DOWNLOAD_SAMPLE_ONLY_FOR_SINGLE_COLLECTION(HttpStatus.BAD_REQUEST),

    MOD_ZIP_DESC_BUILD(HttpStatus.BAD_REQUEST),
    CORERESPONSE_CONSTRUCTION,
    ITEMCOLLECTIONRESPONSE_CONSTRUCTION,
    COLLECTIONSRESPONSE_CONSTRUCTION,
    COLLECTION_CONSTRUCTION,
    CONFORMANCERESPONSE_CONSTRUCTION,

    JINJA_TEMPLATE_LOADING_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR),

    ENTITY_JSON_EXTRACTION,
    ENTITY_ATTRIBUTE_VALUE_EXTRACTION,
    ENTITY_TO_ITEM_CONVERSION,

    DATASET_AGGREGATION_FAILURE,

    ROOT_STATIC_COLLECTIONS_QUERY,

    TIMELINE_RETRIEVE(HttpStatus.BAD_REQUEST),
    TIMELINE_RETRIEVE_MODE(HttpStatus.BAD_REQUEST),

    COLLECTION_DATASET_IDS,
    MAPPING_ID_FAILURE(HttpStatus.BAD_REQUEST),

    UNKNOWN;

    private final HttpStatus status;

    StacFailureType(HttpStatus status) {
        this.status = status;
    }

    StacFailureType() {
        this(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
