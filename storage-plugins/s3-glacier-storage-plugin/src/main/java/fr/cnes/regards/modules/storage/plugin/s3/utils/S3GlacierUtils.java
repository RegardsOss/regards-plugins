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
import fr.cnes.regards.framework.s3.domain.StorageCommandID;
import fr.cnes.regards.framework.s3.domain.StorageConfig;
import fr.cnes.regards.framework.utils.file.DownloadUtils;
import fr.cnes.regards.modules.storage.plugin.s3.S3Glacier;
import fr.cnes.regards.modules.storage.plugin.s3.task.RetrieveCacheFileTask;
import org.apache.commons.io.FileUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.model.InvalidObjectStateException;
import software.amazon.awssdk.services.s3.model.RestoreObjectResponse;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
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

    private S3GlacierUtils() {

    }

    /**
     * Call the restore process for the given key and handle errors
     *
     * @param s3Client the client used to process the restore
     * @param config   configuration of the s3 storage
     * @param key      s3 key of the file to restore
     */
    public static void restore(S3HighLevelReactiveClient s3Client, StorageConfig config, String key) {
        s3Client.restore(config, key)
                .onErrorResume(InvalidObjectStateException.class, error -> {
                    LOGGER.warn("The requested file {} is present but its storage class is not "
                                + "the expected one. This most likely means that you are using the glacier plugin (intended for t3 storage) on a t2 storage."
                                + " The restoration process will continue as if.", key);
                    return Mono.empty();
                })
                .doOnError(e -> LOGGER.error("Error while attempting to restore key {}", key, e))
                .onErrorReturn(RestoreObjectResponse.builder().build())
                .block();
    }

    /**
     * Check if the restoration of a file is completed, either successfully or after the timeout is exceeded
     *
     * @param targetFilePath    path where the file will be downloaded
     * @param key               s3 key of the file to download
     * @param s3Configuration   configuration of the s3 storage
     * @param s3AccessTimeout   time after which the restoration attempt will fail
     * @param lockName          name of the lock for renewal purpose
     * @param lockCreationDate  creation date of the lock for renewal purpose
     * @param renewCallDuration worst case scenario duration of the renewal call, necessary to prevent the lock from expiring between renewal call and renewal success
     * @param lockService       lockService handling the lock renewal
     * @return true if the restoration succeeded, false otherwise
     */
    public static boolean checkRestorationComplete(Path targetFilePath,
                                                   String key,
                                                   StorageConfig s3Configuration,
                                                   int s3AccessTimeout,
                                                   String lockName,
                                                   Instant lockCreationDate,
                                                   Long renewCallDuration,
                                                   LockService lockService) {
        int lockTimeToLive = lockService.getTimeToLive();

        String downloadedFileName = targetFilePath.getFileName().toString();
        boolean restorationComplete = false;
        boolean delayExpired = false;
        int delay = RetrieveCacheFileTask.INITIAL_DELAY;
        int totalWaited = 0;
        int iterationNumber = 0;

        try {
            while (!restorationComplete && !delayExpired) {
                iterationNumber++;
                LOGGER.debug("Checking if restoration succeeded");
                restorationComplete = DownloadUtils.existsS3(key, s3Configuration);
                LOGGER.debug("Restoration succeeded");
                if (restorationComplete) {
                    String taskId = "S3GlacierRestore_" + downloadedFileName + "_" + iterationNumber;
                    LOGGER.info("Downloading file from S3");
                    InputStream sourceStream = DownloadUtils.getInputStreamFromS3Source(key,
                                                                                        s3Configuration,
                                                                                        new StorageCommandID(taskId,
                                                                                                             UUID.randomUUID()));
                    LOGGER.info("File download ended");
                    FileUtils.copyInputStreamToFile(sourceStream, targetFilePath.toFile());
                } else {
                    LOGGER.debug("Restoration not succeeded yet");
                    if (totalWaited > s3AccessTimeout * 1000) {
                        LOGGER.error("The Restoration was not completed after the set maximum delay of {} s, ending "
                                     + "restoration process", s3AccessTimeout * 1000);
                        delayExpired = true;
                    } else {
                        WaitingLock lock = new WaitingLock(lockName,
                                                           lockCreationDate,
                                                           lockTimeToLive,
                                                           renewCallDuration,
                                                           lockService);
                        LOGGER.debug("Next try in {}", delay);
                        LOGGER.debug("Will wait at most {}", s3AccessTimeout * 1000 - totalWaited);
                        lock.waitAndRenew(delay);
                        totalWaited += delay;
                        delay = 2 * delay;
                    }
                }
            }
        } catch (FileNotFoundException e) {
            LOGGER.error("The requested file {} was not found on the server", downloadedFileName, e);
        } catch (IOException e) {
            LOGGER.error("Error when downloading file {}", downloadedFileName, e);
        } catch (InterruptedException e) {
            LOGGER.error("Sleep interrupted", e);
        }
        return restorationComplete;
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
        return name.substring(S3Glacier.BUILDING_DIRECTORY_PREFIX.length());
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
     * Create the lock name for the given node
     *
     * @param node   the node, if null will be replaced par an empty string
     * @param suffix the lock suffix for its type
     * @return the lock name
     */
    public static String getLockName(String rootPath, String node, String suffix) {
        return S3Glacier.LOCK_PREFIX + Path.of(node != null ? node : "", rootPath != null ? rootPath : "") + suffix;
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
    public static S3GlacierUrl dispatchS3FilePath(String fileName) {
        String[] parts = fileName.split(Pattern.quote("?" + S3Glacier.SMALL_FILE_PARAMETER_NAME + "="));
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