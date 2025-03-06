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
 * along with REGARDS. If not, see `<http://www.gnu.org/licenses/>`.
 */
/**
 *
 **/
package fr.cnes.regards.modules.storage.plugin.smallfiles;

import fr.cnes.regards.framework.jpa.multitenant.lock.LockService;
import fr.cnes.regards.framework.s3.domain.GlacierFileStatus;
import fr.cnes.regards.framework.s3.domain.RestorationStatus;
import fr.cnes.regards.modules.storage.plugin.smallfiles.utils.RestoreResponse;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Main interface for storage plugin dealing with small files
 */
public interface ISmallFilesStorage {

    String SMALL_FILES_WORKSPACE_PATH = "Small_File_Workspace_Path";

    String SMALL_FILES_MAX_SIZE = "Small_File_Max_Size";

    String SMALL_FILES_ARCHIVE_MAX_SIZE = "Small_File_Archive_Max_Size";

    String SMALL_FILES_ARCHIVE_DURATION_IN_HOURS = "Small_File_Archive_Duration_In_Hours";

    String SMALL_FILES_PARALLEL_DELETE_AND_RESTORE_TASK_NUMBER = "Small_File_Parallel_Restore_Number";

    String SMALL_FILES_PARALLEL_STORE_TASK_NUMBER = "Small_File_Parallel_Upload_Number";

    String SMALL_FILES_ARCHIVE_CACHE_FILE_LIFETIME_IN_HOURS = "Small_File_Local_Workspace_File_Lifetime_In_Hours";

    String ZIP_DIR = "zip";

    String TMP_DIR = "tmp";

    String ARCHIVE_DATE_FORMAT = "yyyyMMddHHmmssSSS";

    String BUILDING_DIRECTORY_PREFIX = "rs_zip_";

    String CURRENT_ARCHIVE_SUFFIX = "_current";

    String MD5_CHECKSUM = "MD5";

    String ARCHIVE_EXTENSION = ".zip";

    String LOCK_PREFIX = "LOCK_";

    String SMALL_FILE_PARAMETER_NAME = "fileName";

    String USE_EXTERNAL_CACHE_NAME = "useExternalCacheName";

    /**
     * Call the restore process for the given key and handle errors if the file is not in the status :
     * <ul>
     *     <li>{@link RestorationStatus.AVAILABLE}</li>
     *     <li>{@link RestorationStatus.RESTORE_PENDING}</li>
     * </ul>
     *
     * @param key               s3 key of the file to restore
     * @param availabilityHours lifetime of file in hours
     */
    RestoreResponse restore(String key, @Nullable Integer availabilityHours);

    /**
     * Download S3 available file to local directory
     *
     * @return true if file is downloaded with success
     */
    boolean downloadFile(Path targetFilePath, String key, @Nullable String taskId);

    /**
     * Download a file after the checking if the restoration of a file is completed, either successfully or after the
     * timeout is exceeded in internal cache.
     *
     * @param targetFilePath        path where the file will be downloaded
     * @param key                   s3 key of the file to download
     * @param lockName              name of the lock for renewal purpose
     * @param lockCreationDate      creation date of the lock for renewal purpose
     * @param renewCallDurationInMs worst case scenario duration of the renewal call, necessary to prevent the lock from expiring between renewal call and renewal success
     * @param lockService           lockService handling the lock renewal
     * @return SmallFilesStorageFileStatus
     */
    GlacierFileStatus downloadAfterRestoreFile(Path targetFilePath,
                                               String key,
                                               String lockName,
                                               Instant lockCreationDate,
                                               int renewMaxIterationWaitingPeriodInS,
                                               Long renewCallDurationInMs,
                                               LockService lockService);

    /**
     * Check if the restoration of a file is completed, either successfully or after the timeout is exceeded in
     * external cache.
     *
     * @param key                   s3 key of the file to download
     * @param lockName              name of the lock for renewal purpose
     * @param lockCreationDate      creation date of the lock for renewal purpose
     * @param renewCallDurationInMs worst case scenario duration of the renewal call, necessary to prevent the lock from expiring between renewal call and renewal success
     * @param lockService           lockService handling the lock renewal
     * @return SmallFilesStorageFileStatus
     */
    GlacierFileStatus checkRestorationComplete(String key,
                                               String lockName,
                                               Instant lockCreationDate,
                                               int renewMaxIterationWaitingPeriodInS,
                                               Long renewCallDurationInMs,
                                               LockService lockService);

    Path getFileRelativePath(String fileRelativePath) throws MalformedURLException;

    boolean deleteArchive(String taskId, String entryKey);

    URL getStorageUrl(String entryKey);

    String storeFile(Path fileToCreate, String filePathOnStorage, String checksum, Long fileSize) throws IOException;

    boolean existsStorageUrl(Path path);
}