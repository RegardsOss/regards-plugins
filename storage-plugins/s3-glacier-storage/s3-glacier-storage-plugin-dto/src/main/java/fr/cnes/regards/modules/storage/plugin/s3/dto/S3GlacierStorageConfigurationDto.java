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
package fr.cnes.regards.modules.storage.plugin.s3.dto;

import fr.cnes.regards.framework.s3.dto.StorageConfigDto;
import fr.cnes.regards.modules.fileaccess.dto.output.worker.FileNamingStrategy;
import fr.cnes.regards.modules.storage.s3.common.dto.AbstractS3ConfigurationDto;

import java.beans.ConstructorProperties;

/**
 * Configuration DTO for S3 Glacier Storage Plugin
 *
 * @author Thibaud Michaudel
 **/
public class S3GlacierStorageConfigurationDto extends AbstractS3ConfigurationDto {

    /**
     * Local workspace for archive building and restoring cache
     */
    private String rawWorkspacePath;

    /**
     * Threshold under which files are categorized as small files, in bytes
     */
    private int smallFileMaxSize;

    /**
     * Threshold beyond which a small files archive is considered as full and closed, in bytes.
     */
    private int archiveMaxSize;

    /**
     * Determines when the current small files archive is considered too old and should be closed, in hours
     */
    private int archiveMaxAge;

    /**
     * Number of tasks that can be handled at the same time in each job. The different tasks (Store, Restore, Delete) use the same thread pool
     */
    private int parallelTaskNumber;

    /**
     * Duration in hours for which the restored small file archives and their
     * content will be kept in the archive cache in order to limit multiple
     * restoration of the same archive
     */
    private int archiveCacheLifetime;

    /**
     * Timeout in seconds during S3 access after which the job will return an error
     */
    private int s3AccessTimeout;

    /**
     * The name of the standard storage class if different from STANDARD
     */
    private String standardStorageClassName;

    @ConstructorProperties({ "storageConfig",
                             "multipartThresholdMb",
                             "nbParallelPartsUpload",
                             "fileNamingStrategy",
                             "rawWorkspacePath",
                             "smallFileMaxSize",
                             "archiveMaxSize",
                             "archiveMaxAge",
                             "parallelTaskNumber",
                             "archiveCacheLifetime",
                             "s3AccessTimeout",
                             "standardStorageClassName", })
    public S3GlacierStorageConfigurationDto(StorageConfigDto storageConfig,
                                            int multipartThresholdMb,
                                            int nbParallelPartsUpload,
                                            FileNamingStrategy fileNamingStrategy,
                                            String rawWorkspacePath,
                                            int smallFileMaxSize,
                                            int archiveMaxSize,
                                            int archiveMaxAge,
                                            int parallelTaskNumber,
                                            int archiveCacheLifetime,
                                            int s3AccessTimeout,
                                            String standardStorageClassName) {
        super(storageConfig, multipartThresholdMb, nbParallelPartsUpload, fileNamingStrategy);
        this.rawWorkspacePath = rawWorkspacePath;
        this.smallFileMaxSize = smallFileMaxSize;
        this.archiveMaxSize = archiveMaxSize;
        this.archiveMaxAge = archiveMaxAge;
        this.parallelTaskNumber = parallelTaskNumber;
        this.archiveCacheLifetime = archiveCacheLifetime;
        this.s3AccessTimeout = s3AccessTimeout;
        this.standardStorageClassName = standardStorageClassName;
    }

    public String getRawWorkspacePath() {
        return rawWorkspacePath;
    }

    public int getSmallFileMaxSize() {
        return smallFileMaxSize;
    }

    public int getArchiveMaxSize() {
        return archiveMaxSize;
    }

    public int getArchiveMaxAge() {
        return archiveMaxAge;
    }

    public int getParallelTaskNumber() {
        return parallelTaskNumber;
    }

    public int getArchiveCacheLifetime() {
        return archiveCacheLifetime;
    }

    public int getS3AccessTimeout() {
        return s3AccessTimeout;
    }

    public String getStandardStorageClassName() {
        return standardStorageClassName;
    }

}
