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

import fr.cnes.regards.framework.jpa.multitenant.lock.LockServiceTask;
import fr.cnes.regards.modules.fileaccess.plugin.domain.IPeriodicActionProgressManager;
import fr.cnes.regards.modules.storage.plugin.smallfiles.ISmallFilesStorage;
import fr.cnes.regards.modules.storage.plugin.smallfiles.configuration.CheckPendingActionTaskConfiguration;
import fr.cnes.regards.modules.storage.plugin.smallfiles.utils.SmallFilesUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Check if the pending actions of the given file have been completed or not.
 * This is necessary to prevent pending actions to stay forever if the job is terminated (due to an error
 * or a shutdown/crash) between the moment when the pending action is completed and the moment when the progress
 * manager is informed of the completion.
 *
 * @author Thibaud Michaudel
 **/
public class CheckPendingActionTask implements LockServiceTask<Void> {

    private static final Logger LOGGER = getLogger(CheckPendingActionTask.class);

    IPeriodicActionProgressManager progressManager;

    CheckPendingActionTaskConfiguration configuration;

    public CheckPendingActionTask(CheckPendingActionTaskConfiguration configuration,
                                  IPeriodicActionProgressManager progressManager) {
        this.progressManager = progressManager;
        this.configuration = configuration;
    }

    @Override
    public Void run() {
        String dirName = SmallFilesUtils.computePathOfBuildDirectoryFromArchiveName(configuration.archivePath()
                                                                                                 .getFileName()
                                                                                                 .toString());
        String dirPath = configuration.archivePath().getParent() != null ?
            configuration.archivePath().getParent().resolve(dirName).toString() :
            dirName;
        Path localFilePath = Path.of(configuration.workspacePath(),
                                     ISmallFilesStorage.ZIP_DIR,
                                     dirPath,
                                     configuration.fileName());

        Path localCurrentFilePath = Path.of(configuration.workspacePath(),
                                            ISmallFilesStorage.ZIP_DIR,
                                            dirPath + ISmallFilesStorage.CURRENT_ARCHIVE_SUFFIX,
                                            configuration.fileName());

        boolean localExists = Files.exists(localFilePath);
        boolean localCurrentExists = Files.exists(localCurrentFilePath);
        boolean remoteExists = configuration.urlExists().apply(configuration.archivePath());
        if (localExists || localCurrentExists) {
            if (remoteExists) {
                //The file has been uploaded but some errors occurred before the process was completed
                //Delete the file and the directory if its empty, otherwise it will be deleted later with sibling
                // requests
                Path filePath = localExists ? localFilePath : localCurrentFilePath;
                Path parentPath = filePath.getParent();
                try {
                    Files.delete(filePath);
                    try (Stream<Path> files = Files.list(parentPath)) {
                        if (files.findFirst().isEmpty()) {
                            Files.delete(parentPath);
                        }
                    }
                    progressManager.storagePendingActionSucceed(configuration.url());
                } catch (IOException e) {
                    LOGGER.error("Error while attempting to delete file {} or the containing directory", filePath, e);
                }

            } else {
                //The file has not been uploaded, this is the nominal case, nothing to do
            }
        } else {
            if (remoteExists) {
                //The file has been uploaded but some errors occurred after the
                // local file was deleted but before the process was completed
                LOGGER.warn("The file {} was successfully stored earlier but the pending action was not completed, "
                            + "removing it now", configuration.url());
                progressManager.storagePendingActionSucceed(configuration.url());
            } else {
                //The file has not been uploaded but is not present locally, the file is lost
                //This cannot happen without external interference (i.e. someone deleting the local file from the
                // filesystem)
                LOGGER.error("The file with url {} is neither present locally or on the target storage, the file is "
                             + "lost", configuration.url());
                progressManager.storagePendingActionError(localFilePath);
            }
        }
        return null;
    }
}
