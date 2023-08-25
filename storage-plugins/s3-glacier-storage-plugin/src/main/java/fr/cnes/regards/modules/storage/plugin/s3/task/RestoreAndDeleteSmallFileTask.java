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

import fr.cnes.regards.framework.jpa.multitenant.lock.LockServiceTask;
import fr.cnes.regards.framework.utils.file.ZipUtils;
import fr.cnes.regards.modules.storage.domain.database.request.FileDeletionRequest;
import fr.cnes.regards.modules.storage.domain.plugin.IDeletionProgressManager;
import fr.cnes.regards.modules.storage.plugin.s3.S3Glacier;
import fr.cnes.regards.modules.storage.plugin.s3.configuration.DeleteLocalSmallFileTaskConfiguration;
import fr.cnes.regards.modules.storage.plugin.s3.configuration.RestoreAndDeleteSmallFileTaskConfiguration;
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
 * Restore a small file contained in an archive then delete it :
 * <ul>
 * <li>First check that the file is not already present in the building workspace, this happens when the small file
 * is not the first one from the archive to be deleted since the last periodic actions execution
 * </li>
 * <li>If needed restore the archive if its not already present in the archive cache</li>
 * <li>If needed extract the archive in the building workspace</li>
 * <li>Launch a {@link DeleteLocalSmallFileTask} that will handle deletion of the file</li>
 * </ul>
 *
 * @author Thibaud Michaudel
 **/
public class RestoreAndDeleteSmallFileTask implements LockServiceTask<Void> {

    private static final Logger LOGGER = getLogger(RestoreAndDeleteSmallFileTask.class);

    private final RestoreAndDeleteSmallFileTaskConfiguration configuration;

    private final FileDeletionRequest request;

    private final IDeletionProgressManager progressManager;

    public RestoreAndDeleteSmallFileTask(RestoreAndDeleteSmallFileTaskConfiguration restoreAndDeleteSmallFileTaskConfiguration,
                                         FileDeletionRequest request,
                                         IDeletionProgressManager progressManager) {
        this.configuration = restoreAndDeleteSmallFileTaskConfiguration;
        this.request = request;
        this.progressManager = progressManager;
    }

    @Override
    public Void run() {
        LOGGER.info("Starting RestoreAndDeleteSmallFileTask on {}", configuration.fileRelativePath());
        long start = System.currentTimeMillis();
        //Full relative path with archive delimiter : /subdir/archive.zip?filename
        String fileRelativePathAsString = configuration.fileRelativePath().toString();

        //Archive relative path : /subdir/archive.zip
        S3GlacierUtils.S3GlacierUrl fileInfos = S3GlacierUtils.dispatchS3FilePath(fileRelativePathAsString);
        String archiveRelativePathAsString = fileInfos.archiveFilePath();
        Optional<String> smallFileName = fileInfos.smallFileNameInArchive();

        if (smallFileName.isPresent()) {

            Path archiveRelativePath = Path.of(archiveRelativePathAsString);
            String archiveName = archiveRelativePath.getFileName().toString();
            String directoryName = S3GlacierUtils.createBuildDirectoryFromArchiveName(archiveName);
            //Relative directory path : /subdir/rs_zip_archive
            Path relativeDirectoryPath = archiveRelativePath.getParent().resolve(directoryName);

            // Archive path in cache : storages.../glacier/workspace/tmp/subdir/archive.zip
            Path archivePathInCache = Path.of(configuration.cachePath()).resolve(archiveRelativePath);

            // Dir path in building workspace : storages.../glacier/workspace/zip/subdir/rs_zip_archive
            Path dirInWorkspacePath = Path.of(configuration.archiveBuildingWorkspacePath())
                                          .resolve(relativeDirectoryPath);

            // Dir path in cache workspace : storages.../glacier/workspace/tmp/subdir/rs_zip_archive
            Path dirInCachePath = Path.of(configuration.cachePath()).resolve(relativeDirectoryPath);

            if (!Files.exists(dirInWorkspacePath)) {
                if (!Files.exists(archivePathInCache)) {

                    // Restore
                    RestoreResponse restoreResponse = S3GlacierUtils.restore(configuration.s3Client(),
                                                                             configuration.storageConfiguration(),
                                                                             archiveRelativePathAsString);
                    if (restoreResponse.status().equals(RestoreStatus.KEY_NOT_FOUND)) {
                        LOGGER.warn("The file to delete {} was not found on the server, the deletion will be "
                                    + "considered successful", request.getFileReference().getLocation().getUrl());
                        progressManager.deletionSucceed(request);
                        return null;
                    }

                    if (restoreResponse.status().equals(RestoreStatus.CLIENT_EXCEPTION)) {
                        LOGGER.error("Unable to reach S3 server", restoreResponse.exception());
                        progressManager.deletionFailed(request, "Unable to reach S3 server");
                        return null;
                    }
                    // Launch check restoration process
                    boolean restorationComplete = checkRestorationComplete(archiveRelativePathAsString,
                                                                           archivePathInCache);
                    if (!restorationComplete) {
                        progressManager.deletionFailed(request,
                                                       String.format("Unable to restore the archive %s containing the "
                                                                     + "file to delete", archiveRelativePathAsString));
                        return null;
                    }
                }
                // Unzip the restored archive in the plugin workspace cache directory
                // The extracted directory will be used to create the updated archive
                // The presence of the files here allow the directory to be used to both restore the files and create
                // the updated archive.
                boolean success = ZipUtils.unzip(archivePathInCache, dirInCachePath);
                if (!success) {
                    progressManager.deletionFailed(request, "Error while extracting small file archive");
                    return null;
                }
                try {
                    // Create a symbolic link
                    Files.createDirectories(dirInWorkspacePath.getParent());
                    Files.createSymbolicLink(dirInWorkspacePath, dirInCachePath);
                } catch (IOException e) {
                    LOGGER.error(e.getMessage(), e);
                    progressManager.deletionFailed(request, "Error while creating new small file archive");
                    return null;
                }
            }
            DeleteLocalSmallFileTaskConfiguration subTaskConfiguration = createDeleteLocalSmallFileTaskConfiguration();
            DeleteLocalSmallFileTask task = new DeleteLocalSmallFileTask(subTaskConfiguration,
                                                                         request,
                                                                         progressManager);
            try {
                LOGGER.debug("In thread {}, running DeleteLocalSmallFileTask in RestoreAndDeleteSmallFileTask with lock",
                             Thread.currentThread().getName());
                configuration.lockService()
                             .runWithLock(S3GlacierUtils.getLockName(configuration.rootPath(),
                                                                     configuration.fileRelativePath().getParent()
                                                                     != null ?
                                                                         configuration.fileRelativePath()
                                                                                      .getParent()
                                                                                      .toString() :
                                                                         "",
                                                                     S3Glacier.LOCK_STORE_SUFFIX), task);
            } catch (InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
                progressManager.deletionFailed(request, "The deletion task was interrupted before completion.");
                Thread.currentThread().interrupt();
            }
        } else {
            progressManager.deletionFailed(request,
                                           String.format("Error while trying to delete small file %s. Url "
                                                         + "does not match a smallFile url with %s parameter",
                                                         S3Glacier.SMALL_FILE_PARAMETER_NAME,
                                                         fileRelativePathAsString));
        }

        LOGGER.info("End of RestoreAndDeleteSmallFileTask on {} after {} ms",
                    configuration.fileRelativePath(),
                    System.currentTimeMillis() - start);
        return null;
    }

    private DeleteLocalSmallFileTaskConfiguration createDeleteLocalSmallFileTaskConfiguration() {
        return new DeleteLocalSmallFileTaskConfiguration(configuration.fileRelativePath(),
                                                         configuration.archiveBuildingWorkspacePath(),
                                                         configuration.storageName(),
                                                         configuration.storageConfiguration(),
                                                         configuration.s3Client(),
                                                         configuration.glacierArchiveService());
    }

    private boolean checkRestorationComplete(String archiveRelativePathAsString, Path archivePathInCache) {
        return S3GlacierUtils.checkRestorationComplete(archivePathInCache,
                                                       archiveRelativePathAsString,
                                                       configuration.storageConfiguration(),
                                                       configuration.s3AccessTimeout(),
                                                       configuration.lockName(),
                                                       configuration.lockCreationDate(),
                                                       configuration.renewDuration(),
                                                       configuration.standardStorageClassName(),
                                                       configuration.lockService(),
                                                       configuration.s3Client());
    }
}
