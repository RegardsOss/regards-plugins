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
import fr.cnes.regards.framework.utils.file.ChecksumUtils;
import fr.cnes.regards.framework.utils.file.ZipUtils;
import fr.cnes.regards.modules.fileaccess.plugin.domain.IPeriodicActionProgressManager;
import fr.cnes.regards.modules.storage.plugin.smallfiles.ISmallFilesStorage;
import fr.cnes.regards.modules.storage.plugin.smallfiles.configuration.SubmitReadyArchiveTaskConfiguration;
import fr.cnes.regards.modules.storage.plugin.smallfiles.utils.SmallFilesUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Stream;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author Thibaud Michaudel
 **/
public abstract class AbstractSubmitArchiveTask implements LockServiceTask<Boolean> {

    protected static final Logger LOGGER = getLogger(AbstractSubmitArchiveTask.class);

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
                                                            .resolve(SmallFilesUtils.createArchiveNameFromBuildingDir(
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
                + "actions, no files from this directory will be submitted to glacier storage and no request will be updated",
                dirPath);
            LOGGER.error(e.getMessage(), e);
            return false;
        }
        if (filesList.isEmpty()) {
            success = handleEmptyDir(dirPath);
        } else {
            // Computing relative path of the archive on the storage
            String archiveName = SmallFilesUtils.createArchiveNameFromBuildingDir(dirPath.getFileName().toString());
            String archivePathOnStorage = Paths.get(Paths.get(configuration.workspacePath(), ISmallFilesStorage.ZIP_DIR)
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
            String checksum = ChecksumUtils.computeHexChecksum(archiveToCreate, ISmallFilesStorage.MD5_CHECKSUM);
            Long fileSize = Files.size(archiveToCreate);
            // Store archive file to glacier
            String storageEntryUrl = configuration.interfaceSmallFiles()
                                                  .storeFile(archiveToCreate, archivePathOnStorage, checksum, fileSize);

            // Saving archive information
            progressManager.archiveStored(configuration.storageName(), storageEntryUrl, checksum, fileSize);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("Error while sending created archive, unknown algorithm {}",
                         ISmallFilesStorage.MD5_CHECKSUM,
                         e);
            return false;
        } catch (IOException e) {
            LOGGER.error("Error while sending created archive", e);
            return false;
        } catch (Exception e) {
            LOGGER.error("Error while writing on storage", e);
            return false;
        }
        return true;
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
                URL storedFileUrl = configuration.interfaceSmallFiles()
                                                 .getStorageUrl(SmallFilesUtils.createSmallFilePath(archivePathOnStorage,
                                                                                                    file.getName()));
                progressManager.storagePendingActionSucceed(storedFileUrl.toString());
            } else {
                progressManager.storagePendingActionError(file.toPath());
            }
        });
    }
}
