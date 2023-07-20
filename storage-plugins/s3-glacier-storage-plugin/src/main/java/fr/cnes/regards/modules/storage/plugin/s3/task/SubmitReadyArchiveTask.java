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
import fr.cnes.regards.framework.s3.domain.StorageEntry;
import fr.cnes.regards.framework.s3.exception.S3ClientException;
import fr.cnes.regards.framework.utils.file.ChecksumUtils;
import fr.cnes.regards.framework.utils.file.ZipUtils;
import fr.cnes.regards.modules.storage.domain.plugin.IPeriodicActionProgressManager;
import fr.cnes.regards.modules.storage.plugin.s3.S3Glacier;
import fr.cnes.regards.modules.storage.plugin.s3.configuration.SubmitReadyArchiveTaskConfiguration;
import fr.cnes.regards.modules.storage.plugin.s3.utils.S3GlacierUtils;
import io.vavr.Tuple;
import io.vavr.control.Option;
import org.apache.commons.lang3.time.DateUtils;
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
import java.text.ParseException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.slf4j.LoggerFactory.getLogger;

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
public class SubmitReadyArchiveTask implements LockServiceTask<Boolean> {

    private static final Logger LOGGER = getLogger(SubmitReadyArchiveTask.class);

    private final SubmitReadyArchiveTaskConfiguration configuration;

    private final IPeriodicActionProgressManager progressManager;

    public SubmitReadyArchiveTask(SubmitReadyArchiveTaskConfiguration configuration,
                                  IPeriodicActionProgressManager progressManager) {
        this.configuration = configuration;
        this.progressManager = progressManager;
    }

    @Override
    public Boolean run() {
        LOGGER.info("Starting CleanDirectoryTask on {}", configuration.dirPath());
        long start = System.currentTimeMillis();
        boolean continueOk = true;
        // Renaming _current if needed
        Optional<PathAndSuccessState> optionalFinalDirPath = renameCurrentIfNeeded(configuration.dirPath(), continueOk);
        if (optionalFinalDirPath.isEmpty()) {
            // Its the _current dir and it is not old enough to send
            return true;
        }
        PathAndSuccessState finalDirPath = optionalFinalDirPath.get();

        // Creating list of files to add to the archive
        List<File> filesList;
        try (Stream<Path> filesListStream = Files.list(finalDirPath.dirPath())) {
            filesList = filesListStream.map(Path::toFile).toList();
        } catch (IOException e) {
            LOGGER.error(
                "Error while attempting to access small files directory {} in archives workspace during periodic "
                + "actions, no files from this directory will be submitted to S3 and no request will be updated",
                finalDirPath.dirPath());
            LOGGER.error(e.getMessage(), e);
            return false;
        }
        if (filesList.isEmpty()) {
            boolean deletionSuccess = handleEmptyDir(finalDirPath.dirPath());

            LOGGER.info("End of SubmitReadyArchiveTask on {} after {} ms",
                        configuration.dirPath(),
                        System.currentTimeMillis() - start);
            return deletionSuccess;
        } else {
            // Computing relative path of the archive on the storage
            String archiveName = S3GlacierUtils.createArchiveNameFromBuildingDir(finalDirPath.dirPath()
                                                                                             .getFileName()
                                                                                             .toString());
            String archivePathOnStorage = Paths.get(Paths.get(configuration.workspacePath(), S3Glacier.ZIP_DIR)
                                                         .relativize(finalDirPath.dirPath().getParent())
                                                         .toString(), archiveName).toString();

            Path archiveToCreate = finalDirPath.dirPath().getParent().resolve(archiveName);

            boolean storageSuccess;
            if (finalDirPath.continueOk()) {
                // Creating and sending archive
                storageSuccess = createAndSendArchive(filesList, archivePathOnStorage, archiveToCreate);
            } else {
                storageSuccess = false;
            }

            // Sending storageSuccess or error to progressManager
            handleEndSubmit(progressManager, filesList, archivePathOnStorage, storageSuccess);

            // Cleaning
            cleanBuildingArchive(finalDirPath.dirPath(), filesList, archiveToCreate, storageSuccess);

            LOGGER.info("End of SubmitReadyArchiveTask on {} after {} ms",
                        configuration.dirPath(),
                        System.currentTimeMillis() - start);
            return storageSuccess;
        }

    }

    private boolean handleEmptyDir(Path dirPath) {
        // The directory is now empty, we can't be sure it's present on the server, but it should be in
        // 99% of use cases. We won't check for presence before deleting it for performances gain.
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
            Files.delete(dirPath);
            // Deleting archive info as it doesn't exist anymore
            S3GlacierUtils.S3GlacierUrl s3FileInfo = S3GlacierUtils.dispatchS3FilePath(archiveUrl);
            configuration.glacierArchiveService()
                         .deleteGlacierArchive(configuration.storageName(), s3FileInfo.archiveFilePath());
            return true;
        } catch (IOException e) {
            LOGGER.error("Error while deleting local directory {}, but the "
                         + "corresponding directory in the S3 Storage was "
                         + "successfully deleted", dirPath, e);
            return false;
        }
    }

    /**
     * Rename the _current directory if it is too old and return the new path
     */
    private Optional<PathAndSuccessState> renameCurrentIfNeeded(Path dirPath, boolean continueOk) {
        if (dirPath.getFileName().toString().endsWith(S3Glacier.CURRENT_ARCHIVE_SUFFIX)) {
            String currentName = dirPath.getFileName().toString();
            String nameWithoutSuffix = S3GlacierUtils.removeSuffix(currentName);
            try {
                Instant dirCreationDate = DateUtils.parseDate(S3GlacierUtils.removePrefix(nameWithoutSuffix),
                                                              S3Glacier.ARCHIVE_DATE_FORMAT).toInstant();

                if (dirCreationDate.plus(configuration.archiveMaxAge(), ChronoUnit.HOURS).isAfter(Instant.now())) {
                    // the directory is not old enough, nothing to do
                    return Optional.empty();
                }
                Path newDirPath = dirPath.getParent().resolve(nameWithoutSuffix);
                continueOk = dirPath.toFile().renameTo(newDirPath.toFile());
                dirPath = newDirPath;
                if (!continueOk) {
                    LOGGER.error("Error while renaming current building directory {}", currentName);
                }
            } catch (ParseException e) {
                LOGGER.error("Error while parsing directory name as a date : {}", nameWithoutSuffix, e);
            }
        }
        return Optional.of(new PathAndSuccessState(dirPath, continueOk));
    }

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

            Flux<ByteBuffer> buffers = DataBufferUtils.read(archiveToCreate,
                                                            new DefaultDataBufferFactory(),
                                                            configuration.multipartThresholdMb() * 1024 * 1024)
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
            sendArchive(archiveToCreate, archivePathOnStorage, storageEntry, taskId);

            // Saving archive information
            configuration.glacierArchiveService()
                         .saveGlacierArchive(configuration.storageName(),
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
    private void sendArchive(Path archiveToCreate, String entryKey, StorageEntry storageEntry, String taskId)
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
}
