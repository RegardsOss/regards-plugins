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
package fr.cnes.regards.modules.catalog.stac.domain.api.extension.searchcol;

import io.vavr.collection.List;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import lombok.experimental.NonFinal;

/**
 * Set filters to search collections.
 *
 * @author Marc SORDI
 */
@Value
@NonFinal
@With
@Builder
public class FiltersByCollection {

    private List<CollectionFilters> collections;

    private Boolean appendAuthParameters;

    @Value
    @With
    @Builder
    public static class CollectionFilters {

        private String collectionId;

        /* This identifier is used as a discriminant when two collections have the same collectionID */
        private String correlationId;

        private CollectionSearchBody.CollectionItemSearchBody filters;
    }
}
