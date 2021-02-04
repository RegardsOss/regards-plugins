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
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBodyFactory;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.ItemCollection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.vavr.collection.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.StacApiConstants.*;

/**
 * Search API
 *
 * @see <a href="https://github.com/radiantearth/stac-api-spec/tree/v1.0.0-beta.1/item-search">Description</a>>
 */
@RestController
@RequestMapping(STAC_SEARCH_PATH)
public class ItemSearchController {

    private final ItemSearchBodyFactory itemSearchBodyFactory;

    @Autowired
    public ItemSearchController(ItemSearchBodyFactory itemSearchBodyFactory) {
        this.itemSearchBodyFactory = itemSearchBodyFactory;
    }

    @Operation(summary = "search with simple filtering",
            description = "Retrieve Items matching filters. Intended as a shorthand API for simple queries.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "An ItemCollection.")
    })
    @ResourceAccess(
            description = "search with simple filtering",
            role = DefaultRole.REGISTERED_USER
    )
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<ItemCollection> simple(
            @RequestParam(name = LIMIT_QUERY_PARAM, required = false) Integer limit,
            @RequestParam(name = BBOX_QUERY_PARAM, required = false) BBox bbox,
            @RequestParam(name = DATETIME_QUERY_PARAM, required = false) String datetime,
            @RequestParam(name = COLLECTIONS_QUERY_PARAM, required = false) List<String> collections,
            @RequestParam(name = IDS_QUERY_PARAM, required = false) List<String> ids,
            @RequestParam(name = FIELDS_QUERY_PARAM, required = false) String fields,
            @RequestParam(name = QUERY_QUERY_PARAM, required = false) String query,
            @RequestParam(name = SORTBY_QUERY_PARAM, required = false) String sortBy
    ) throws ModuleException {
        // TODO
        return null;
    }

    @Operation(summary = "search with complex filtering",
            description = "Retrieve Items matching filters. Full-featured query API.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "An ItemCollection.")
    })
    @ResourceAccess(
            description = "search with complex filtering",
            role = DefaultRole.REGISTERED_USER
    )
    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<ItemCollection> complex(
            @RequestBody ItemSearchBody itemSearch
            ) throws ModuleException {
        // TODO
        return null;
    }
}
