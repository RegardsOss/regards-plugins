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

package fr.cnes.regards.modules.catalog.stac.rest;

import fr.cnes.regards.framework.security.annotation.ResourceAccess;
import fr.cnes.regards.framework.security.role.DefaultRole;
import fr.cnes.regards.modules.catalog.stac.domain.StacConstants;
import fr.cnes.regards.modules.catalog.stac.domain.api.ConformanceResponse;
import fr.cnes.regards.modules.catalog.stac.rest.utils.StacApiConstants;
import fr.cnes.regards.modules.catalog.stac.rest.utils.TryToResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.CONFORMANCERESPONSE_CONSTRUCTION;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;

/**
 * Conformance page
 *
 * @see <a href="https://github.com/radiantearth/stac-api-spec/tree/release/v1.0.0/ogcapi-features"></a>
 */
@RestController
@RequestMapping(path = StacApiConstants.STAC_CONFORMANCE_PATH, produces = StacConstants.APPLICATION_JSON_MEDIA_TYPE)
@SuppressWarnings("java:S1192") // Suppress warning for "String literals should not be duplicated" for URIs.
// Duplication of string literals is intentional: these URIs come from the STAC API spec and are repeated for clarity and readability.
public class ConformanceController implements TryToResponseEntity {

    public static final List<String> CONFORMS_TO = List.of("https://api.stacspec.org/v1.0.0/core",
                                                           "https://api.stacspec.org/v1.0.0/collections",
                                                           "https://api.stacspec.org/v1.0.0/ogcapi-features",
                                                           "https://api.stacspec.org/v1.0.0/ogcapi-features#fields",
                                                           "https://api.stacspec.org/v1.0.0/ogcapi-features#query",
                                                           "https://api.stacspec.org/v1.0.0/ogcapi-features#sort",
                                                           "https://api.stacspec.org/v1.0.0-rc.2/ogcapi-features#context",
                                                           "https://api.stacspec.org/v1.0.0/item-search",
                                                           "https://api.stacspec.org/v1.0.0/item-search#fields",
                                                           "https://api.stacspec.org/v1.0.0/item-search#query",
                                                           "https://api.stacspec.org/v1.0.0/item-search#sort",
                                                           "https://api.stacspec.org/v1.0.0-rc.2/item-search#context",
                                                           "https://api.stacspec.org/v1.0.0-rc.1/collection-search",
                                                           "https://api.stacspec.org/v1.0.0-rc"
                                                           + ".1/collection-search#fields",
                                                           "https://api.stacspec.org/v1.0.0-rc"
                                                           + ".1/collection-search#query",
                                                           "https://api.stacspec.org/v1.0.0-rc"
                                                           + ".1/collection-search#sort",
                                                           "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/core",
                                                           "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/oas30",
                                                           "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/geojson");

    @Operation(summary = "information about specifications that this API conforms to",
               description = "A list of all conformance classes specified in a standard that the server conforms to.")
    @ApiResponses(value = { @ApiResponse(responseCode = "200",
                                         description = "The URIs of all conformance classes supported by the server.") })
    @ResourceAccess(description = "information about specifications that this API conforms to",
                    role = DefaultRole.PUBLIC)
    @GetMapping
    public ResponseEntity<ConformanceResponse> getConformanceDeclaration() {
        return toResponseEntity(trying(() -> new ConformanceResponse(CONFORMS_TO)).mapFailure(
            CONFORMANCERESPONSE_CONSTRUCTION,
            () -> "Failed to build conformance response"));
    }

}
