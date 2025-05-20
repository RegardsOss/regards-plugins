/*
 * Copyright 2017-2025 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.stac.rest.extension.download;

import fr.cnes.regards.framework.feign.security.FeignSecurityManager;
import fr.cnes.regards.framework.security.annotation.ResourceAccess;
import fr.cnes.regards.framework.security.role.DefaultRole;
import fr.cnes.regards.modules.catalog.stac.domain.api.extension.searchcol.CollectionInformation;
import fr.cnes.regards.modules.catalog.stac.domain.api.extension.searchcol.DownloadPreparationResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.extension.searchcol.FiltersByCollection;
import fr.cnes.regards.modules.catalog.stac.rest.link.LinkCreatorService;
import fr.cnes.regards.modules.catalog.stac.rest.utils.TryToResponseEntity;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.CollectionSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static fr.cnes.regards.modules.catalog.stac.rest.utils.StacApiConstants.STAC_COLLECTION_INFORMATION_PATH;

/**
 * Get collection information
 *
 * @author Marc SORDI
 */

@RestController
@RequestMapping(path = STAC_COLLECTION_INFORMATION_PATH)
public class CollectionInformationController implements TryToResponseEntity {

    private final CollectionSearchService collectionSearchService;

    private final LinkCreatorService linkCreatorService;

    private final FeignSecurityManager feignSecurityManager;

    public CollectionInformationController(CollectionSearchService collectionSearchService,
                                           LinkCreatorService linkCreatorService,
                                           FeignSecurityManager feignSecurityManager) {
        this.collectionSearchService = collectionSearchService;
        this.linkCreatorService = linkCreatorService;
        this.feignSecurityManager = feignSecurityManager;
    }

    @Operation(summary = "Compute aggregated information for specified collections",
               description = "For each collection and its item query parameters, aggregated information are computed "
                             + "so that the user can have a global view of the collection content")
    @ApiResponses(value = { @ApiResponse(responseCode = "200",
                                         description = "Aggregated information computed",
                                         content = { @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                              schema = @Schema(implementation = DownloadPreparationResponse.class)) }) })
    @ResourceAccess(description = "Get collection aggregated information", role = DefaultRole.PUBLIC)
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CollectionInformation> getCollectionInformation(
        @RequestBody FiltersByCollection filtersByCollection) {
        return toResponseEntity(collectionSearchService.getCollectionInformation(filtersByCollection,
                                                                                 linkCreatorService.makeDownloadLinkCreator(
                                                                                     feignSecurityManager,
                                                                                     Optional.ofNullable(
                                                                                                 filtersByCollection.getAppendAuthParameters())
                                                                                             .orElse(false))));
    }
}
