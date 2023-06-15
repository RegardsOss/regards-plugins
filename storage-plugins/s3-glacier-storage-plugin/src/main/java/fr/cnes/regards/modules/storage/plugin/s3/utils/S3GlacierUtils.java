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
import fr.cnes.regards.framework.s3.domain.StorageCommandID;
import fr.cnes.regards.framework.s3.domain.StorageConfig;
import fr.cnes.regards.framework.utils.file.DownloadUtils;
import fr.cnes.regards.modules.storage.plugin.s3.S3Glacier;
import fr.cnes.regards.modules.storage.plugin.s3.task.RetrieveCacheFileTask;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

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
                restorationComplete = DownloadUtils.existsS3(key, s3Configuration);
                if (restorationComplete) {
                    String taskId = "S3GlacierRestore_" + downloadedFileName + "_" + iterationNumber;
                    InputStream sourceStream = DownloadUtils.getInputStreamFromS3Source(key,
                                                                                        s3Configuration,
                                                                                        new StorageCommandID(taskId,
                                                                                                             UUID.randomUUID()));
                    FileUtils.copyInputStreamToFile(sourceStream, targetFilePath.toFile());
                } else {
                    if (totalWaited > s3AccessTimeout * 1000) {
                        delayExpired = true;
                    } else {
                        WaitingLock lock = new WaitingLock(lockName,
                                                           lockCreationDate,
                                                           lockTimeToLive,
                                                           renewCallDuration,
                                                           lockService);
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
}
