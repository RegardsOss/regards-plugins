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

package fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils;

import fr.cnes.regards.modules.catalog.stac.domain.error.StacException;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.OffsetDateTime;
import java.util.UUID;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.error;

/**
 * Formatting errors for the REST client.
 */
@ControllerAdvice
public class ErrorControllerAdvice {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorControllerAdvice.class);

    @Value
    static class ErrorStructure {

        UUID correlationId;

        String type;

        String message;

        String cause;

        OffsetDateTime time;
    }

    @ExceptionHandler(StacException.class)
    public ResponseEntity<ErrorStructure> formatStacError(StacException e) {
        UUID cid = StacRequestCorrelationId.currentCId();
        error(LOGGER, "STAC Request {}: {}", cid, e.getMessage(), e);
        return new ResponseEntity<>(new ErrorStructure(cid,
                                                       e.getType().name(),
                                                       e.getMessage(),
                                                       e.getCause() == null ? null : e.getCause().getMessage(),
                                                       OffsetDateTime.now()), headers(), e.getType().getStatus());
    }

    public HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

}