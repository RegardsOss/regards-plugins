/*
 * Copyright 2017-2022 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.storage.plugin.local.service;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import fr.cnes.regards.framework.gson.adapters.OffsetDateTimeAdapter;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.s3.S3StorageConfiguration;
import fr.cnes.regards.framework.utils.file.DownloadUtils;
import fr.cnes.regards.modules.fileaccess.plugin.domain.*;
import fr.cnes.regards.modules.fileaccess.plugin.dto.FileCacheRequestDto;
import fr.cnes.regards.modules.fileaccess.plugin.dto.FileDeletionRequestDto;
import fr.cnes.regards.modules.filecatalog.dto.AbstractStoragePluginConfigurationDto;
import fr.cnes.regards.modules.filecatalog.dto.FileReferenceWithoutOwnersDto;
import fr.cnes.regards.modules.filecatalog.dto.request.FileStorageRequestAggregationDto;
import fr.cnes.regards.modules.storage.plugin.local.dto.LocalStorageLocationConfigurationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.zip.ZipFile;

/**
 * Plugin handling the storage on local file system
 *
 * @author Sylvain Vissiere-Guerinet
 */
@Plugin(author = "REGARDS Team",
        description = "Plugin handling the storage on local file system",
        id = "Local",
        version = "1.0",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CNES",
        url = "https://regardsoss.github.io/")
public class LocalDataStorage implements IOnlineStorageLocation {

    /**
     * Plugin parameter name of the storage base location as a string
     */
    public static final String BASE_STORAGE_LOCATION_PLUGIN_PARAM_NAME = "Storage_URL";

    /**
     * Plugin parameter name of the can delete attribute
     */
    public static final String LOCAL_STORAGE_DELETE_OPTION = "Local_Delete_Option";

    /**
     * Plugin parameter name of the total space allowed
     */
    public static final String LOCAL_STORAGE_TOTAL_SPACE = "Local_Total_Space";

    public static final String LOCAL_STORAGE_MAX_ZIP_SIZE = "Local_Storage_Max_Zip_Size";

    public static final String LOCAL_STORAGE_MAX_FILE_SIZE_FOR_ZIP = "Local_Storage_Max_File_Size_For_Zip";

    public static final int MAX_REQUESTS_PER_WORKING_SUBSET = 100;

    public static final String IOEXCEPTION_ERROR_MESSAGE_FORMAT = "Storage of StorageDataFile(%s) failed due to the following IOException: %s";

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalDataStorage.class);

    /**
     * ZIP directory name which is only used when no storage sub directory is specified
     */
    private static final String ZIP_DIR_NAME = "zips";

    private static final String CURRENT_ZIP_NAME = "current";

    private static final Integer MAX_FILE_IN_ZIP = 1000;

    private static final String CREATE_ENV_FS = "create";

    private static final String ZIP_PROTOCOL = "jar:file:";

    private static final Long ZIP_ACQUIRE_TIMEOUT = 25L;

    private final Semaphore zipAccessSemaphore = new Semaphore(1, true);

    /**
     * Base storage location url
     */
    @PluginParameter(name = BASE_STORAGE_LOCATION_PLUGIN_PARAM_NAME,
                     description = "Root directory where to store new files on this location",
                     label = "Root directory")
    private String baseStorageLocationAsString;

    @PluginParameter(name = LOCAL_STORAGE_DELETE_OPTION,
                     defaultValue = "false",
                     label = "Enable physical deletion of files",
                     description = "If deletion is allowed, files are physically deleted else files are only removed from references")
    private Boolean allowPhysicalDeletion;

    @PluginParameter(name = LOCAL_STORAGE_MAX_FILE_SIZE_FOR_ZIP,
                     label = "Files maximum size for zips (octets)",
                     description = "When storing a new file in this location, if the file size is less than this value, so the file is stored with other \\\"small files\\\" in a zip archive. The size is in octets.",
                     defaultValue = "1000")
    private Long maxFileSizeForZip;

    @PluginParameter(name = LOCAL_STORAGE_MAX_ZIP_SIZE,
                     label = "Maximum zip size (octets)",
                     description = "When storing a new file in this location, \"small\" files are stored in a zip archive which maximum size is configurable thanks this property. The size is in octets.",
                     defaultValue = "500000000")
    private Long maxZipSize;

    @Autowired
    private S3StorageConfiguration knownS3Storages;

    @Override
    public Optional<Path> getRootPath() {
        return Optional.ofNullable(Paths.get(baseStorageLocationAsString));
    }

    @Override
    public PreparationResponse<FileStorageWorkingSubset, FileStorageRequestAggregationDto> prepareForStorage(Collection<FileStorageRequestAggregationDto> fileReferenceRequests) {
        return createWorkingSubset(fileReferenceRequests, FileStorageWorkingSubset::new);
    }

    @Override
    public PreparationResponse<FileDeletionWorkingSubset, FileDeletionRequestDto> prepareForDeletion(Collection<FileDeletionRequestDto> fileDeletionRequests) {
        return createWorkingSubset(fileDeletionRequests, FileDeletionWorkingSubset::new);
    }

    @Override
    public PreparationResponse<FileRestorationWorkingSubset, FileCacheRequestDto> prepareForRestoration(Collection<FileCacheRequestDto> fileCacheRequests) {
        return createWorkingSubset(fileCacheRequests, FileRestorationWorkingSubset::new);
    }

    /**
     * Creates working subsets of requests to limit number of requests to handle in a same batch.
     */
    private <W, R> PreparationResponse createWorkingSubset(Collection<R> requests,
                                                           Function<Collection<R>, W> createSubset) {
        Collection<W> workingSubSets = Lists.newArrayList();
        Iterator<R> it = requests.iterator();
        List<R> requestsPerSubset = new ArrayList<>();
        do {
            if (it.hasNext()) {
                requestsPerSubset.add(it.next());
            }
            if (requestsPerSubset.size() >= MAX_REQUESTS_PER_WORKING_SUBSET || (!it.hasNext()
                                                                                && !requestsPerSubset.isEmpty())) {
                workingSubSets.add(createSubset.apply(requestsPerSubset));
                requestsPerSubset.clear();
            }
        } while (it.hasNext() || !requestsPerSubset.isEmpty());
        return PreparationResponse.build(workingSubSets, Maps.newHashMap());
    }

    @Override
    public void store(FileStorageWorkingSubset workingSubset, IStorageProgressManager progressManager) {
        workingSubset.getFileReferenceRequests().forEach(data -> doStore(progressManager, data));
    }

    private void doStore(IStorageProgressManager progressManager, FileStorageRequestAggregationDto request) {
        Path fullPathToFile;
        try {
            fullPathToFile = getStorageLocation(request);
        } catch (IOException ioe) {
            String failureCause = String.format(IOEXCEPTION_ERROR_MESSAGE_FORMAT,
                                                request.getMetaInfo().getChecksum(),
                                                ioe);
            LOGGER.error(failureCause, ioe);
            progressManager.storageFailed(request, ioe.getMessage());
            return;
        }
        //check if file is already at the right place or not. Unless we are instructed not to(for updates for example)
        if (Files.exists(fullPathToFile)) {
            Long fileSize = fullPathToFile.toFile().length();
            //if it is, there is nothing to move/copy, we just need to say to the system that the file is stored successfully
            try {
                LOGGER.debug("[LOCAL STORAGE PLUGIN] File {} already exists, no replacement.", fullPathToFile);
                progressManager.storageSucceed(request, fullPathToFile.toUri().toURL(), fileSize);
            } catch (MalformedURLException e) {
                LOGGER.error(e.getMessage(), e);
                String failureCause = String.format("Invalid URL creation for file %s.", fullPathToFile);
                progressManager.storageFailed(request, failureCause);
            }
            return;
        }
        try {
            URL sourceUrl = new URL(request.getOriginUrl());
            boolean downloadOk = false;
            try {
                downloadOk = DownloadUtils.downloadAndCheckChecksum(sourceUrl,
                                                                    fullPathToFile,
                                                                    request.getMetaInfo().getAlgorithm(),
                                                                    request.getMetaInfo().getChecksum(),
                                                                    knownS3Storages.getStorages());
            } catch (IOException e) {
                throw new ModuleException(String.format("Download error for file %s. Cause : %s",
                                                        request.getOriginUrl(),
                                                        e.getMessage()), e);
            }
            if (downloadOk) {
                File file = fullPathToFile.toFile();
                if (file.canWrite()) {
                    file.setReadOnly();
                }
                Long fileSize = file.length();
                if (fileSize < maxFileSizeForZip) {
                    doStoreInZip(progressManager, request, file);
                } else {
                    progressManager.storageSucceed(request, fullPathToFile.toUri().toURL(), fileSize);
                }
            } else {
                Files.deleteIfExists(fullPathToFile);
                progressManager.storageFailed(request, "Checksum does not match with expected one");
            }
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error(e.getMessage(), e);
            String failureCause = String.format(
                "Invalid checksum algorithm %s. Unable to determine if the file is well formed.",
                request.getMetaInfo().getChecksum());
            progressManager.storageFailed(request, failureCause);
        } catch (IOException ioe) {
            String failureCause = String.format(IOEXCEPTION_ERROR_MESSAGE_FORMAT,
                                                request.getMetaInfo().getChecksum(),
                                                ioe);
            LOGGER.error(failureCause, ioe);
            fullPathToFile.toFile().delete();
            progressManager.storageFailed(request, failureCause);
        } catch (ModuleException e) {
            LOGGER.error(e.getMessage(), e);
            fullPathToFile.toFile().delete();
            progressManager.storageFailed(request, e.getMessage());
        }
    }

    private void doStoreInZip(IStorageProgressManager progressManager,
                              FileStorageRequestAggregationDto request,
                              File file) throws IOException {
        long start = System.currentTimeMillis();
        Path zipDirPath = getStorageLocationForZip(request);
        try {
            LOGGER.trace("[LOCAL STORAGE PLUGIN] Store in zip ....");
            zipAccessSemaphore.acquire();
            Path zipPath = getCurrentZipPath(zipDirPath);
            // check if file is already in zip
            Map<String, String> env = new HashMap<>(1);
            env.put(CREATE_ENV_FS, "true");
            String checksum = request.getMetaInfo().getChecksum();
            boolean downloadOk = false;
            try (FileChannel zipFC = FileChannel.open(zipPath, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
                FileLock zipLock = zipFC.lock();
                try (FileSystem zipFs = FileSystems.newFileSystem(URI.create(ZIP_PROTOCOL + zipPath.toAbsolutePath()),
                                                                  env)) {
                    Path pathInZip = zipFs.getPath(request.getMetaInfo().getChecksum());
                    if (Files.exists(pathInZip)) {
                        //if it is, there is nothing to move/copy, we just need to say to the system that the file is stored successfully
                        LOGGER.debug("[LOCAL STORAGE PLUGIN] File {} already exists in zip, no replacement.",
                                     pathInZip);
                        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                            Long fileSize = zipFile.getEntry(checksum).getSize();
                            progressManager.storageSucceed(request, zipPath.toUri().toURL(), fileSize);
                        }
                    } else {
                        // add the file into the zip
                        URL sourceUrl = new URL("file", null, file.getPath());
                        downloadOk = DownloadUtils.downloadAndCheckChecksum(sourceUrl,
                                                                            pathInZip,
                                                                            request.getMetaInfo().getAlgorithm(),
                                                                            checksum,
                                                                            knownS3Storages.getStorages());
                        // download issues are handled right here
                        // while download success has to be handle after the zip file system has been closed
                        // for zip entries to be detected correctly
                        if (!downloadOk) {
                            String failureCause = String.format(
                                "Storage of StorageDataFile(%s) failed at the following location: %s, in zip: %s. Its checksum once stored does not match with the expected one",
                                checksum,
                                pathInZip,
                                zipPath);
                            Files.deleteIfExists(pathInZip);
                            progressManager.storageFailed(request, failureCause);
                        }
                    }
                } finally {
                    zipLock.release();
                }
            }
            if (downloadOk) {
                try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                    Long fileSize = zipFile.getEntry(checksum).getSize();
                    progressManager.storageSucceed(request, zipPath.toUri().toURL(), fileSize);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("[LOCAL STORAGE PLUGIN] Storage into zip has been interrupted while acquiring semaphore.", e);
        } catch (MalformedURLException | NoSuchAlgorithmException e) {
            LOGGER.error(e.getMessage(), e);
            String failureCause = String.format("Invalid URL creation for file. %s", e.getMessage());
            progressManager.storageFailed(request, failureCause);
        } catch (IOException ioe) {
            String failureCause = String.format(IOEXCEPTION_ERROR_MESSAGE_FORMAT,
                                                request.getMetaInfo().getChecksum(),
                                                ioe);
            LOGGER.error(failureCause, ioe);
            progressManager.storageFailed(request, failureCause);
        } finally {
            LOGGER.trace("[LOCAL STORAGE PLUGIN] Store in zip done in {}ms", System.currentTimeMillis() - start);
            zipAccessSemaphore.release();
            file.delete();
        }
    }

    // this method is public because of tests
    public Path getStorageLocation(FileStorageRequestAggregationDto request) throws IOException {
        String checksum = request.getMetaInfo().getChecksum();
        Path storageLocation = Paths.get(baseStorageLocationAsString);
        if ((request.getSubDirectory() != null) && !request.getSubDirectory().isEmpty()) {
            // Storage directory is provided. use it
            storageLocation = Paths.get(baseStorageLocationAsString, request.getSubDirectory());
        } else {
            // Storage directory is not provided, generate new one with checksum
            int subFolders = 0;
            String fileChecksum = request.getMetaInfo().getChecksum();
            String subDir = fileChecksum.length() > 2 ? fileChecksum.substring(0, 2) : fileChecksum;
            int idx = 2;
            while (((idx + 2) < fileChecksum.length()) && (subFolders < 3)) {
                subDir = Paths.get(subDir, fileChecksum.substring(idx, idx + 2)).toString();
                idx = idx + 2;
                subFolders++;
            }
            storageLocation = storageLocation.resolve(subDir);
        }

        if (Files.notExists(storageLocation)) {
            Files.createDirectories(storageLocation);
        }
        // files are stored with the checksum as their name
        return storageLocation.resolve(checksum);
    }

    public Path getStorageLocationForZip(FileStorageRequestAggregationDto request) throws IOException {
        Path storageLocation = Paths.get(baseStorageLocationAsString);
        if (!Strings.isNullOrEmpty(request.getSubDirectory())) {
            // add the sub directory
            storageLocation = Paths.get(baseStorageLocationAsString, request.getSubDirectory());
        } else {
            // add "zips" to the storage location to get all zips in the same subdirectory
            storageLocation = storageLocation.resolve(ZIP_DIR_NAME);
        }
        return Files.createDirectories(storageLocation);
    }

    public Path getCurrentZipPath(Path storageLocation) throws IOException {
        // To avoid issue with knowing which zip is the right one, lets have a symlink on the current zip
        Path linkPath = storageLocation.resolve(CURRENT_ZIP_NAME);
        // If link exists but is associated to a non existing file, so delete the dead link
        if (Files.isSymbolicLink(linkPath) && Files.notExists(Files.readSymbolicLink(linkPath))) {
            LOGGER.warn("[LOCAL STORAGE PLUGIN] Deleting dead link {}", linkPath);
            Files.deleteIfExists(linkPath);
        }
        if (!Files.isSymbolicLink(linkPath)) {
            // If the link does not exist it means that no zip has been created yet
            // Lets create the first one
            Map<String, String> env = new HashMap<>(1);
            env.put(CREATE_ENV_FS, "true");
            Path zipPath = storageLocation.resolve("regards_"
                                                   + OffsetDateTime.now()
                                                                   .format(OffsetDateTimeAdapter.ISO_DATE_TIME_UTC)
                                                   + ".zip");
            try (FileSystem zipFs = FileSystems.newFileSystem(URI.create(ZIP_PROTOCOL + zipPath.toAbsolutePath()),
                                                              env)) {
                // now that zip has been created, lets create the link.
                Files.createSymbolicLink(linkPath, zipPath);
            }
            return zipPath;
        }
        if (Files.isSymbolicLink(linkPath)) {
            // if the link does exist, it means that a zip has already been created
            // in this case, we have to check its size to be sure we can still add files to it
            Path targetPath = Files.readSymbolicLink(linkPath);
            try (ZipFile zip = new ZipFile(targetPath.toFile())) {
                if ((targetPath.toFile().length() >= maxZipSize) || (zip.size() >= MAX_FILE_IN_ZIP)) {
                    // we have to create a new one
                    try (FileChannel targetFC = FileChannel.open(targetPath,
                                                                 StandardOpenOption.WRITE,
                                                                 StandardOpenOption.READ)) {
                        FileLock targetLock = targetFC.lock();
                        try {
                            // create a new zip and make the link points to the new zip
                            Map<String, String> env = new HashMap<>(1);
                            env.put(CREATE_ENV_FS, "true");
                            Path newZipPath = storageLocation.resolve("regards_"
                                                                      + OffsetDateTime.now()
                                                                                      .format(OffsetDateTimeAdapter.ISO_DATE_TIME_UTC)
                                                                      + ".zip");
                            try (FileSystem zipFs = FileSystems.newFileSystem(URI.create(ZIP_PROTOCOL
                                                                                         + newZipPath.toAbsolutePath()),
                                                                              env)) {
                                // now that zip has been created, lets create the link.
                                Files.deleteIfExists(linkPath);
                                Files.createSymbolicLink(linkPath, newZipPath);
                            }
                            return newZipPath;
                        } finally {
                            targetLock.release();
                        }
                    }
                } else {
                    return targetPath;
                }
            }
        }
        throw new IOException(
            "A file should have been put into a zip but we could not create nor retrieve the zip in which it should have been added");
    }

    @Override
    public void delete(FileDeletionWorkingSubset workingSubset, IDeletionProgressManager progressManager) {
        for (FileDeletionRequestDto request : workingSubset.getFileDeletionRequests()) {
            if (request.getFileReference().getLocation().getUrl().matches(".*regards_.*\\.zip")) {
                deleteFromZipPath(request, progressManager);
            } else {
                try {
                    URL url = new URL(request.getFileReference().getLocation().getUrl());
                    Path location = Paths.get(url.getPath());
                    if (Files.deleteIfExists(location)) {
                        LOGGER.debug("[LOCAL STORAGE PLUGIN] File {} deleted", location.toAbsolutePath());
                    } else {
                        LOGGER.debug("[LOCAL STORAGE PLUGIN] File {} not deleted as it does not exists",
                                     location.toAbsolutePath());
                    }
                    progressManager.deletionSucceed(request);
                } catch (IOException ioe) {
                    String failureCause = String.format(
                        "Deletion of StorageDataFile(%s) failed due to the following IOException: %s %s",
                        ioe.getClass().getSimpleName(),
                        request.getFileReference().getMetaInfo().getChecksum(),
                        ioe.getMessage());
                    LOGGER.error(failureCause, ioe);
                    progressManager.deletionFailed(request, failureCause);
                }
            }
        }
    }

    private void deleteFromZipPath(FileDeletionRequestDto request, IDeletionProgressManager progressManager) {
        try {
            Path zipPath = Paths.get(new URL(request.getFileReference().getLocation().getUrl()).getPath());
            if (Files.exists(zipPath) && Files.isReadable(zipPath)) {
                deleteFromZipPath(zipPath, request, progressManager);
            } else {
                LOGGER.debug("[LOCAL STORAGE PLUGIN] File to delete from a zip file [{}] but zip file does not exist.",
                             zipPath);
                progressManager.deletionSucceed(request);
            }
        } catch (IOException e) {
            String failureCause = String.format(
                "Deletion of StorageDataFile(checksum:%s) failed due to the following IOException: %s",
                request.getFileReference().getMetaInfo().getChecksum(),
                e.getMessage());
            LOGGER.error(failureCause, e);
            progressManager.deletionFailed(request, failureCause);
        }
    }

    private void deleteFromZipPath(Path zipPath,
                                   FileDeletionRequestDto request,
                                   IDeletionProgressManager progressManager) throws IOException {
        Map<String, String> env = new HashMap<>(1);
        env.put(CREATE_ENV_FS, "false");
        LOGGER.debug("[LOCAL STORAGE PLUGIN] File to delete from a zip file [{}].", zipPath);
        try (FileChannel zipFC = FileChannel.open(zipPath, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
            zipAccessSemaphore.acquire();
            try {
                FileLock zipLock = zipFC.lock();
                try {
                    try (FileSystem zipFs = FileSystems.newFileSystem(URI.create(ZIP_PROTOCOL
                                                                                 + zipPath.toAbsolutePath()), env)) {
                        Path pathInZip = zipFs.getPath(request.getFileReference().getMetaInfo().getChecksum());
                        Files.deleteIfExists(pathInZip);
                        progressManager.deletionSucceed(request);
                    }
                    try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                        if (!zipFile.entries().hasMoreElements()) {
                            Path linkPath = zipPath.getParent().resolve(CURRENT_ZIP_NAME);
                            // Check if it is the current zip file. If it is, delete the symboliclink
                            if (Files.isSymbolicLink(linkPath) && zipPath.equals(Files.readSymbolicLink(linkPath))) {
                                Files.delete(linkPath);
                            }
                            Files.deleteIfExists(zipPath);
                        }
                    }
                } finally {
                    zipLock.release();
                }
            } finally {
                zipAccessSemaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("[LOCAL STORAGE PLUGIN] Deletion from zip has been interrupted while acquiring semaphore.", e);
        }
    }

    @Override
    public InputStream retrieve(FileReferenceWithoutOwnersDto fileRef) throws ModuleException, FileNotFoundException {
        if (fileRef.getLocation().getUrl().matches(".*regards_.*\\.zip")) {
            return retrieveFromZip(fileRef);
        } else {
            try {
                return (new URL(fileRef.getLocation().getUrl())).openStream();
            } catch (FileNotFoundException e) {
                String errorMessage = String.format("[LOCAL STORAGE PLUGIN] file %s does not exists.",
                                                    fileRef.getLocation().getUrl());
                LOGGER.error(errorMessage, e);
                throw new FileNotFoundException(errorMessage);
            } catch (IOException e) {
                String errorMessage = String.format("[LOCAL STORAGE PLUGIN] file %s is not a valid URL to retrieve.",
                                                    fileRef.getLocation().getUrl());
                LOGGER.error(errorMessage, e);
                throw new ModuleException(errorMessage);
            }
        }
    }

    /**
     * Retrieve a stream of the given file from a ZIP file.<br/>
     * <b>NOTE</b> : The stream and the ZIP access are released when the stream is closed. Callers must call the stream after usage.
     *
     * @param fileRef {@link FileReferenceWithoutOwnersDto} to retrieve
     * @return {@link InputStream}
     * @throws ModuleException if an error occurs while accessing ZIP file or file himself
     */
    private InputStream retrieveFromZip(FileReferenceWithoutOwnersDto fileRef) throws ModuleException {
        Map<String, String> env = new HashMap<>(1);
        env.put(CREATE_ENV_FS, "false");
        String checksum = fileRef.getMetaInfo().getChecksum();
        FileChannel zipFC = null;
        FileLock zipLock = null;
        FileSystem zipFs = null;
        InputStream streamFromZip = null;
        try {
            LOGGER.debug("Attempting to acquire semaphore, available permits : {}",
                         zipAccessSemaphore.availablePermits());
            if (zipAccessSemaphore.tryAcquire(ZIP_ACQUIRE_TIMEOUT, TimeUnit.SECONDS)) {

                LOGGER.debug("Semaphore acquired available permits : {}", zipAccessSemaphore.availablePermits());
                Path zipPath = Paths.get(new URL(fileRef.getLocation().getUrl()).getPath());
                // File channel and File system are not included into try-finally or try-with-resource because if we do this it does not work.
                // Instead, they are closed thanks to RegardsIS
                // Moreover semaphore and lock are released by RegardsIS too
                zipFC = FileChannel.open(zipPath, StandardOpenOption.WRITE, StandardOpenOption.READ); // NOSONAR
                LOGGER.debug("Attempting to get Lock from zip {}", fileRef.getLocation().getUrl());
                zipLock = zipFC.lock();
                LOGGER.debug("Lock acquired from zip {}", fileRef.getLocation().getUrl());
                zipFs = FileSystems.newFileSystem(URI.create(ZIP_PROTOCOL + zipPath.toAbsolutePath()),// NOSONAR
                                                  env); // NOSONAR
                Path pathInZip = zipFs.getPath(checksum);
                streamFromZip = RegardsIS.build(Files.newInputStream(pathInZip),
                                                zipFs,
                                                zipLock,
                                                zipFC,
                                                zipAccessSemaphore);
                return streamFromZip;
            } else {
                String errorMessage = String.format(
                    "[LOCAL STORAGE PLUGIN] Error retrieving file %s (%s) from zip %s. Cause : timeout accessing zip file. Zip file is already locked.",
                    checksum,
                    fileRef.getMetaInfo().getFileName(),
                    fileRef.getLocation().getUrl());
                LOGGER.error(errorMessage);
                throw new ModuleException(errorMessage);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String errorMessage = String.format(
                "[LOCAL STORAGE PLUGIN] Retrieve file %s (%s) from zip %s has been interrupted while acquiring semaphore. Cause : %s.",
                checksum,
                fileRef.getMetaInfo().getFileName(),
                fileRef.getLocation().getUrl(),
                e.getMessage());
            LOGGER.error(errorMessage, e);
            throw new ModuleException(errorMessage);
        } catch (IOException e) {
            String errorMessage = String.format(
                "[LOCAL STORAGE PLUGIN] Error retrieving file %s (%s) from zip %s. Cause : %s",
                checksum,
                fileRef.getMetaInfo().getFileName(),
                fileRef.getLocation().getUrl(),
                e.getMessage());
            try {
                if (zipFs != null) {
                    zipFs.close();
                }
                if (zipLock != null) {
                    zipLock.release();
                }
                if (zipFC != null) {
                    zipFC.close();
                }
                throw new ModuleException(errorMessage);
            } catch (IOException ioE) {
                LOGGER.error("Error while attempting to close the zip {} you might want to reboot the microservice",
                             fileRef.getLocation().getUrl(),
                             e);
                throw new ModuleException(errorMessage);
            } finally {
                zipAccessSemaphore.release();
                LOGGER.debug("Semaphore released");
            }
        }
    }

    @Override
    public boolean allowPhysicalDeletion() {
        return allowPhysicalDeletion;
    }

    @Override
    public boolean isValidUrl(String urlToValidate, Set<String> errors) {
        boolean valid = true;
        try {
            URL url = new URL(urlToValidate);
            if (!url.getProtocol().equals("file")) {
                errors.add(String.format("Invalid url protocol. Expected file bu was %s", url.getProtocol()));
                valid = false;
            }
        } catch (MalformedURLException e) {
            LOGGER.error(e.getMessage(), e);
            errors.add(String.format("Invalid url format %s", e.getMessage()));
            valid = false;
        }
        return valid;
    }

    @Override
    public AbstractStoragePluginConfigurationDto createWorkerStoreConfiguration() {
        return new LocalStorageLocationConfigurationDto(allowPhysicalDeletion,
                                                        baseStorageLocationAsString,
                                                        maxFileSizeForZip,
                                                        maxZipSize);
    }

    private static class RegardsIS extends InputStream {

        private FileLock lock;

        private FileChannel fc;

        private Semaphore semaphore;

        private InputStream source;

        private FileSystem fs;

        public static RegardsIS build(InputStream source,
                                      FileSystem fs,
                                      FileLock lock,
                                      FileChannel fc,
                                      Semaphore semaphore) {
            RegardsIS is = new RegardsIS();
            is.source = source;
            is.fs = fs;
            is.lock = lock;
            is.fc = fc;
            is.semaphore = semaphore;
            return is;
        }

        @Override
        public int read() throws IOException {
            return source.read();
        }

        @Override
        public void close() throws IOException {
            source.close();
            LOGGER.debug("IS closed");
            fs.close();
            LOGGER.debug("fs closed");
            lock.release();
            LOGGER.debug("lock released");
            fc.close();
            LOGGER.debug("fc released");
            semaphore.release();
            LOGGER.debug("Semaphore released");
        }
    }

}
