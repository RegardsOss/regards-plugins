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
package fr.cnes.regards.modules.test.plugins.batchretry.rest;

import fr.cnes.regards.framework.security.annotation.ResourceAccess;
import fr.cnes.regards.framework.security.role.DefaultRole;
import fr.cnes.regards.modules.test.plugins.batchretry.service.BatchRetryTestServiceHandler;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Batch Retry Test REST controller.
 * This REST api permits to simulate various errors in order to check the AMQP batch retry system
 *
 * @author Olivier Rousselot
 */
@RestController
@RequestMapping("batch-retry-test")
public class BatchRetryTestController {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchRetryTestController.class);

    private final BatchRetryTestServiceHandler batchRetryTestServiceHandler;

    @PostConstruct
    public void init() {
        LOGGER.info("RestController {} UP for tests purpose from batchRetryTestPlugin.", this.getClass().getName());
    }

    public BatchRetryTestController(BatchRetryTestServiceHandler batchRetryTestServiceHandler) {
        this.batchRetryTestServiceHandler = batchRetryTestServiceHandler;
    }

    /**
     * Simulate a number of unexpected errors while receiving a batch of AMQP messages
     *
     * @param errorCount number of consecutive errors thrown
     */
    @GetMapping("simulate/unexpected/errors/{errorCount}")
    @ResourceAccess(description = "Simulate some unexpected errors when handle AMQP messages. Endpoint used for test "
                                  + "purpose only", role = DefaultRole.PUBLIC)
    public ResponseEntity<Void> simulateUnexpectedErrors(@PathVariable("errorCount") int errorCount) {
        batchRetryTestServiceHandler.setUnexpectedErrorsThrownCount(errorCount);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reset simulation.
     * No more unexpected error are thrown, no more messages generates a validation error
     */
    @GetMapping("simulate/reset")
    @ResourceAccess(description = "Reset counter of simulated errors to 0. Endpoint used for test purpose only",
                    role = DefaultRole.PUBLIC)
    public ResponseEntity<Void> simulateReset() {
        batchRetryTestServiceHandler.clear();
        return ResponseEntity.noContent().build();
    }
}
