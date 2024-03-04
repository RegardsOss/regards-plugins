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
package fr.cnes.regards.modules.storage.plugins.s3.mock;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for FEM Driver
 *
 * @author Sébastien Binda
 */
@RestController
@RequestMapping("s3-plugin-mock")
public class S3GlacierMockController {

    public static final String RESTORE_ENDPOINT = "/file/{namePattern}/restore";

    public static final String TO_T3_ENDPOINT = "/file/{namePattern}/toT3";

    public static final String SET_EXPIRED_ENDPOINT = "/file/{namePattern}/setExpired";

    private final S3MockService s3MockService;

    public S3GlacierMockController(S3MockService s3MockService) {
        this.s3MockService = s3MockService;
    }

    @GetMapping(path = RESTORE_ENDPOINT)
    public ResponseEntity<Void> restore(@PathVariable String namePattern) {
        s3MockService.restoreWithFileName(namePattern);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping(path = TO_T3_ENDPOINT)
    public ResponseEntity<Void> toT3(@PathVariable String namePattern) {
        s3MockService.T2toT3forFileWithName(namePattern);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping(path = SET_EXPIRED_ENDPOINT)
    public ResponseEntity<Void> setExpired(@PathVariable String namePattern) {
        s3MockService.setExpiredDateToFileWithName(namePattern);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}
