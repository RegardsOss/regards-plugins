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

import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.security.annotation.ResourceAccess;
import fr.cnes.regards.framework.security.role.DefaultRole;
import fr.cnes.regards.modules.catalog.stac.domain.StacConstants;
import fr.cnes.regards.modules.catalog.stac.domain.spec.Catalog;
import fr.cnes.regards.modules.catalog.stac.rest.link.LinkCreatorService;
import fr.cnes.regards.modules.catalog.stac.rest.utils.HeaderUtils;
import fr.cnes.regards.modules.catalog.stac.rest.utils.StacApiConstants;
import fr.cnes.regards.modules.catalog.stac.rest.utils.TryToResponseEntity;
import fr.cnes.regards.modules.catalog.stac.service.collection.CollectionService;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.CORERESPONSE_CONSTRUCTION;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;

/**
 * Core, landing page
 *
 * @see <a href="https://github.com/radiantearth/stac-api-spec/tree/release/v1.0.0/core"></a>
 */
@RestController
@RequestMapping(path = StacApiConstants.STAC_PATH, produces = StacConstants.APPLICATION_JSON_MEDIA_TYPE)
public class CoreController implements TryToResponseEntity {

    private final ConfigurationAccessorFactory configFactory;

    private final LinkCreatorService linker;

    private final IRuntimeTenantResolver runtimeTenantResolver;

    private final CollectionService collectionService;

    @Autowired
    public CoreController(ConfigurationAccessorFactory configFactory,
                          LinkCreatorService linker,
                          IRuntimeTenantResolver runtimeTenantResolver,
                          CollectionService collectionService) {
        this.configFactory = configFactory;
        this.linker = linker;
        this.runtimeTenantResolver = runtimeTenantResolver;
        this.collectionService = collectionService;
    }

    @Operation(summary = "landing page",
               description = "Returns the root STAC Catalog or STAC Collection that is the entry "
                             + "point for users to browse with STAC Browser or for search engines to crawl. "
                             + "This can either return a single STAC Collection or more commonly a STAC catalog.")
    @ApiResponses(value = { @ApiResponse(responseCode = "200",
                                         description = "Links to the API definition and Feature Collections") })
    @ResourceAccess(description = "landing page", role = DefaultRole.PUBLIC)
    @GetMapping
    public ResponseEntity<Catalog> getLandingPage(@RequestHeader Map<String, String> headers) {
        ConfigurationAccessor config = configFactory.makeConfigurationAccessor();
        String title = config.getTitle();
        boolean appendAuthParam = !configFactory.makeConfigurationAccessor().isDisableauthParam();
        OGCFeatLinkCreator linkCreator = linker.makeOGCFeatLinkCreator(appendAuthParam,
                                                                       HeaderUtils.getStacHeaders(headers));
        return toResponseEntity(trying(() -> new Catalog(StacConstants.STAC_API_VERSION,
                                                         HashSet.empty(),
                                                         runtimeTenantResolver.getTenant(),
                                                         title,
                                                         config.getDescription(),
                                                         List.ofAll(collectionService.buildLandingPageLinks(config,
                                                                                                            linkCreator)),
                                                         ConformanceController.CONFORMS_TO)).mapFailure(
            CORERESPONSE_CONSTRUCTION,
            () -> "Failed to build core response"));
    }

}
