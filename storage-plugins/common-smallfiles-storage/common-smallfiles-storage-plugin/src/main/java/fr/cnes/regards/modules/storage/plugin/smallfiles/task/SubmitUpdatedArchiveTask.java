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
package fr.cnes.regards.modules.storage.plugin.smallfiles.task;

import fr.cnes.regards.modules.fileaccess.plugin.domain.IPeriodicActionProgressManager;
import fr.cnes.regards.modules.storage.plugin.smallfiles.ISmallFilesStorage;
import fr.cnes.regards.modules.storage.plugin.smallfiles.configuration.SubmitReadyArchiveTaskConfiguration;
import fr.cnes.regards.modules.storage.plugin.smallfiles.utils.SmallFilesUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Submit a small file archive that is either full or old enough to be closed even if not full :
 * <ul>
 *   <li>If its the _current directory and its old enough, close it</li>
 *   <li>Create the zip archive from the directory</li>
 *   <li>Send the zip archive to the glacier server</li>
 *   <li>Send the upload result through the progress manager for each file contained in the archive</li>
 * </ul>
 * The result of the upload is sent through the progress manager using
 * {@link IPeriodicActionProgressManager#storagePendingActionSucceed(String)} if it was successful or
 * {@link IPeriodicActionProgressManager#storagePendingActionError(Path)} otherwise
 *
 * @author Thibaud Michaudel
 **/
public class SubmitUpdatedArchiveTask extends AbstractSubmitArchiveTask {

    public SubmitUpdatedArchiveTask(SubmitReadyArchiveTaskConfiguration configuration,
                                    IPeriodicActionProgressManager progressManager) {
        super(configuration, progressManager);
    }

    @Override
    public Boolean run() {
        LOGGER.info("Starting SubmitReadyArchiveTask on {}", configuration.dirPath());
        long start = System.currentTimeMillis();
        boolean success = handleArchiveToSend(configuration.dirPath());
        LOGGER.info("End of SubmitReadyArchiveTask on {} after {} ms",
                    configuration.dirPath(),
                    System.currentTimeMillis() - start);

        return success;
    }

    protected boolean handleEmptyDir(Path dirPath) {
        // The directory is now empty, we need to delete it from the server
        String taskId = "GlacierDeleteArchive" + dirPath.getFileName();
        String entryKey = Paths.get(configuration.workspacePath(), ISmallFilesStorage.ZIP_DIR)
                               .relativize(dirPath)
                               .getParent()
                               .resolve(SmallFilesUtils.createArchiveNameFromBuildingDir(dirPath.getFileName()
                                                                                                .toString()))
                               .toString();

        boolean deleteResult = configuration.interfaceSmallFiles().deleteArchive(taskId, entryKey);
        if (deleteResult) {
            try {
                // Deleting local dir
                Files.delete(dirPath.toRealPath());

                // Deleting link
                Files.delete(dirPath);

                // Deleting archive info as it doesn't exist anymore
                SmallFilesUtils.GlacierUrl glacierFileInfo = SmallFilesUtils.dispatchFilePath(configuration.interfaceSmallFiles()
                                                                                                           .getStorageUrl(
                                                                                                               entryKey)
                                                                                                           .toString());
                progressManager.archiveDeleted(configuration.storageName(), glacierFileInfo.archiveFilePath());
            } catch (IOException e) {
                LOGGER.error("Error while deleting local directory {}, but the "
                             + "corresponding directory in the S3 Storage was "
                             + "successfully deleted", dirPath, e);
                return false;
            }
        }
        return deleteResult;
    }
}
