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
package fr.cnes.regards.modules.catalog.stac.domain.api.extension.searchcol;

import io.vavr.collection.List;

import java.net.URI;

/**
 * Aggregates information about collections
 *
 * @param downloadAllScript download all script URI
 * @param collections       list of collections
 * @author Marc SORDI
 */
public record CollectionInformation(URI downloadAllScript,
                                    List<SingleCollectionInformation> collections) {

    /**
     * Single collection information
     *
     * @param collectionId  collection id
     * @param correlationId correlation id
     * @param size          size in bytes
     * @param items         number of items
     * @param files         number of files
     * @param sample        sample information
     * @author Marc SORDI
     */
    public record SingleCollectionInformation(String collectionId,
                                              String correlationId,
                                              Long size,
                                              Long items,
                                              Long files,
                                              SampleInformation sample) {

    }

    /**
     * Sample information
     *
     * @param size     size of the sample
     * @param files    files in the sample
     * @param download download URI for the sample
     * @author Marc SORDI
     */
    public record SampleInformation(Long size,
                                    Long files,
                                    URI download) {

    }
}
