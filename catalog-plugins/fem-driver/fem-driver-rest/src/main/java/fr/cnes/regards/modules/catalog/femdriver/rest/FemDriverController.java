/*
 * Copyright 2017-2020 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.femdriver.rest;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import fr.cnes.regards.framework.geojson.GeoJsonMediaType;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.security.annotation.ResourceAccess;
import fr.cnes.regards.modules.catalog.femdriver.dto.FeatureUpdateRequest;
import fr.cnes.regards.modules.catalog.femdriver.service.FemDriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/**
 * REST Controller for FEM Driver
 *
 * @author Sébastien Binda
 *
 */
@RestController
@RequestMapping(FemDriverController.FEM_DRIVER_PATH)
public class FemDriverController {

    public static final String FEM_DRIVER_PATH = "/femdriver";

    @Autowired
    private FemDriverService femDriverService;

    @Operation(summary = "Publish a new feature and return the request id",
            description = "Publish a new feature and return the request id")
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "A RequestInfo") })
    @ResourceAccess(description = "Publish a new feature and return the request id")
    @RequestMapping(method = RequestMethod.POST, consumes = GeoJsonMediaType.APPLICATION_GEOJSON_VALUE)
    public ResponseEntity<Void> createFeatures(
            @Parameter(description = "Contain all Features to handle") @Valid @RequestBody FeatureUpdateRequest request)
            throws ModuleException {
        femDriverService.scheduleUpdate(request);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
