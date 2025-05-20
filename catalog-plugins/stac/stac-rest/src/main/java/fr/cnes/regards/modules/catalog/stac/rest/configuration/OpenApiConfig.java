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
 * along with REGARDS. If not, see `<http://www.gnu.org/licenses/>`.
 */


package fr.cnes.regards.modules.catalog.stac.rest.configuration;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration for grouping STAC endpoints in Swagger
 */
@Configuration
public class OpenApiConfig {

    /**
     * Creates a Swagger group named "stac-api" for documenting
     * endpoints under the "/stac/**" path.
     */
    @Bean
    public GroupedOpenApi stacApiGroup() {
        return GroupedOpenApi.builder().group("stac-api").pathsToMatch("/stac/**").build();
    }
}