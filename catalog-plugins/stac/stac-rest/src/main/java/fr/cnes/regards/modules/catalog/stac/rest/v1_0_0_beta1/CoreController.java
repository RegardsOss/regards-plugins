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
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.CoreResponse;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.StacApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Core, landing page
 *
 * @see <a href="https://github.com/radiantearth/stac-api-spec/tree/v1.0.0-beta.1/core"></a>
 */
@RestController
@RequestMapping(StacApiConstants.STAC_PATH)
public class CoreController {

    @Operation(summary = "landing page",
            description = "Returns the root STAC Catalog or STAC Collection that is the entry " +
                    "point for users to browse with STAC Browser or for search engines to crawl. " +
                    "This can either return a single STAC Collection or more commonly a STAC catalog.")
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Links to the API definition and Feature Collections") })
    @ResourceAccess(
            description = "landing page",
            role = DefaultRole.PUBLIC
    )
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<CoreResponse> conformance() throws ModuleException {
        // TODO
        return null;
    }

}
