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
import fr.cnes.regards.framework.utils.file.DownloadUtils;
import fr.cnes.regards.modules.fileaccess.dto.request.FileStorageRequestAggregationDto;
import fr.cnes.regards.modules.fileaccess.plugin.domain.IStorageProgressManager;
import fr.cnes.regards.modules.storage.plugin.smallfiles.ISmallFilesStorage;
import fr.cnes.regards.modules.storage.plugin.smallfiles.configuration.StoreSmallFileTaskConfiguration;
import fr.cnes.regards.modules.storage.plugin.smallfiles.utils.SmallFilesUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Task to store small file for S3Glacier
 * <p>
 * Save the small file in the workspace to build the directory that will be archived and sent to the S3 Glacier.
 * The task has the following responsibilities :
 * <ul>
 *   <li>Create the node in the workspace and the building directory if needed</li>
 *   <li>Handle any possible issue if the file already exists </li>
 *   <li>Download the file in the workspace</li>
 *   <li>Check that the file's information match what is expected</li>
 *   <li>Rename the building directory if it is full after the download is complete </li>
 *   <li>Send the call result through the progress manager</li>
 * </ul>
 * <p>
 * The result of the call is sent through the progress manager using
 * {@link IStorageProgressManager#storageSucceedWithPendingActionRemaining(FileStorageRequestAggregationDto, URL, Long, Boolean)}
 * in case of success or {@link IStorageProgressManager#storageFailed(FileStorageRequestAggregationDto, String)} in case of error
 *
 * @author Thibaud Michaudel
 **/
public class StoreSmallFileTask implements LockServiceTask<Void> {

    private static final Logger LOGGER = getLogger(StoreSmallFileTask.class);

    private final StoreSmallFileTaskConfiguration configuration;

    private final FileStorageRequestAggregationDto request;

    private final IStorageProgressManager progressManager;

    private final Function<String, URL> urlProvider;

    public StoreSmallFileTask(StoreSmallFileTaskConfiguration storeSmallFileTaskConfiguration,
                              FileStorageRequestAggregationDto request,
                              IStorageProgressManager progressManager,
                              Function<String, URL> urlProvider) {
        this.configuration = storeSmallFileTaskConfiguration;
        this.request = request;
        this.progressManager = progressManager;
        this.urlProvider = urlProvider;
    }

    @Override
    public Void run() {
        LOGGER.info("Starting StoreSmallFileTask on {}", request.getMetaInfo().getFileName());
        long start = System.currentTimeMillis();
        // Local path of the node
        Path node = Paths.get(configuration.workspacePath(),
                              ISmallFilesStorage.ZIP_DIR,
                              configuration.rootPath(),
                              request.getSubDirectory() != null ? request.getSubDirectory() : "");
        Optional<ArchiveInfo> optionalArchiveInfo = createArchiveInfo(node);
        // Save the file only if it is valid and not already saved
        optionalArchiveInfo.ifPresent(this::saveLocalSmallFile);
        LOGGER.info("Ending StoreSmallFileTask on {} after {}",
                    request.getMetaInfo().getFileName(),
                    System.currentTimeMillis() - start);
        return null;
    }

    /**
     * Get the archive name and location for the given node, this method handles the following cases
     * <ul>
     *   <li>Node directory exists or need to be created</li>
     *   <li>Archive directory exists or need ot be created</li>
     *   <li>Filename already exists</li>
     * </ul>
     *
     * @return Optional {@link ArchiveInfo} : Empty if there is nothing to do on the given path.
     */
    private Optional<ArchiveInfo> createArchiveInfo(Path node) {
        Path localArchiveLocation;
        String archiveName;
        // Check if the node directory or the current directory need to be created
        if (!node.toFile().exists()) {
            archiveName = OffsetDateTime.now()
                                        .format(DateTimeFormatter.ofPattern(ISmallFilesStorage.ARCHIVE_DATE_FORMAT));
            localArchiveLocation = node.resolve(ISmallFilesStorage.BUILDING_DIRECTORY_PREFIX
                                                + archiveName
                                                + ISmallFilesStorage.CURRENT_ARCHIVE_SUFFIX);
        } else {
            try (Stream<Path> fileList = Files.list(node)) {
                Optional<String> currentDateArchiveOptional = fileList.map(Path::getFileName)
                                                                      .map(Path::toString)
                                                                      .filter(dirName -> dirName.endsWith(
                                                                          ISmallFilesStorage.CURRENT_ARCHIVE_SUFFIX))
                                                                      .findFirst();
                if (currentDateArchiveOptional.isPresent()) {
                    archiveName = SmallFilesUtils.removePrefixAndSuffix(currentDateArchiveOptional.get());
                    localArchiveLocation = node.resolve(currentDateArchiveOptional.get());

                    // The file might be present following a deletion without physical deletion enabled
                    boolean fileAlreadyExists = handleFileAlreadyExists(localArchiveLocation, archiveName);
                    if (fileAlreadyExists) {
                        // The same file already exists, nothing to do
                        return Optional.empty();
                    }
                } else {
                    archiveName = OffsetDateTime.now()
                                                .format(DateTimeFormatter.ofPattern(ISmallFilesStorage.ARCHIVE_DATE_FORMAT));
                    localArchiveLocation = node.resolve(ISmallFilesStorage.BUILDING_DIRECTORY_PREFIX
                                                        + archiveName
                                                        + ISmallFilesStorage.CURRENT_ARCHIVE_SUFFIX);
                }

            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
                progressManager.storageFailed(request, "Error while opening workspace zip directory");
                return Optional.empty();
            } catch (NoSuchAlgorithmException e) {
                LOGGER.error(e.getMessage(), e);
                progressManager.storageFailed(request,
                                              String.format("Unknown checksum algorithm %s ",
                                                            ISmallFilesStorage.MD5_CHECKSUM));
                return Optional.empty();
            }
        }
        ArchiveInfo archiveInfo = new ArchiveInfo(localArchiveLocation, archiveName);
        return Optional.of(archiveInfo);
    }

    /**
     * Check if the file already exists :
     * <ul>
     *   <li>Same name and checksum : Send success to progress manager and end task</li>
     *   <li>Same name different checksum : Rename the file by adding _2 (or _3 ...)</li>
     *   <li>Different name : Do nothing </li>
     * </ul>
     *
     * @return true if the file already exists (same name and checksum)
     */
    private boolean handleFileAlreadyExists(Path localArchiveLocation, String archiveName)
        throws NoSuchAlgorithmException, IOException {
        int tryCount = 1;
        boolean uniqueFileName = false;
        while (!uniqueFileName) {
            Path filePath;
            if (tryCount == 1) {
                filePath = localArchiveLocation.resolve(request.getMetaInfo().getFileName());
            } else {
                filePath = localArchiveLocation.resolve(addCountBeforeExtension(request.getMetaInfo().getFileName(),
                                                                                tryCount));
            }

            if (Files.exists(filePath)) {
                if (ChecksumUtils.computeHexChecksum(filePath, ISmallFilesStorage.MD5_CHECKSUM)
                                 .equals(request.getMetaInfo().getChecksum())) {
                    // The file is already present, nothing to download the storage is successful
                    handleStorageSucceedWithPendingAction(request, progressManager, archiveName, Files.size(filePath));
                    return true;
                }
            } else {
                request.getMetaInfo()
                       .setFileName(tryCount > 1 ?
                                        addCountBeforeExtension(request.getMetaInfo().getFileName(), tryCount) :
                                        request.getMetaInfo().getFileName());
                uniqueFileName = true;
            }
            tryCount++;
        }
        return false;
    }

    private String addCountBeforeExtension(String fileName, long count) {
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex > 0) {
            return fileName.substring(0, extensionIndex) + "_" + count + fileName.substring(extensionIndex);
        }
        return fileName + "_" + count;
    }

    /**
     * Save the file in the archive building workspace.
     */
    private void saveLocalSmallFile(ArchiveInfo archiveInfo) {
        try {
            Path fileDownloadPath = archiveInfo.localArchiveLocation().resolve(request.getMetaInfo().getFileName());
            Files.createDirectories(fileDownloadPath.getParent());
            boolean checksumOk = DownloadUtils.downloadAndCheckChecksum(new URL(request.getOriginUrl()),
                                                                        fileDownloadPath,
                                                                        ISmallFilesStorage.MD5_CHECKSUM,
                                                                        request.getMetaInfo().getChecksum(),
                                                                        configuration.storages());
            if (!checksumOk) {
                Files.deleteIfExists(fileDownloadPath);
                progressManager.storageFailed(request, "The checksum of the file doesn't match the expected one");
            } else {
                long realFileSize = Files.size(fileDownloadPath);
                // Rename the _current archive if needed
                try (Stream<Path> fileList = Files.list(archiveInfo.localArchiveLocation())) {
                    if (fileList.mapToLong(f -> f.toFile().length()).sum() > configuration.archiveMaxSize()) {
                        String currentName = archiveInfo.localArchiveLocation().getFileName().toString();
                        String nameWithoutSuffix = currentName.substring(0,
                                                                         currentName.length()
                                                                         - ISmallFilesStorage.CURRENT_ARCHIVE_SUFFIX.length());
                        boolean renameOk = archiveInfo.localArchiveLocation()
                                                      .toFile()
                                                      .renameTo(archiveInfo.localArchiveLocation()
                                                                           .getParent()
                                                                           .resolve(nameWithoutSuffix)
                                                                           .toFile());
                        if (!renameOk) {
                            LOGGER.error("Error while renaming current building directory {}", currentName);
                            progressManager.storageFailed(request,
                                                          String.format(
                                                              "Error while renaming current building directory %s",
                                                              currentName));
                        }
                    }
                }
                handleStorageSucceedWithPendingAction(request,
                                                      progressManager,
                                                      archiveInfo.archiveName(),
                                                      realFileSize);
            }

        } catch (MalformedURLException e) {
            LOGGER.error(e.getMessage(), e);
            progressManager.storageFailed(request, String.format("Invalid source url %s", request.getOriginUrl()));
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            progressManager.storageFailed(request, String.format("Error while downloading %s", request.getOriginUrl()));
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error(e.getMessage(), e);
            progressManager.storageFailed(request,
                                          String.format("Unknown checksum algorithm %s ",
                                                        ISmallFilesStorage.MD5_CHECKSUM));
        }
    }

    private void handleStorageSucceedWithPendingAction(FileStorageRequestAggregationDto request,
                                                       IStorageProgressManager progressManager,
                                                       String archiveName,
                                                       long realFileSize) {
        String storedArchivePath = Paths.get(configuration.rootPath(),
                                             request.getSubDirectory() != null ? request.getSubDirectory() : "",
                                             archiveName + ISmallFilesStorage.ARCHIVE_EXTENSION).toString();
        String storedSmallFilePath = SmallFilesUtils.createSmallFilePath(storedArchivePath,
                                                                         request.getMetaInfo().getFileName());
        progressManager.storageSucceedWithPendingActionRemaining(request,
                                                                 urlProvider.apply(storedSmallFilePath),
                                                                 realFileSize,
                                                                 false);
    }

    private record ArchiveInfo(Path localArchiveLocation,
                               String archiveName) {

    }
}
