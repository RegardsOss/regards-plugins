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
import fr.cnes.regards.modules.storage.plugin.s3.utils.S3GlacierUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import software.amazon.awssdk.services.s3.model.RestoreObjectResponse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author Thibaud Michaudel
 **/
public class RestoreAndDeleteSmallFileTask implements LockServiceTask {

    private static final Logger LOGGER = getLogger(RestoreAndDeleteSmallFileTaskConfiguration.class);

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
    public void run() {
        String fileRelativePathAsString = configuration.fileRelativePath().toString();
        String directoryName = fileRelativePathAsString.substring(0,
                                                                  fileRelativePathAsString.indexOf(S3Glacier.ARCHIVE_EXTENSION));
        Path buildingWorkspaceDirectoryName = Path.of(configuration.archiveBuildingWorkspacePath())
                                                  .resolve(directoryName);

        if (!Files.exists(buildingWorkspaceDirectoryName)) {
            String archivePathAsString = directoryName + S3Glacier.ARCHIVE_EXTENSION;
            Path cacheWorkspaceArchive = Path.of(configuration.cachePath()).resolve(archivePathAsString);
            if (!Files.exists(cacheWorkspaceArchive)) {
                String key = StringUtils.isNotBlank(configuration.rootPath()) ?
                    configuration.rootPath() + File.separator + directoryName + S3Glacier.ARCHIVE_EXTENSION :
                    archivePathAsString;

                // Restore
                configuration.s3Client()
                             .restore(configuration.storageConfiguration(), key)
                             .doOnError(e -> LOGGER.warn(
                                 "The file to delete {} is present but its storage class is not "
                                 + "the expected one, the deletion process will continue as if.",
                                 request.getFileReference().getLocation().getUrl()))
                             .onErrorReturn(RestoreObjectResponse.builder().build())
                             .block();
                boolean restorationComplete = S3GlacierUtils.checkRestorationComplete(Path.of(configuration.cachePath(),
                                                                                              archivePathAsString),
                                                                                      key,
                                                                                      configuration.storageConfiguration(),
                                                                                      configuration.s3AccessTimeout(),
                                                                                      configuration.lockName(),
                                                                                      configuration.lockCreationDate(),
                                                                                      configuration.renewDuration(),
                                                                                      configuration.lockService());
                if (!restorationComplete) {
                    progressManager.deletionFailed(request,
                                                   String.format("Unable to restore the archive %s containing the "
                                                                 + "file to delete : timeout exceeded", key));
                    return;
                }
            }
            // Unzip the restored archive in the building directory
            ZipUtils.unzip(cacheWorkspaceArchive, buildingWorkspaceDirectoryName);
        }
        DeleteLocalSmallFileTaskConfiguration subTaskConfiguration = new DeleteLocalSmallFileTaskConfiguration(
            configuration.fileRelativePath(),
            configuration.archiveBuildingWorkspacePath(),
            configuration.storageConfiguration(),
            configuration.s3Client());
        DeleteLocalSmallFileTask task = new DeleteLocalSmallFileTask(subTaskConfiguration, request, progressManager);
        try {
            configuration.lockService()
                         .runWithLock(S3Glacier.LOCK_PREFIX
                                      + S3Glacier.replaceSeparatorInLock(fileRelativePathAsString), task);
        } catch (InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
            progressManager.deletionFailed(request, "The deletion task was interrupted before completion.");
        }
    }
}
