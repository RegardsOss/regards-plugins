/*
 * Copyright 2017-2022 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.security.annotation.ResourceAccess;
import fr.cnes.regards.framework.security.role.DefaultRole;
import fr.cnes.regards.modules.catalog.femdriver.dto.FeatureUpdateRequest;
import fr.cnes.regards.modules.catalog.femdriver.dto.RecipientsSearchRequest;
import fr.cnes.regards.modules.catalog.femdriver.service.FemDriverService;
import fr.cnes.regards.modules.search.dto.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * REST Controller for FEM Driver
 *
 * @author Sébastien Binda
 */
@RestController
@RequestMapping(FemDriverController.FEM_DRIVER_PATH)
public class FemDriverController {

    public static final String FEM_DRIVER_PATH = "/femdriver/features";

    public static final String FEM_DRIVER_UPDATE_PATH = "/update";

    public static final String FEM_DRIVER_DELETE_PATH = "/delete";

    public static final String FEM_DRIVER_NOTIFY_PATH = "/notify";

    @Autowired
    private FemDriverService femDriverService;

    @Operation(summary = "Schedule FEM Feature updates",
               description = "Schedule feature updates on FEM microserice for each catalog entity matching given search request")
    @ApiResponses(value = { @ApiResponse(responseCode = "204", description = "No response content") })
    @ResourceAccess(description = "Schedule feature updates on FEM microserice for each catalog entity matching given search request",
                    role = DefaultRole.EXPLOIT)
    @RequestMapping(path = FEM_DRIVER_UPDATE_PATH, method = RequestMethod.POST)
    public ResponseEntity<Void> updateFeatures(
        @Parameter(description = "Contain feature seach request and feature properties to update") @Valid @RequestBody
        FeatureUpdateRequest request) throws ModuleException {
        femDriverService.scheduleUpdate(request);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(summary = "Schedule FEM Feature deletion",
               description = "Schedule feature deletion on FEM microserice for each catalog entity matching given search request")
    @ApiResponses(value = { @ApiResponse(responseCode = "204", description = "No response content") })
    @ResourceAccess(description = "Schedule feature deletion on FEM microserice for each catalog entity matching given search request",
                    role = DefaultRole.EXPLOIT)
    @RequestMapping(path = FEM_DRIVER_DELETE_PATH, method = RequestMethod.POST)
    public ResponseEntity<Void> deleteFeatures(
        @Parameter(description = "Contain feature seach request") @Valid @RequestBody SearchRequest request)
        throws ModuleException {
        femDriverService.scheduleDeletion(request);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(summary = "Schedule FEM Feature notification",
               description = "Schedule feature notification on FEM microserice for each catalog entity matching given search request")
    @ApiResponses(value = { @ApiResponse(responseCode = "204", description = "No response content") })
    @ResourceAccess(description = "Schedule feature notification on FEM microserice for each catalog entity matching given search request",
                    role = DefaultRole.EXPLOIT)
    @RequestMapping(path = FEM_DRIVER_NOTIFY_PATH, method = RequestMethod.POST)
    public ResponseEntity<Void> notifyFeatures(
        @Parameter(description = "Contain feature seach request") @Valid @RequestBody RecipientsSearchRequest request)
        throws ModuleException {
        femDriverService.scheduleNotification(request);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}
