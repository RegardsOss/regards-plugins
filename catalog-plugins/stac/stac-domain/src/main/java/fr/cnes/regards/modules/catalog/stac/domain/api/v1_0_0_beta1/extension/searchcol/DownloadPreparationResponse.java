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
package fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol;

import io.vavr.collection.List;
import lombok.Value;
import lombok.With;

import java.net.URI;

/**
 * @author Marc SORDI
 */
@Value
@With
public class DownloadPreparationResponse {

    /**
     * Total size in bytes
     */
    Long totalSize;

    /**
     * Total number of items
     */
    Long totalItems;

    /**
     * Total number of files
     */
    Long totalFiles;

    /**
     * All at once download
     */
    URI downloadAll;

    List<DownloadCollectionPreparationResponse> collections;

    @Value
    @With
    public static class DownloadCollectionPreparationResponse {

        String collectionId;

        /**
         * Size in bytes
         */
        Long size;

        /**
         * Number of items
         */
        Long items;

        /**
         * Number of files
         */
        Long files;

        URI download;

        /**
         * Only set if at least one error occurs during preparation of this collection
         */
        List<String> errors;
    }

}
