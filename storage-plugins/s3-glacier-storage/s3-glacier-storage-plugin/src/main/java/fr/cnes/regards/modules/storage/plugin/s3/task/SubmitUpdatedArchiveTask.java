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
import fr.cnes.regards.framework.s3.domain.StorageCommand;
import fr.cnes.regards.framework.s3.domain.StorageCommandID;
import fr.cnes.regards.framework.s3.domain.StorageCommandResult;
import fr.cnes.regards.framework.s3.exception.S3ClientException;
import fr.cnes.regards.modules.fileaccess.plugin.domain.IPeriodicActionProgressManager;
import fr.cnes.regards.modules.storage.plugin.s3.S3Glacier;
import fr.cnes.regards.modules.storage.plugin.s3.configuration.SubmitReadyArchiveTaskConfiguration;
import fr.cnes.regards.modules.storage.plugin.s3.utils.S3GlacierUtils;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Submit a small file archive that is either full or old enough to be closed even if not full :
 * <ul>
 *   <li>If its the _current directory and its old enough, close it</li>
 *   <li>Create the zip archive from the directory</li>
 *   <li>Send the zip archive to the s3 server</li>
 *   <li>Send the upload result through the progress manager for each file contained in the archive</li>
 * </ul>
 * The result of the upload is sent through the progress manager using
 * {@link IPeriodicActionProgressManager#storagePendingActionSucceed(String)} if it was successful or
 * {@link IPeriodicActionProgressManager#storagePendingActionError(Path)} otherwise
 *
 * @author Thibaud Michaudel
 **/
public class SubmitUpdatedArchiveTask extends AbstractSubmitArchiveTask implements LockServiceTask<Boolean> {

    public SubmitUpdatedArchiveTask(SubmitReadyArchiveTaskConfiguration configuration,
                                    IPeriodicActionProgressManager progressManager) {
        super(configuration, progressManager);
    }

    @Override
    public Boolean run() {
        LOGGER.info("Starting CleanDirectoryTask on {}", configuration.dirPath());
        long start = System.currentTimeMillis();
        boolean success = handleArchiveToSend(configuration.dirPath());
        LOGGER.info("End of SubmitReadyArchiveTask on {} after {} ms",
                    configuration.dirPath(),
                    System.currentTimeMillis() - start);

        return success;
    }

    protected boolean handleEmptyDir(Path dirPath) {
        // The directory is now empty, we need to delete it from the server
        String taskId = "S3DeleteArchive" + dirPath.getFileName();
        String entryKey = Paths.get(configuration.workspacePath(), S3Glacier.ZIP_DIR)
                               .relativize(dirPath)
                               .getParent()
                               .resolve(S3GlacierUtils.createArchiveNameFromBuildingDir(dirPath.getFileName()
                                                                                               .toString()))
                               .toString();

        StorageCommand.Delete deleteCmd = new StorageCommand.Delete.Impl(configuration.storageConfiguration(),
                                                                         new StorageCommandID(taskId,
                                                                                              UUID.randomUUID()),
                                                                         entryKey);
        try {
            StorageCommandResult.DeleteSuccess result = configuration.s3client()
                                                                     .delete(deleteCmd)
                                                                     .flatMap(deleteResult -> deleteResult.matchDeleteResult(
                                                                         Mono::just,
                                                                         unreachable -> Mono.error(new RuntimeException(
                                                                             "Unreachable endpoint")),
                                                                         failure -> Mono.error(new RuntimeException(
                                                                             "Delete failure in S3 storage"))))
                                                                     .onErrorResume(e -> Mono.error(new S3ClientException(
                                                                         e)))
                                                                     .block();
            if (result != null) {
                String archiveUrl = configuration.storageConfiguration().entryKeyUrl(entryKey).toString();
                return result.matchDeleteResult(r -> this.onDeleteSuccess(r, dirPath, archiveUrl),
                                                r -> this.onStorageUnreachable(r, archiveUrl),
                                                r -> this.onDeleteFailure(r, archiveUrl));
            } else {
                return false;
            }
        } catch (S3ClientException e) {
            LOGGER.error("Error while deleting empty archive in S3 Storage", e);
            return false;
        }
    }

    private Boolean onDeleteFailure(StorageCommandResult.DeleteFailure deleteFailure, String archiveUrl) {
        LOGGER.error("Deletion failure for archive {}", archiveUrl);
        return false;
    }

    private Boolean onStorageUnreachable(StorageCommandResult.UnreachableStorage unreachableStorage,
                                         String archiveUrl) {
        LOGGER.error("Deletion failure for archive {}", archiveUrl);
        LOGGER.error(unreachableStorage.getThrowable().getMessage(), unreachableStorage.getThrowable());
        return false;
    }

    public Boolean onDeleteSuccess(StorageCommandResult.DeleteSuccess success, Path dirPath, String archiveUrl) {
        try {
            LOGGER.info(
                "Archive {} successfully deleted from remote S3 storage. Deleting local archive {} and reference in database.",
                archiveUrl,
                dirPath);
            // Deleting local dir
            Files.delete(dirPath.toRealPath());

            // Deleting link
            Files.delete(dirPath);

            // Deleting archive info as it doesn't exist anymore
            S3GlacierUtils.S3GlacierUrl s3FileInfo = S3GlacierUtils.dispatchS3FilePath(archiveUrl);
            progressManager.archiveDeleted(configuration.storageName(), s3FileInfo.archiveFilePath());
            return true;
        } catch (IOException e) {
            LOGGER.error("Error while deleting local directory {}, but the "
                         + "corresponding directory in the S3 Storage was "
                         + "successfully deleted", dirPath, e);
            return false;
        }
    }
}
