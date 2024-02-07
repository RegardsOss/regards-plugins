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
package fr.cnes.regards.modules.storage.plugin.s3.utils;

import fr.cnes.regards.framework.jpa.multitenant.lock.LockService;
import fr.cnes.regards.framework.s3.client.S3HighLevelReactiveClient;
import fr.cnes.regards.framework.s3.domain.GlacierFileStatus;
import fr.cnes.regards.framework.s3.domain.RestorationStatus;
import fr.cnes.regards.framework.s3.domain.StorageCommandID;
import fr.cnes.regards.framework.s3.domain.StorageConfig;
import fr.cnes.regards.framework.s3.exception.S3ClientException;
import fr.cnes.regards.framework.utils.file.DownloadUtils;
import fr.cnes.regards.modules.storage.plugin.s3.S3Glacier;
import fr.cnes.regards.modules.storage.plugin.s3.task.RetrieveCacheFileTask;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.model.InvalidObjectStateException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Utils file for common methods used in different tasks
 *
 * @author Thibaud Michaudel
 **/
public class S3GlacierUtils {

    private static final Logger LOGGER = getLogger(S3GlacierUtils.class);

    private static final int S3_MAX_ATTEMPT = 5;

    private S3GlacierUtils() {

    }

    /**
     * Call the restore process for the given key and handle errors if the file is not in the status :
     * <ul>
     *     <li>{@link RestorationStatus.AVAILABLE}</li>
     *     <li>{@link RestorationStatus.RESTORE_PENDING}</li>
     * </ul>
     *
     * @param s3Client          the client used to process the restore
     * @param config            configuration of the s3 storage
     * @param key               s3 key of the file to restore
     * @param availabilityHours lifetime of file in hours
     */
    public static RestoreResponse restore(S3HighLevelReactiveClient s3Client,
                                          StorageConfig config,
                                          String key,
                                          String standardStorageClassName,
                                          @Nullable Integer availabilityHours) {
        GlacierFileStatus glacierFileStatus = s3Client.isFileAvailable(config, key, standardStorageClassName).block();

        if (RestorationStatus.AVAILABLE == glacierFileStatus.getStatus()) {
            return new RestoreResponse(RestoreStatus.FILE_AVAILABLE, glacierFileStatus);
        }
        if (RestorationStatus.RESTORE_PENDING == glacierFileStatus.getStatus()) {
            return new RestoreResponse(RestoreStatus.SUCCESS, glacierFileStatus);
        }
        RestoreResponse response = s3Client.restore(config, key, computeDaysFromHours(availabilityHours))
                                           .map(result -> new RestoreResponse(RestoreStatus.SUCCESS, glacierFileStatus))
                                           .onErrorResume(InvalidObjectStateException.class,
                                                          error -> Mono.just(new RestoreResponse(RestoreStatus.WRONG_STORAGE_CLASS,
                                                                                                 glacierFileStatus)))
                                           .onErrorResume(NoSuchKeyException.class,
                                                          error -> Mono.just(new RestoreResponse(RestoreStatus.KEY_NOT_FOUND,
                                                                                                 glacierFileStatus)))
                                           .onErrorResume(S3Exception.class,
                                                          error -> error.getMessage()
                                                                        .contains("RestoreAlreadyInProgress") ?
                                                              Mono.just(new RestoreResponse(RestoreStatus.RESTORE_ALREADY_IN_PROGRESS,
                                                                                            glacierFileStatus)) :
                                                              Mono.just(new RestoreResponse(RestoreStatus.CLIENT_EXCEPTION,
                                                                                            glacierFileStatus,
                                                                                            error)))
                                           .onErrorResume(S3ClientException.class,
                                                          error -> Mono.just(new RestoreResponse(RestoreStatus.CLIENT_EXCEPTION,
                                                                                                 glacierFileStatus,
                                                                                                 error)))
                                           .onErrorResume((error) -> Mono.just(new RestoreResponse(RestoreStatus.CLIENT_EXCEPTION,
                                                                                                   glacierFileStatus,
                                                                                                   new Exception(error))))
                                           .block();
        if (RestoreStatus.WRONG_STORAGE_CLASS == response.status()) {
            LOGGER.warn("The requested file {} is present but its storage class is not "
                        + "the expected one. This most likely means that you are using the glacier plugin (intended for t3 storage) on a t2 storage."
                        + " The restoration process will continue as if.", key);
            return new RestoreResponse(RestoreStatus.FILE_AVAILABLE, glacierFileStatus);
        }
        if (RestoreStatus.RESTORE_ALREADY_IN_PROGRESS == response.status()) {
            LOGGER.info("A restoration process is already in progress for key {}", key);
            return new RestoreResponse(RestoreStatus.SUCCESS, glacierFileStatus);
        }
        return response;
    }

    /**
     * Compute the number of days from a number of hours (rounded up to the superior day) :
     * <ul>
     *     <li>5 hours -> 1 day</li>
     *     <li>25 hours -> 2 days</li>
     * </ul>
     */
    private static Integer computeDaysFromHours(@Nullable Integer hours) {
        Integer days = null;
        if (hours != null && hours > 0) {
            days = (int) Math.ceil((float) hours / (float) 24);
        }
        return days;
    }

    /**
     * Check if the restoration of a file is completed, either successfully or after the timeout is exceeded in
     * external cache.
     *
     * @param key                      s3 key of the file to download
     * @param s3Configuration          configuration of the s3 storage
     * @param s3AccessTimeoutInSeconds time after which the restoration attempt will fail
     * @param lockName                 name of the lock for renewal purpose
     * @param lockCreationDate         creation date of the lock for renewal purpose
     * @param renewCallDurationInMs    worst case scenario duration of the renewal call, necessary to prevent the lock from expiring between renewal call and renewal success
     * @param lockService              lockService handling the lock renewal
     * @return GlacierFileStatus
     */
    public static GlacierFileStatus checkRestorationComplete(String key,
                                                             StorageConfig s3Configuration,
                                                             int s3AccessTimeoutInSeconds,
                                                             String lockName,
                                                             Instant lockCreationDate,
                                                             int renewMaxIterationWaitingPeriodInS,
                                                             Long renewCallDurationInMs,
                                                             @Nullable String standardStorageClassName,
                                                             LockService lockService,
                                                             S3HighLevelReactiveClient s3Client) {
        int lockTimeToLive = lockService.getTimeToLive();

        RestorationStatus restorationStatus = RestorationStatus.NOT_AVAILABLE;

        boolean retryAvailability = true;
        int delay = RetrieveCacheFileTask.INITIAL_DELAY;
        int totalWaited = 0;
        int reachServerAttempt = 0;
        GlacierFileStatus glacierFileStatus = null;

        try {
            while ((restorationStatus != RestorationStatus.AVAILABLE) && retryAvailability) {
                reachServerAttempt++;
                LOGGER.debug("Checking if restoration succeeded");

                try {
                    glacierFileStatus = s3Client.isFileAvailable(s3Configuration, key, standardStorageClassName)
                                                .block();
                    restorationStatus = glacierFileStatus.getStatus();
                    reachServerAttempt = 0;
                } catch (S3ClientException e) {
                    LOGGER.warn("Unable to check if the restoration is complete because the server is unreachable");
                    if (reachServerAttempt >= S3_MAX_ATTEMPT) {
                        throw e;
                    }
                }

                switch (restorationStatus) {
                    case RESTORE_PENDING -> {
                        LOGGER.info("Restoration of file {}/{} not succeeded yet. Waiting for restoration end.",
                                    s3Configuration.getBucket(),
                                    key);
                        if (totalWaited > s3AccessTimeoutInSeconds * 1000) {
                            LOGGER.error("The Restoration was not completed after the set maximum delay of {}s, ending "
                                         + "restoration process", s3AccessTimeoutInSeconds);
                            retryAvailability = false;
                        } else {
                            WaitingLock lock = new WaitingLock(lockName,
                                                               lockCreationDate,
                                                               lockTimeToLive,
                                                               renewCallDurationInMs,
                                                               lockService);
                            LOGGER.debug("Next try in {}", delay);
                            LOGGER.debug("Will wait at most {}ms", s3AccessTimeoutInSeconds * 1000 - totalWaited);
                            lock.waitAndRenew(delay);
                            totalWaited += delay;
                            delay = Math.min(2 * delay, renewMaxIterationWaitingPeriodInS * 1000);
                        }
                    }
                    case AVAILABLE -> {
                        LOGGER.info("Restoration succeeded for file {}/{}", s3Configuration.getBucket(), key);
                        retryAvailability = false;
                    }
                    case EXPIRED -> {
                        LOGGER.error("The restoration of file {}/{} is done but expired. File is no longer available.",
                                     s3Configuration.getBucket(),
                                     key);
                        retryAvailability = false;
                    }
                    case NOT_AVAILABLE -> {
                        LOGGER.error("File {}/{} is not available and no restoration request is pending.",
                                     s3Configuration.getBucket(),
                                     key);
                        retryAvailability = false;
                    }
                }
            }
        } catch (NoSuchKeyException e) {
            LOGGER.error("The requested file {} was not found on the server", key, e);
        } catch (InterruptedException e) {
            LOGGER.error("Sleep interrupted", e);
        }

        return glacierFileStatus == null ?
            new GlacierFileStatus(restorationStatus, null, null) :
            new GlacierFileStatus(restorationStatus,
                                  glacierFileStatus.getFileSize(),
                                  glacierFileStatus.getExpirationDate());
    }

    /**
     * Download a file after the checking if the restoration of a file is completed, either successfully or after the
     * timeout is exceeded in internal cache.
     *
     * @param targetFilePath           path where the file will be downloaded
     * @param key                      s3 key of the file to download
     * @param s3Configuration          configuration of the s3 storage
     * @param s3AccessTimeoutInSeconds time after which the restoration attempt will fail
     * @param lockName                 name of the lock for renewal purpose
     * @param lockCreationDate         creation date of the lock for renewal purpose
     * @param renewCallDurationInMs    worst case scenario duration of the renewal call, necessary to prevent the lock from expiring between renewal call and renewal success
     * @param lockService              lockService handling the lock renewal
     * @return GlacierFileStatus
     */
    public static GlacierFileStatus downloadAfterRestoreFile(Path targetFilePath,
                                                             String key,
                                                             StorageConfig s3Configuration,
                                                             int s3AccessTimeoutInSeconds,
                                                             String lockName,
                                                             Instant lockCreationDate,
                                                             int renewMaxIterationWaitingPeriodInS,
                                                             Long renewCallDurationInMs,
                                                             @Nullable String standardStorageClassName,
                                                             LockService lockService,
                                                             S3HighLevelReactiveClient s3Client) {

        GlacierFileStatus glacierFileStatus = checkRestorationComplete(key,
                                                                       s3Configuration,
                                                                       s3AccessTimeoutInSeconds,
                                                                       lockName,
                                                                       lockCreationDate,
                                                                       renewMaxIterationWaitingPeriodInS,
                                                                       renewCallDurationInMs,
                                                                       standardStorageClassName,
                                                                       lockService,
                                                                       s3Client);

        if (glacierFileStatus.getStatus() == RestorationStatus.AVAILABLE) {
            String downloadedFileName = targetFilePath.getFileName().toString();
            
            try {
                if (!downloadFile(targetFilePath, key, s3Configuration, "S3GlacierRestore_" + downloadedFileName)) {
                    return new GlacierFileStatus(RestorationStatus.NOT_AVAILABLE,
                                                 glacierFileStatus.getFileSize(),
                                                 glacierFileStatus.getExpirationDate());
                }
            } catch (NoSuchKeyException e) {
                LOGGER.error("The requested file {} was not found on the server", downloadedFileName, e);
            }
        }
        return glacierFileStatus;
    }

    /**
     * Download S3 available file to local directory
     *
     * @return true if file is downloaded with success
     */
    public static boolean downloadFile(Path targetFilePath,
                                       String key,
                                       StorageConfig s3Configuration,
                                       @Nullable String taskId) {
        if (StringUtils.isBlank(taskId)) {
            taskId = "S3GlacierRestore_" + targetFilePath.getFileName().toString();
        }
        try {
            InputStream sourceStream = DownloadUtils.getInputStreamFromS3Source(key,
                                                                                s3Configuration,
                                                                                new StorageCommandID(taskId,
                                                                                                     UUID.randomUUID()));
            FileUtils.copyInputStreamToFile(sourceStream, targetFilePath.toFile());
        } catch (IOException e) {
            LOGGER.error(String.format("Error downloading file %s from s3 server to local directory", key), e);
            return false;
        }
        return true;
    }

    /**
     * Remove the suffix {@link fr.cnes.regards.modules.storage.plugin.s3.S3Glacier#CURRENT_ARCHIVE_SUFFIX} from the
     * given String
     */
    public static String removeSuffix(String name) {
        return name.substring(0, name.length() - S3Glacier.CURRENT_ARCHIVE_SUFFIX.length());
    }

    /**
     * Remove the prefix {@link fr.cnes.regards.modules.storage.plugin.s3.S3Glacier#BUILDING_DIRECTORY_PREFIX} from the
     * given String
     */
    public static String removePrefix(String name) {
        if (name.length() > S3Glacier.BUILDING_DIRECTORY_PREFIX.length()) {
            return name.substring(S3Glacier.BUILDING_DIRECTORY_PREFIX.length());
        } else {
            return name;
        }
    }

    /**
     * Creates temporary extraction directory dir from archive file name.
     * If archive file name is <b>date.zip</b> so temp dir is <b>rs_zip_date</b>
     */
    public static String computePathOfBuildDirectoryFromArchiveName(String archiveName) {
        return S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName.substring(0,
                                                                           archiveName.indexOf(S3Glacier.ARCHIVE_EXTENSION));
    }

    /**
     * Creates zip archive file name from its associated temporary extraction directory name.
     * If temp_dir name is <b>rs_zip_date</b> so zip archive name is <b>date.zip</b>
     */
    public static String createArchiveNameFromBuildingDir(String buildingDirectoryName) {
        return S3GlacierUtils.removePrefix(buildingDirectoryName) + S3Glacier.ARCHIVE_EXTENSION;
    }

    /**
     * Remove the prefix {@link fr.cnes.regards.modules.storage.plugin.s3.S3Glacier#BUILDING_DIRECTORY_PREFIX} and
     * the suffix {@link fr.cnes.regards.modules.storage.plugin.s3.S3Glacier#CURRENT_ARCHIVE_SUFFIX} from the
     * given String
     */
    public static String removePrefixAndSuffix(String name) {
        return removePrefix(removeSuffix(name));
    }

    /**
     * Calculates lock name for glacier tasks.
     * Can be :
     * <ul>
     *     <li>Restore big file : LOCK_/root/path/node1/node2/file.txt_RESTORE_LOCK</li>
     *     <li>Restore small file in remote archive : LOCK_/root/path/node1/node2/archive.zip_RESTORE_LOCK</li>
     *     <li>Store big file : None</li>
     *     <li>Store small file : LOCK_/root/path/node1/node2</li>
     * </ul>
     */
    public static String getLockName(LockTypeEnum lockType,
                                     @Nullable String rootPath,
                                     @Nullable String workspacePath,
                                     String node) {
        if (node == null) {
            // avoid null value on subdirectory param
            node = "";
        }
        String pathToLock;
        S3GlacierUrl dispatchedFilePath = dispatchS3FilePath(node);
        Path zipBuildingRootPath = workspacePath != null ? Path.of(workspacePath, S3Glacier.ZIP_DIR) : null;
        Path cacheWorkspace = workspacePath != null ? Paths.get(workspacePath, S3Glacier.TMP_DIR) : null;
        Path nodePath = Paths.get(node);
        boolean isBuildingDirectory = zipBuildingRootPath != null && nodePath.startsWith(zipBuildingRootPath);
        boolean isCacheDirectory = cacheWorkspace != null && nodePath.startsWith(cacheWorkspace);
        if (isBuildingDirectory) {
            // Build path to lock for locking a building archive
            Path relativizedPath = zipBuildingRootPath.relativize(nodePath);
            String fileName = removePrefix(relativizedPath.getFileName().toString());
            pathToLock = relativizedPath.getParent().resolve(fileName).toString();
            // If lock type is RESTORE we need to lock the zip archive name with zip extension.
            // Else for STORE lock we lock only the node (no zip extension)
            if (lockType == LockTypeEnum.LOCK_RESTORE) {
                pathToLock += S3Glacier.ARCHIVE_EXTENSION;
            }
        } else if (isCacheDirectory) {
            // Build path to lock the restored archive
            Path relativizedPath = cacheWorkspace.relativize(nodePath);
            pathToLock = rootPath != null ?
                Path.of(rootPath).relativize(relativizedPath).toString() :
                relativizedPath.toString();
        } else if (dispatchedFilePath.isSmallFileUrl()) {
            // For small files :
            // Lock the archive containing the small file to prevent other retrieve jobs from restoring the
            // same archive (during restore or delete)
            // @see S3Glacier#doRetrieveTask and {@link S3Glacier#doDeleteTask} and {@link S3Glacier#doCleanDirectory}
            pathToLock = dispatchedFilePath.archiveFilePath;
        } else {
            // Not a small file, not in building or in restore directories.
            // Lock on the node
            pathToLock = node;
        }

        if (rootPath != null) {
            return Paths.get(S3Glacier.LOCK_PREFIX, "/", rootPath, pathToLock, lockType.toString()).toString();
        } else {
            return Paths.get(S3Glacier.LOCK_PREFIX, "/", pathToLock, lockType.toString()).toString();
        }
    }

    /**
     * Dispatch an S3 Glacier url into two strings. First one contains stored file path. Second one contains optional
     * smallFile name like url = archive_path?fileName=smallFileName
     */
    public static S3GlacierUrl dispatchS3Url(String url) throws URISyntaxException {
        String urlWithoutParam = new URIBuilder(url).clearParameters().build().toString();
        Optional<String> fileNameParam = new URIBuilder(url).getQueryParams()
                                                            .stream()
                                                            .filter(pair -> pair.getName()
                                                                                .equals(S3Glacier.SMALL_FILE_PARAMETER_NAME))
                                                            .map(NameValuePair::getValue)
                                                            .filter(Objects::nonNull)
                                                            .findFirst();
        return new S3GlacierUrl(urlWithoutParam, fileNameParam);
    }

    /**
     * Dispatch an S3 Glacier path into two strings. First one contains stored file path. Second one contains optional
     * smallFile name like url = archive_path?fileName=smallFileName
     */
    public static S3GlacierUrl dispatchS3FilePath(String filePath) {
        String[] parts = filePath.split(Pattern.quote("?" + S3Glacier.SMALL_FILE_PARAMETER_NAME + "="));
        String archiveName = parts[0];
        String smallFileName = null;
        if (parts.length > 1) {
            smallFileName = parts[1];
        }
        return new S3GlacierUrl(archiveName, Optional.ofNullable(smallFileName));
    }

    /**
     * Creates a S3 Glacier store file path with an archive name and a small file into the archive like
     * archivePath?fileName=smallFileName
     */
    public static String createSmallFilePath(String archivePath, String smallFileName) {
        return String.format("%s?%s=%s", archivePath, S3Glacier.SMALL_FILE_PARAMETER_NAME, smallFileName);
    }

    public record S3GlacierUrl(String archiveFilePath,
                               Optional<String> smallFileNameInArchive) {

        public boolean isSmallFileUrl() {
            return smallFileNameInArchive.isPresent();
        }

    }
}
