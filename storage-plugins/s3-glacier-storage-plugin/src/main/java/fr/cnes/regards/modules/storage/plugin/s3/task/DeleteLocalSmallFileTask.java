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
import fr.cnes.regards.modules.storage.domain.database.request.FileDeletionRequest;
import fr.cnes.regards.modules.storage.domain.plugin.IDeletionProgressManager;
import fr.cnes.regards.modules.storage.plugin.s3.S3Glacier;
import fr.cnes.regards.modules.storage.plugin.s3.configuration.DeleteLocalSmallFileTaskConfiguration;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
public class DeleteLocalSmallFileTask implements LockServiceTask {

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
    public void run() {
        Path localPathWithArchiveDelimiter = Path.of(configuration.archiveBuildingWorkspacePath())
                                                 .resolve(configuration.fileRelativePath());
        String[] split = localPathWithArchiveDelimiter.getFileName()
                                                      .toString()
                                                      .split(Pattern.quote(S3Glacier.ARCHIVE_DELIMITER));
        String dirName = split[0].substring(0, split[0].indexOf(S3Glacier.ARCHIVE_EXTENSION));
        Path localPath = localPathWithArchiveDelimiter.getParent().resolve(dirName).resolve(split[1]);
        Path localPathCurrent = localPathWithArchiveDelimiter.getParent()
                                                             .resolve(dirName + S3Glacier.CURRENT_ARCHIVE_SUFFIX)
                                                             .resolve(split[1]);

        if (Files.exists(localPath)) {
            try {
                Files.delete(localPath);
                Path parentPath = localPath.getParent();
                try (Stream<Path> entries = Files.list(parentPath)) {
                    if (entries.findFirst().isEmpty()) {
                        // The directory is now empty, we can't be sure it's present on the server, but it should be in
                        // 99% of use cases. We won't check for presence before deleting it for performances gain.
                        String taskId = "S3DeleteArchive" + parentPath.getFileName();
                        String entryKey = configuration.storageConfiguration()
                                                       .entryKey(configuration.fileRelativePath()
                                                                              .getParent()
                                                                              .toString());

                        StorageCommand.Delete deleteCmd = new StorageCommand.Delete.Impl(configuration.storageConfiguration(),
                                                                                         new StorageCommandID(taskId,
                                                                                                              UUID.randomUUID()),
                                                                                         entryKey);
                        configuration.s3Client()
                                     .delete(deleteCmd)
                                     .flatMap(deleteResult -> deleteResult.matchDeleteResult(Mono::just,
                                                                                             unreachable -> Mono.error(
                                                                                                 new RuntimeException(
                                                                                                     "Unreachable endpoint")),
                                                                                             failure -> Mono.error(new RuntimeException(
                                                                                                 "Delete failure in S3 storage"))))
                                     .doOnError(t -> progressManager.deletionFailed(request,
                                                                                    "Error while deleting empty archive "
                                                                                    + "in S3 Storage"))
                                     .doOnSuccess(success -> {
                                         progressManager.deletionSucceed(request);
                                         try {
                                             Files.delete(parentPath);
                                         } catch (IOException e) {
                                             LOGGER.error("Error while deleting local directory {}, but the "
                                                          + "corresponding directory in the S3 Storage was "
                                                          + "successfully deleted", parentPath, e);
                                         }
                                     })
                                     .block();
                    } else {
                        progressManager.deletionSucceed(request);
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Error while trying to delete {}", localPath, e);
                progressManager.deletionFailed(request, String.format("Error while trying to delete %s", localPath));
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
            progressManager.deletionFailed(request,
                                           String.format("The file to delete %s should exists locally but "
                                                         + "wasn't found", localPath));
        }
    }
}
