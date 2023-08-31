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
import fr.cnes.regards.framework.utils.file.DownloadUtils;
import fr.cnes.regards.modules.storage.domain.plugin.IPeriodicActionProgressManager;
import fr.cnes.regards.modules.storage.plugin.s3.S3Glacier;
import fr.cnes.regards.modules.storage.plugin.s3.configuration.CheckPendingActionTaskConfiguration;
import fr.cnes.regards.modules.storage.plugin.s3.utils.S3GlacierUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.slf4j.LoggerFactory.getLogger;

/**
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
        String dirName = S3GlacierUtils.createBuildDirectoryFromArchiveName(configuration.archivePath()
                                                                                         .getFileName()
                                                                                         .toString());
        Path localFilePath = Path.of(configuration.workspacePath(),
                                     S3Glacier.ZIP_DIR,
                                     configuration.archivePath().getParent().resolve(dirName).toString(),
                                     configuration.fileName());

        Path localCurrentFilePath = Path.of(configuration.workspacePath(),
                                            S3Glacier.ZIP_DIR,
                                            configuration.archivePath().getParent().resolve(dirName).toString()
                                            + S3Glacier.CURRENT_ARCHIVE_SUFFIX,
                                            configuration.fileName());

        boolean localExists = Files.exists(localFilePath);
        boolean localCurrentExists = Files.exists(localCurrentFilePath);
        boolean remoteExists = DownloadUtils.existsS3(configuration.archivePath().toString(),
                                                      configuration.storageConfig());

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
