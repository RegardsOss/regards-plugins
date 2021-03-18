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

package fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.security.annotation.ResourceAccess;
import fr.cnes.regards.framework.security.role.DefaultRole;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.CollectionsResponse;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Item;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemCollectionResponse;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Asset.MediaType.APPLICATION_JSON;
import static fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.StacApiConstants.*;

/**
 * OGC Feature API
 *
 * @see <a href="https://github.com/radiantearth/stac-api-spec/tree/v1.0.0-beta.1/ogcapi-features"></a>
 */
@RestController
@RequestMapping(
        path = STAC_COLLECTIONS_PATH,
        produces = APPLICATION_JSON,
        consumes = APPLICATION_JSON
)
public class OGCFeaturesController {

    @Operation(summary = "the feature collections in the dataset",
            description = "A body of Feature Collections that belong or are used together with additional links. " +
                    "Request may not return the full set of metadata per Feature Collection.")
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "The feature collections shared by this API.") })
    @ResourceAccess(
            description = "the feature collections in the dataset",
            role = DefaultRole.PUBLIC
    )
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<CollectionsResponse> collections() throws ModuleException {
        // TODO
        return null;
    }

    /**
     * Get the stac collection from Regards collection
     * @param collectionId this is the urn
     * @return
     * @throws ModuleException
     */
    @Operation(summary = "describe the feature collection with id `collectionId`",
            description = "A single Feature Collection for the given if collectionId. " +
                    "Request this endpoint to get a full list of metadata for the Feature Collection.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Information about the feature collection with id collectionId."),
            @ApiResponse(responseCode = "404", description = "Collection not found.")
    })
    @ResourceAccess(
            description = "describe the feature collection with id `collectionId`",
            role = DefaultRole.PUBLIC
    )
    @RequestMapping(path = STAC_COLLECTION_PATH_SUFFIX, method = RequestMethod.GET)
    public ResponseEntity<Collection> collection(
            @PathVariable(COLLECTION_ID_PARAM) String collectionId
    ) throws ModuleException {
        // TODO
        return null;
    }

    @Operation(summary = "fetch features",
            description = "Fetch features of the feature collection with id collectionId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The response is a document consisting of features in the collection."),
            @ApiResponse(responseCode = "404", description = "Collection not found.")
    })
    @ResourceAccess(
            description = "fetch features",
            role = DefaultRole.PUBLIC
    )
    @RequestMapping(path = STAC_ITEMS_PATH_SUFFIX, method = RequestMethod.GET)
    public ResponseEntity<ItemCollectionResponse> features(
            @PathVariable(name = COLLECTION_ID_PARAM) String collectionId,
            @RequestParam(name = LIMIT_QUERY_PARAM, required = false) Integer limit,
            @RequestParam(name = BBOX_QUERY_PARAM, required = false) BBox bbox,
            @RequestParam(name = DATETIME_QUERY_PARAM, required = false) String datetime
    ) throws ModuleException {
        // TODO
        return null;
    }

    @Operation(summary = "fetch a single feature",
            description = "Fetch the feature with id featureId in the feature collection with id collectionId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The feature content."),
            @ApiResponse(responseCode = "404", description = "Feature not found.")
    })
    @ResourceAccess(
            description = "fetch a single feature",
            role = DefaultRole.PUBLIC
    )
    @RequestMapping(path = STAC_ITEM_PATH_SUFFIX, method = RequestMethod.GET)
    public ResponseEntity<Item> feature(
            @PathVariable(name = COLLECTION_ID_PARAM) String collectionId,
            @PathVariable(name = ITEM_ID_PARAM) String featureId
    ) throws ModuleException {
        // TODO
        return null;
    }
}
