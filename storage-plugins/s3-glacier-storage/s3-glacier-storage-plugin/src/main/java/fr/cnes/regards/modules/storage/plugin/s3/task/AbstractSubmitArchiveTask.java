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
import fr.cnes.regards.framework.s3.domain.StorageEntry;
import fr.cnes.regards.framework.s3.exception.S3ClientException;
import fr.cnes.regards.framework.utils.file.ChecksumUtils;
import fr.cnes.regards.framework.utils.file.ZipUtils;
import fr.cnes.regards.modules.fileaccess.plugin.domain.IPeriodicActionProgressManager;
import fr.cnes.regards.modules.storage.plugin.s3.S3Glacier;
import fr.cnes.regards.modules.storage.plugin.s3.configuration.SubmitReadyArchiveTaskConfiguration;
import fr.cnes.regards.modules.storage.plugin.s3.utils.S3GlacierUtils;
import io.vavr.Tuple;
import io.vavr.control.Option;
import org.slf4j.Logger;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author Thibaud Michaudel
 **/
public abstract class AbstractSubmitArchiveTask implements LockServiceTask<Boolean> {

    protected static final Logger LOGGER = getLogger(SubmitReadyArchiveTask.class);

    protected final SubmitReadyArchiveTaskConfiguration configuration;

    protected final IPeriodicActionProgressManager progressManager;

    public AbstractSubmitArchiveTask(SubmitReadyArchiveTaskConfiguration configuration,
                                     IPeriodicActionProgressManager progressManager) {
        this.configuration = configuration;
        this.progressManager = progressManager;
    }

    /**
     * Delete the created local archive and the directory content if the storage succeeded
     */
    private static void cleanBuildingArchive(Path dirPath,
                                             List<File> filesList,
                                             Path archiveToCreate,
                                             boolean storageSuccess) {
        if (storageSuccess) {
            try {
                // Delete files only if the storage succeeded
                if (Files.isSymbolicLink(dirPath)) {
                    //Link target
                    Path dirPathInCache = dirPath.toRealPath();
                    //If the directory is a symbolic link to the archive cache workspace, delete
                    //the link but not the real directory because it willl be deleted when the cache will be cleaned
                    Files.delete(dirPath);
                    //Also delete the archive in the cache workspace because it is no longer up to date.
                    Path archivePathInCache = dirPathInCache.getParent()
                                                            .resolve(S3GlacierUtils.createArchiveNameFromBuildingDir(
                                                                dirPathInCache.getFileName().toString()));
                    Files.delete(archivePathInCache);
                } else {
                    //If it's a real directory, delete both the files and the directory
                    filesList.forEach(file -> {
                        try {
                            Files.delete(file.toPath());
                        } catch (IOException e) {
                            LOGGER.error("Error while deleting {}", file.getName(), e);
                        }
                    });
                    Files.delete(dirPath);
                }
            } catch (DirectoryNotEmptyException e) {
                LOGGER.error("Could not delete {} as it is not empty", dirPath.getFileName(), e);
            } catch (IOException e) {
                LOGGER.error("Error while deleting {}", dirPath.getFileName(), e);
            }
        }

        // Delete the archive whether the storage was successful or not
        try {
            Files.deleteIfExists(archiveToCreate);
        } catch (IOException e) {
            LOGGER.error("Error while deleting the archive {}", archiveToCreate.getFileName(), e);
        }
    }

    protected boolean handleArchiveToSend(Path dirPath) {
        boolean success;
        // Creating list of files to add to the archive
        List<File> filesList;
        try (Stream<Path> filesListStream = Files.list(dirPath)) {
            filesList = filesListStream.map(Path::toFile).toList();
        } catch (IOException e) {
            LOGGER.error(
                "Error while attempting to access small files directory {} in archives workspace during periodic "
                + "actions, no files from this directory will be submitted to S3 and no request will be updated",
                dirPath);
            LOGGER.error(e.getMessage(), e);
            return false;
        }
        if (filesList.isEmpty()) {
            success = handleEmptyDir(dirPath);
        } else {
            // Computing relative path of the archive on the storage
            String archiveName = S3GlacierUtils.createArchiveNameFromBuildingDir(dirPath.getFileName().toString());
            String archivePathOnStorage = Paths.get(Paths.get(configuration.workspacePath(), S3Glacier.ZIP_DIR)
                                                         .relativize(dirPath.getParent())
                                                         .toString(), archiveName).toString();

            Path archiveToCreate = dirPath.getParent().resolve(archiveName);

            // Creating and sending archive
            success = createAndSendArchive(filesList, archivePathOnStorage, archiveToCreate);

            // Sending storageSuccess or error to progressManager
            handleEndSubmit(progressManager, filesList, archivePathOnStorage, success);

            // Cleaning
            AbstractSubmitArchiveTask.cleanBuildingArchive(dirPath, filesList, archiveToCreate, success);
        }
        return success;
    }

    protected abstract boolean handleEmptyDir(Path dirPath);

    /**
     * Create the archive from the directory content and send it to the server
     */
    private boolean createAndSendArchive(List<File> filesList, String archivePathOnStorage, Path archiveToCreate) {
        try {
            boolean archiveCreationSuccess = ZipUtils.createZipArchive(archiveToCreate.toFile(), filesList);
            if (!archiveCreationSuccess) {
                LOGGER.error("Error while creating archive {}", archiveToCreate.getFileName());
                return false;
            }

            // Sending archive
            String checksum = ChecksumUtils.computeHexChecksum(archiveToCreate, S3Glacier.MD5_CHECKSUM);
            Long fileSize = Files.size(archiveToCreate);

            Flux<ByteBuffer> buffers = DataBufferUtils.read(archiveToCreate, new DefaultDataBufferFactory(), 1024)
                                                      .map(DataBuffer::asByteBuffer);

            StorageEntry storageEntry = StorageEntry.builder()
                                                    .config(configuration.storageConfiguration())
                                                    .fullPath(archivePathOnStorage)
                                                    .checksum(Option.some(Tuple.of(S3Glacier.MD5_CHECKSUM, checksum)))
                                                    .size(Option.some(fileSize))
                                                    .data(buffers)
                                                    .build();
            String taskId = "S3GlacierPeriodicAction" + OffsetDateTime.now()
                                                                      .format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

            // Sending archive
            handleArchiveToSend(archiveToCreate, archivePathOnStorage, storageEntry, taskId);

            // Saving archive information
            progressManager.archiveStored(configuration.storageName(),
                                          configuration.storageConfiguration()
                                                       .entryKeyUrl(archivePathOnStorage)
                                                       .toString(),
                                          checksum,
                                          fileSize);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("Error while sending created archive, unknown algorithm {}", S3Glacier.MD5_CHECKSUM, e);
            return false;
        } catch (IOException e) {
            LOGGER.error("Error while sending created archive", e);
            return false;
        } catch (S3ClientException e) {
            LOGGER.error("Error while  writing on storage", e);
            return false;
        }
        return true;
    }

    /**
     * Send the archive to the server
     */
    private void handleArchiveToSend(Path archiveToCreate, String entryKey, StorageEntry storageEntry, String taskId)
        throws S3ClientException {
        StorageCommand.Write writeCmd = new StorageCommand.Write.Impl(configuration.storageConfiguration(),
                                                                      new StorageCommandID(taskId, UUID.randomUUID()),
                                                                      entryKey,
                                                                      storageEntry);

        LOGGER.info("Glacier accessing S3 to send small file archive");
        configuration.s3client()
                     .write(writeCmd)
                     .flatMap(writeResult -> writeResult.matchWriteResult(Mono::just,
                                                                          unreachable -> Mono.error(new RuntimeException(
                                                                              "Unreachable endpoint")),
                                                                          failure -> Mono.error(new RuntimeException(
                                                                              "Write failure in S3 storage"))))
                     .onErrorResume(e -> {
                         LOGGER.error("[{}] End storing {}", taskId, archiveToCreate.getFileName(), e);
                         return Mono.error(new S3ClientException(e));
                     })
                     .doOnSuccess(success -> {
                         LOGGER.info("[{}] End storing {}", taskId, archiveToCreate.getFileName());
                     })
                     .block();
        LOGGER.info("Glacier S3 access ended");

    }

    /**
     * Send success status to the progress manager for each file that were pending
     */
    private void handleEndSubmit(IPeriodicActionProgressManager progressManager,
                                 List<File> filesList,
                                 String archivePathOnStorage,
                                 boolean storageSuccess) {
        filesList.forEach(file -> {
            if (storageSuccess) {
                URL storedFileUrl = configuration.storageConfiguration()
                                                 .entryKeyUrl(S3GlacierUtils.createSmallFilePath(archivePathOnStorage,
                                                                                                 file.getName()));
                progressManager.storagePendingActionSucceed(storedFileUrl.toString());
            } else {
                progressManager.storagePendingActionError(file.toPath());
            }
        });
    }
}
