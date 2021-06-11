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
    RESTDYNCOLLVAL_PARSING(HttpStatus.BAD_REQUEST),
    URN_PARSING(HttpStatus.BAD_REQUEST),
    DATEINTERVAL_PARSING(HttpStatus.BAD_REQUEST),

    CORERESPONSE_CONSTRUCTION,
    ITEMCOLLECTIONRESPONSE_CONSTRUCTION,
    COLLECTIONSRESPONSE_CONSTRUCTION,
    COLLECTION_CONSTRUCTION,
    CONFORMANCERESPONSE_CONSTRUCTION,

    DATAOBJECT_JSON_EXTRACTION,
    DATAOBJECT_ATTRIBUTE_VALUE_EXTRACTION,
    DATAOBJECT_TO_ITEM_CONVERSION,

    DATASET_AGGREGATION_FAILURE,

    ROOT_STATIC_COLLECTIONS_QUERY,

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
