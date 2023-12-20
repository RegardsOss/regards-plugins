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
package fr.cnes.regards.modules.storage.plugin.s3.task;

import fr.cnes.regards.framework.s3.domain.GlacierFileStatus;
import fr.cnes.regards.framework.s3.domain.RestorationStatus;
import fr.cnes.regards.framework.utils.file.ZipUtils;
import fr.cnes.regards.modules.storage.domain.database.request.FileCacheRequest;
import fr.cnes.regards.modules.storage.domain.plugin.IRestorationProgressManager;
import fr.cnes.regards.modules.storage.plugin.s3.S3Glacier;
import fr.cnes.regards.modules.storage.plugin.s3.configuration.RetrieveCacheFileTaskConfiguration;
import fr.cnes.regards.modules.storage.plugin.s3.utils.RestoreResponse;
import fr.cnes.regards.modules.storage.plugin.s3.utils.RestoreStatus;
import fr.cnes.regards.modules.storage.plugin.s3.utils.S3GlacierUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Task to retrieve a file (no matter the size) from the server for the S3 Glacier.
 * Before asking the server, we check if the file or the containing archive is already present il the local cache.
 * <ul>
 * <li>If the file is present in the cache, copy it and send a success to the progress manager</li>
 * <li>If the containing archive is present in the cache, extract the file, then copy it and send a success to the
 * progress manager</li>
 * </ul>
 * Otherwise, send a restoration request to the server and wait for the file to be available to download
 * <ul>
 * <li>If the file is available, download it to the cache directory, copy it and send a success to the progress
 * manager</li>
 * <li>If the file isn't available after the given timeout, send a failure to the progress manager</li>
 * </ul>
 *
 * @author Thibaud Michaudel
 **/
public class RetrieveCacheFileTask extends AbstractRetrieveFileTask {

    private static final Logger LOGGER = getLogger(RetrieveCacheFileTask.class);

    public static final int INITIAL_DELAY = 1000;

    private final RetrieveCacheFileTaskConfiguration configuration;

    public RetrieveCacheFileTask(RetrieveCacheFileTaskConfiguration configuration,
                                 FileCacheRequest request,
                                 IRestorationProgressManager progressManager) {
        super(request, progressManager);
        this.configuration = configuration;
    }

    @Override
    public Void run() {
        LOGGER.info("Starting RetrieveCacheFileTask on {}", configuration.fileRelativePath());
        long start = System.currentTimeMillis();
        if (configuration.isSmallFile()) {
            retrieveSmallFile();
        } else {
            retrieveBigFile();
        }
        LOGGER.info("End of RetrieveCacheFileTask on {} after {} ms",
                    configuration.fileRelativePath(),
                    System.currentTimeMillis() - start);
        return null;
    }

    private void retrieveSmallFile() {

        S3GlacierUtils.S3GlacierUrl fileInfos = S3GlacierUtils.dispatchS3FilePath(configuration.fileRelativePath()
                                                                                               .toString());

        String relativeArchivePath = fileInfos.archiveFilePath();
        Path archiveCachePath = Path.of(configuration.cachePath(), relativeArchivePath);
        String archiveName = archiveCachePath.getFileName().toString();
        Optional<String> smallFileName = fileInfos.smallFileNameInArchive();

        if (smallFileName.isPresent()) {
            String dirName = S3GlacierUtils.createBuildDirectoryFromArchiveName(archiveName);
            Path localPath = archiveCachePath.getParent().resolve(dirName).resolve(smallFileName.get());
            if (Files.exists(localPath)) {
                copyFileAndHandleSuccess(localPath);
                return;
            }
            // Also check if the archive already exists
            Path archivePath = archiveCachePath.getParent().resolve(archiveName);
            if (Files.exists(archivePath)) {
                extractThenCopyFileAndHandleSuccess(localPath, archivePath);
                return;
            }

            // Restore
            LOGGER.info("Restoring {}", relativeArchivePath);
            RestoreResponse restoreResponse = S3GlacierUtils.restore(configuration.s3Client(),
                                                                     configuration.s3Configuration(),
                                                                     relativeArchivePath,
                                                                     configuration.standardStorageClassName());

            if (restoreResponse.status().equals(RestoreStatus.KEY_NOT_FOUND)) {
                progressManager.restoreFailed(request,
                                              String.format("The specified key %s does not exists on the server.",
                                                            relativeArchivePath));
                return;
            }

            if (restoreResponse.status().equals(RestoreStatus.CLIENT_EXCEPTION)) {
                LOGGER.error("Unable to reach S3 server", restoreResponse.exception());
                progressManager.restoreFailed(request, "Unable to reach S3 server");
                return;
            }

            if (!restoreResponse.status().equals(RestoreStatus.FILE_AVAILABLE)) {
                // Launch check restoration process
                GlacierFileStatus fileStatus = S3GlacierUtils.checkRestorationComplete(archiveCachePath,
                                                                                       relativeArchivePath,
                                                                                       configuration.s3Configuration(),
                                                                                       configuration.s3AccessTimeoutInSeconds(),
                                                                                       configuration.lockName(),
                                                                                       configuration.lockCreationDate(),
                                                                                       configuration.renewMaxIterationWaitingPeriodInS(),
                                                                                       configuration.renewDurationInMs(),
                                                                                       configuration.standardStorageClassName(),
                                                                                       configuration.lockService(),
                                                                                       configuration.s3Client());

                if (RestorationStatus.AVAILABLE == fileStatus.getStatus()) {
                    extractThenCopyFileAndHandleSuccess(localPath, archivePath);
                } else {
                    progressManager.restoreFailed(request, "Error while trying to restore file, timeout exceeded");
                }
            } else {
                // File available, just download file to local directory
                if (S3GlacierUtils.downloadFile(archiveCachePath,
                                                relativeArchivePath,
                                                configuration.s3Configuration(),
                                                null)) {
                    extractThenCopyFileAndHandleSuccess(localPath, archivePath);
                } else {
                    progressManager.restoreFailed(request,
                                                  "Error while trying to restore file, download error to local "
                                                  + "cache directory");
                }
            }

        } else {
            progressManager.restoreFailed(request,
                                          String.format("Error while trying to restore file %s. Url "
                                                        + "does not match a smallFile url with %s parameter",
                                                        S3Glacier.SMALL_FILE_PARAMETER_NAME,
                                                        configuration.fileRelativePath()));
        }
    }

    private void retrieveBigFile() {

        // Check if the file is already present before attempting to restore it
        Path targetPath = Path.of(request.getRestorationDirectory(),
                                  configuration.fileRelativePath().getFileName().toString());
        if (Files.exists(targetPath)) {
            progressManager.restoreSucceededInternalCache(request, targetPath);
            return;
        }

        // Restore
        RestoreResponse restoreResponse = S3GlacierUtils.restore(configuration.s3Client(),
                                                                 configuration.s3Configuration(),
                                                                 configuration.fileRelativePath().toString(),
                                                                 configuration.standardStorageClassName());
        if (restoreResponse.status().equals(RestoreStatus.KEY_NOT_FOUND)) {
            progressManager.restoreFailed(request,
                                          String.format("The specified key %s does not exists on the server.",
                                                        configuration.fileRelativePath()));
            return;
        }

        if (restoreResponse.status().equals(RestoreStatus.CLIENT_EXCEPTION)) {
            LOGGER.error("Unable to reach S3 server", restoreResponse.exception());
            progressManager.restoreFailed(request, "Unable to reach S3 server");
            return;
        }

        if (!restoreResponse.status().equals(RestoreStatus.FILE_AVAILABLE)) {
            // Launch check restoration process
            GlacierFileStatus fileStatus = S3GlacierUtils.checkRestorationComplete(targetPath,
                                                                                   configuration.fileRelativePath()
                                                                                                .toString(),
                                                                                   configuration.s3Configuration(),
                                                                                   configuration.s3AccessTimeoutInSeconds(),
                                                                                   configuration.lockName(),
                                                                                   configuration.lockCreationDate(),
                                                                                   configuration.renewMaxIterationWaitingPeriodInS(),
                                                                                   configuration.renewDurationInMs(),
                                                                                   configuration.standardStorageClassName(),
                                                                                   configuration.lockService(),
                                                                                   configuration.s3Client());
            if (RestorationStatus.AVAILABLE == fileStatus.getStatus()) {
                progressManager.restoreSucceededInternalCache(request, targetPath);
            } else {
                progressManager.restoreFailed(request, "Error while trying to restore file, timeout exceeded");
            }
        } else {
            // File available, just download file to local directory
            if (S3GlacierUtils.downloadFile(targetPath,
                                            configuration.fileRelativePath().toString(),
                                            configuration.s3Configuration(),
                                            null)) {
                progressManager.restoreSucceededInternalCache(request, targetPath);
            } else {
                progressManager.restoreFailed(request,
                                              "Error while trying to restore file, download error to local "
                                              + "cache directory");
            }
        }
    }

    private void extractThenCopyFileAndHandleSuccess(Path localPath, Path archivePath) {
        try {
            ZipUtils.extractFile(archivePath, localPath.getFileName().toString(), localPath.getParent());
            if (Files.exists(localPath)) {
                copyFileAndHandleSuccess(localPath);
            } else {
                progressManager.restoreFailed(request,
                                              String.format("The requested file %s is not present in the archive %s",
                                                            localPath.getFileName().toString(),
                                                            archivePath.getFileName().toString()));
            }
        } catch (IOException e) {
            LOGGER.error("Error while extracting file {} from archive {}", localPath, archivePath, e);
            progressManager.restoreFailed(request,
                                          String.format(
                                              "Error when trying to extract the requested file %s from the archive %s",
                                              localPath.getFileName().toString(),
                                              archivePath.getFileName().toString()));

        }
    }
}
