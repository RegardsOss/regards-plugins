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
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ConformanceResponse;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.StacApiConstants;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.TryToResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Asset.MediaType.APPLICATION_JSON;

/**
 * Conformance page
 *
 * @see <a href="https://github.com/radiantearth/stac-api-spec/tree/v1.0.0-beta.1/ogcapi-features"></a>
 */
@RestController
@RequestMapping(
        path = StacApiConstants.STAC_CONFORMANCE_PATH,
        produces = APPLICATION_JSON,
        consumes = APPLICATION_JSON
)
public class ConformanceController implements TryToResponseEntity {

    public static final List<String> CONFORMANCES = List.of(
        "https://api.stacspec.org/v1.0.0-beta.1/core",
        "https://api.stacspec.org/v1.0.0-beta.1/item-search",
        "https://api.stacspec.org/v1.0.0-beta.1/ogcapi-features",
        "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/core",
        "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/oas30",
        "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/geojson"
    );

    @Operation(summary = "information about specifications that this API conforms to",
            description = "A list of all conformance classes specified in a standard that the server conforms to.")
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "The URIs of all conformance classes supported by the server.") })
    @ResourceAccess(
            description = "information about specifications that this API conforms to",
            role = DefaultRole.PUBLIC
    )
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<ConformanceResponse> conformance() throws ModuleException {
        return toResponseEntity(Try.of(() -> new ConformanceResponse(CONFORMANCES)));
    }

}
