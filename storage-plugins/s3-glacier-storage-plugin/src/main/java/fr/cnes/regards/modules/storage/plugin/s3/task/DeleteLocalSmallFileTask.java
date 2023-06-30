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
import fr.cnes.regards.modules.storage.domain.database.request.FileDeletionRequest;
import fr.cnes.regards.modules.storage.domain.plugin.IDeletionProgressManager;
import fr.cnes.regards.modules.storage.plugin.s3.S3Glacier;
import fr.cnes.regards.modules.storage.plugin.s3.configuration.DeleteLocalSmallFileTaskConfiguration;
import fr.cnes.regards.modules.storage.plugin.s3.utils.S3GlacierUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Task to delete a small file that have yet to be sent to the server for S3 Glacier
 * <ul>
 * <li>If it exists, delete the file from the archive building directory and send a success result to the progress
 * manager</li>
 * <li>If it doesnt, send a failed result to the progress manager</li>
 * </ul>
 *
 * @author Thibaud Michaudel
 **/
public class DeleteLocalSmallFileTask implements LockServiceTask<Void> {

    private static final Logger LOGGER = getLogger(DeleteLocalSmallFileTask.class);

    private final DeleteLocalSmallFileTaskConfiguration configuration;

    private final FileDeletionRequest request;

    private final IDeletionProgressManager progressManager;

    public DeleteLocalSmallFileTask(DeleteLocalSmallFileTaskConfiguration deleteLocalSmallFileTaskConfiguration,
                                    FileDeletionRequest request,
                                    IDeletionProgressManager progressManager) {
        this.configuration = deleteLocalSmallFileTaskConfiguration;
        this.request = request;
        this.progressManager = progressManager;
    }

    @Override
    public Void run() {
        LOGGER.info("Starting DeleteLocalSmallFileTask on {}", configuration.fileRelativePath());
        long start = System.currentTimeMillis();
        Path localPathWithArchiveDelimiter = Path.of(configuration.archiveBuildingWorkspacePath())
                                                 .resolve(configuration.fileRelativePath());
        S3GlacierUtils.S3GlacierUrl fileInfos = S3GlacierUtils.dispatchS3FilePath(localPathWithArchiveDelimiter.getFileName()
                                                                                                               .toString());
        String archiveName = fileInfos.archiveFilePath();
        Optional<String> smallFileName = fileInfos.smallFileNameInArchive();

        if (smallFileName.isPresent()) {

            // Name of the directory
            String dirName = S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName.substring(0,
                                                                                         archiveName.indexOf(S3Glacier.ARCHIVE_EXTENSION));
            // Path of the directory in the workspace containing the file to delete if it's not the current directory
            Path localPath = localPathWithArchiveDelimiter.getParent().resolve(dirName).resolve(smallFileName.get());

            // Path of the directory in the workspace containing the file to delete if it's the current directory
            Path localPathCurrent = localPathWithArchiveDelimiter.getParent()
                                                                 .resolve(dirName + S3Glacier.CURRENT_ARCHIVE_SUFFIX)
                                                                 .resolve(smallFileName.get());

            if (Files.exists(localPath)) {
                try {
                    Files.delete(localPath);
                    if (request.getFileReference().getLocation().isPendingActionRemaining()) {
                        // The file was not yet sent to the server
                        progressManager.deletionSucceed(request);
                    } else {
                        progressManager.deletionSucceedWithPendingAction(request);
                    }
                    // Deleting archive info as it doesn't exist anymore
                    configuration.glacierArchiveService()
                                 .deleteGlacierArchive(configuration.storageName(),
                                                       request.getFileReference().getLocation().getUrl());
                } catch (IOException e) {
                    LOGGER.error("Error while trying to delete {}", localPath, e);
                    progressManager.deletionFailed(request,
                                                   String.format("Error while trying to delete %s", localPath));
                }
            } else if (Files.exists(localPathCurrent)) {
                try {
                    Files.delete(localPathCurrent);
                    progressManager.deletionSucceed(request);
                } catch (IOException e) {
                    LOGGER.error("Error while trying to delete {}", localPathCurrent, e);
                    progressManager.deletionFailed(request,
                                                   String.format("Error while trying to delete %s", localPathCurrent));
                }
            } else {
                LOGGER.warn("The file to delete {} should exist locally but wasn't found, the deletion will be "
                            + "considered as successful but the file might still exist in the storage", localPath);
                progressManager.deletionSucceed(request);
            }
        } else {
            progressManager.deletionFailed(request,
                                           String.format("Error while trying to delete small file %s. Url "
                                                         + "does not match a smallFile url with %s parameter",
                                                         S3Glacier.SMALL_FILE_PARAMETER_NAME,
                                                         localPathWithArchiveDelimiter.getFileName().toString()));
        }
        LOGGER.info("End of DeleteLocalSmallFileTask on {} after {} ms",
                    configuration.fileRelativePath(),
                    System.currentTimeMillis() - start);
        return null;
    }
}
