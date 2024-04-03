/*
 * Copyright 2017-2023 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.storage.s3.common.dto;

import fr.cnes.regards.framework.s3.dto.StorageConfigDto;
import fr.cnes.regards.modules.fileaccess.dto.IStoragePluginConfigurationDto;

/**
 * Common Configuration DTO for S3 Storage Plugins
 *
 * @author Thibaud Michaudel
 **/
public class AbstractS3ConfigurationDto implements IStoragePluginConfigurationDto {

    protected final StorageConfigDto storageConfigDto;

    /**
     * Number of Mb for a file size over which multipart upload is used
     */
    protected final int multipartThresholdMb;

    /**
     * Number of parallel parts to upload
     */
    protected final int nbParallelPartsUpload;

    public AbstractS3ConfigurationDto(StorageConfigDto storageConfigDto,
                                      int multipartThresholdMb,
                                      int nbParallelPartsUpload) {
        this.multipartThresholdMb = multipartThresholdMb;
        this.nbParallelPartsUpload = nbParallelPartsUpload;
        this.storageConfigDto = storageConfigDto;
    }

    public int getMultipartThresholdMb() {
        return multipartThresholdMb;
    }

    public int getNbParallelPartsUpload() {
        return nbParallelPartsUpload;
    }

    public StorageConfigDto getStorageConfigDto() {
        return storageConfigDto;
    }
}
