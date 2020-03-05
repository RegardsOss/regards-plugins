/*
 * Copyright 2017-2019 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.storage.plugin.local;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import fr.cnes.regards.framework.gson.adapters.OffsetDateTimeAdapter;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.utils.file.DownloadUtils;
import fr.cnes.regards.modules.storage.domain.database.FileReference;
import fr.cnes.regards.modules.storage.domain.database.request.FileCacheRequest;
import fr.cnes.regards.modules.storage.domain.database.request.FileDeletionRequest;
import fr.cnes.regards.modules.storage.domain.database.request.FileStorageRequest;
import fr.cnes.regards.modules.storage.domain.plugin.FileDeletionWorkingSubset;
import fr.cnes.regards.modules.storage.domain.plugin.FileRestorationWorkingSubset;
import fr.cnes.regards.modules.storage.domain.plugin.FileStorageWorkingSubset;
import fr.cnes.regards.modules.storage.domain.plugin.IDeletionProgressManager;
import fr.cnes.regards.modules.storage.domain.plugin.IOnlineStorageLocation;
import fr.cnes.regards.modules.storage.domain.plugin.IStorageProgressManager;
import fr.cnes.regards.modules.storage.domain.plugin.PreparationResponse;

/**
 * @author Sylvain Vissiere-Guerinet
 *
 */
@Plugin(author = "REGARDS Team", description = "Plugin handling the storage on local file system", id = "Local",
        version = "1.0", contact = "regards@c-s.fr", license = "GPLv3", owner = "CNES",
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

    /**
     * Class logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(LocalDataStorage.class);

    /**
     * ZIP directory name which is only used when no storage sub directory is specified
     */
    private static final String ZIP_DIR_NAME = "zips";

    private static final String CURRENT_ZIP_NAME = "current";

    private static final Semaphore ZIP_ACCESS_SEMAPHORE = new Semaphore(1, true);

    private static final Integer MAX_FILE_IN_ZIP = 1000;

    private static final String CREATE_ENV_FS = "create";

    private static final String ZIP_PROTOCOL = "jar:file:";

    /**
     * Base storage location url
     */
    @PluginParameter(name = BASE_STORAGE_LOCATION_PLUGIN_PARAM_NAME,
            description = "Root directory where to store new files on this location", label = "Root directory")
    private String baseStorageLocationAsString;

    @PluginParameter(name = LOCAL_STORAGE_DELETE_OPTION, defaultValue = "false",
            label = "Enable physical deletion of files",
            description = "If deletion is allowed, files are physically deleted else files are only removed from references")
    private Boolean allowPhysicalDeletion;

    @PluginParameter(name = LOCAL_STORAGE_MAX_FILE_SIZE_FOR_ZIP, label = "Files maximum size for zips (octets)",
            description = "When storing a new file in this location, if the file size is less than this value, so the file is stored with other \\\"small files\\\" in a zip archive. The size is in octets.",
            defaultValue = "10000")
    private Long maxFileSizeForZip;

    @PluginParameter(name = LOCAL_STORAGE_MAX_ZIP_SIZE, label = "Maximum zip size (octets)",
            description = "When storing a new file in this location, \"small\" files are stored in a zip archive which maximum size is configurable thanks this property. The size is in octets.",
            defaultValue = "500000000")
    private Long maxZipSize;

    @Override
    public PreparationResponse<FileStorageWorkingSubset, FileStorageRequest> prepareForStorage(
            Collection<FileStorageRequest> fileReferenceRequests) {
        Collection<FileStorageWorkingSubset> workingSubSets = Lists.newArrayList();
        workingSubSets.add(new FileStorageWorkingSubset(fileReferenceRequests));
        return PreparationResponse.build(workingSubSets, Maps.newHashMap());
    }

    @Override
    public PreparationResponse<FileDeletionWorkingSubset, FileDeletionRequest> prepareForDeletion(
            Collection<FileDeletionRequest> fileDeletionRequests) {
        Collection<FileDeletionWorkingSubset> workingSubSets = Lists.newArrayList();
        workingSubSets.add(new FileDeletionWorkingSubset(Sets.newHashSet(fileDeletionRequests)));
        return PreparationResponse.build(workingSubSets, Maps.newHashMap());
    }

    @Override
    public PreparationResponse<FileRestorationWorkingSubset, FileCacheRequest> prepareForRestoration(
            Collection<FileCacheRequest> requests) {
        Collection<FileRestorationWorkingSubset> workingSubSets = Lists.newArrayList();
        workingSubSets.add(new FileRestorationWorkingSubset(Sets.newHashSet(requests)));
        return PreparationResponse.build(workingSubSets, Maps.newHashMap());
    }

    @Override
    public void store(FileStorageWorkingSubset workingSubset, IStorageProgressManager progressManager) {
        workingSubset.getFileReferenceRequests().forEach(data -> doStore(progressManager, data));
    }

    private void doStore(IStorageProgressManager progressManager, FileStorageRequest request) {
        Path fullPathToFile;
        try {
            fullPathToFile = getStorageLocation(request);
        } catch (IOException ioe) {
            String failureCause = String
                    .format("Storage of StorageDataFile(%s) failed due to the following IOException: %s",
                            request.getMetaInfo().getChecksum(), ioe.toString());
            LOG.error(failureCause, ioe);
            progressManager.storageFailed(request, ioe.getMessage());
            return;
        }
        //check if file is already at the right place or not. Unless we are instructed not to(for updates for example)
        if (Files.exists(fullPathToFile)) {
            Long fileSize = fullPathToFile.toFile().length();
            //if it is, there is nothing to move/copy, we just need to say to the system that the file is stored successfully
            try {
                LOG.debug("[LOCAL STORAGE PLUGIN] File {} already exists, no replacement.", fullPathToFile);
                progressManager.storageSucceed(request, fullPathToFile.toUri().toURL(), fileSize);
            } catch (MalformedURLException e) {
                LOG.error(e.getMessage(), e);
                String failureCause = String.format("Invalid URL creation for file %s.", fullPathToFile);
                progressManager.storageFailed(request, failureCause);
            }
            return;
        }
        try {
            URL sourceUrl = new URL(request.getOriginUrl());
            boolean downloadOk = false;
            try {
                downloadOk = DownloadUtils.downloadAndCheckChecksum(sourceUrl, fullPathToFile,
                                                                    request.getMetaInfo().getAlgorithm(),
                                                                    request.getMetaInfo().getChecksum());
            } catch (IOException e) {
                throw new ModuleException(
                        String.format("Download error for file %s. Cause : %s", request.getOriginUrl(), e.getMessage()),
                        e);
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
            LOG.error(e.getMessage(), e);
            String failureCause = String
                    .format("Invalid checksum algorithm %s. Unable to determine if the file is well formed.",
                            request.getMetaInfo().getChecksum());
            progressManager.storageFailed(request, failureCause);
        } catch (IOException ioe) {
            String failureCause = String
                    .format("Storage of StorageDataFile(%s) failed due to the following IOException: %s",
                            request.getMetaInfo().getChecksum(), ioe.toString());
            LOG.error(failureCause, ioe);
            fullPathToFile.toFile().delete();
            progressManager.storageFailed(request, failureCause);
        } catch (ModuleException e) {
            LOG.error(e.getMessage(), e);
            fullPathToFile.toFile().delete();
            progressManager.storageFailed(request, e.getMessage());
        }
    }

    private void doStoreInZip(IStorageProgressManager progressManager, FileStorageRequest request, File file)
            throws IOException {
        long start = System.currentTimeMillis();
        Path zipDirPath = getStorageLocationForZip(request);
        try {
            LOG.trace("[LOCAL STORAGE PLUGIN] Store in zip ....");
            ZIP_ACCESS_SEMAPHORE.acquire();
            Path zipPath = getCurrentZipPath(zipDirPath);
            // check if file is already in zip
            Map<String, String> env = new HashMap<>(1);
            env.put(CREATE_ENV_FS, "true");
            String checksum = request.getMetaInfo().getChecksum();
            boolean downloadOk = false;
            try (FileChannel zipFC = FileChannel.open(zipPath, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
                FileLock zipLock = zipFC.lock();
                try (FileSystem zipFs = FileSystems
                        .newFileSystem(URI.create(ZIP_PROTOCOL + zipPath.toAbsolutePath().toString()), env)) {
                    Path pathInZip = zipFs.getPath(request.getMetaInfo().getChecksum());
                    if (Files.exists(pathInZip)) {
                        //if it is, there is nothing to move/copy, we just need to say to the system that the file is stored successfully
                        LOG.debug("[LOCAL STORAGE PLUGIN] File {} already exists in zip, no replacement.", pathInZip);
                        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                            Long fileSize = zipFile.getEntry(checksum).getSize();
                            progressManager.storageSucceed(request, zipPath.toUri().toURL(), fileSize);
                        }
                    } else {
                        // add the file into the zip
                        URL sourceUrl = new URL("file", null, file.getPath());
                        downloadOk = DownloadUtils.downloadAndCheckChecksum(sourceUrl, pathInZip,
                                                                            request.getMetaInfo().getAlgorithm(),
                                                                            checksum);
                        // download issues are handled right here
                        // while download success has to be handle after the zip file system has been closed
                        // for zip entries to be detected correctly
                        if (!downloadOk) {
                            String failureCause = String.format("Checksum does not match expected one", checksum,
                                                                pathInZip, zipPath);
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
            LOG.error("[LOCAL STORAGE PLUGIN] Storage into zip has been interrupted while acquiring semaphore.", e);
        } catch (MalformedURLException | NoSuchAlgorithmException e) {
            LOG.error(e.getMessage(), e);
            String failureCause = String.format("Invalid URL creation for file. %s", e.getMessage());
            progressManager.storageFailed(request, failureCause);
        } catch (IOException ioe) {
            String failureCause = String
                    .format("Storage of StorageDataFile(%s) failed due to the following IOException: %s",
                            request.getMetaInfo().getChecksum(), ioe.toString());
            LOG.error(failureCause, ioe);
            progressManager.storageFailed(request, failureCause);
        } finally {
            LOG.trace("[LOCAL STORAGE PLUGIN] Store in zip done in {}ms", System.currentTimeMillis() - start);
            ZIP_ACCESS_SEMAPHORE.release();
            file.delete();
        }
    }

    // this method is public because of tests
    public Path getStorageLocation(FileStorageRequest request) throws IOException {
        String checksum = request.getMetaInfo().getChecksum();
        Path storageLocation = Paths.get(baseStorageLocationAsString);
        if ((request.getStorageSubDirectory() != null) && !request.getStorageSubDirectory().isEmpty()) {
            // Storage directory is provided. use it
            storageLocation = Paths.get(baseStorageLocationAsString, request.getStorageSubDirectory());
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

    public Path getStorageLocationForZip(FileStorageRequest request) throws IOException {
        Path storageLocation = Paths.get(baseStorageLocationAsString);
        if (!Strings.isNullOrEmpty(request.getStorageSubDirectory())) {
            // add the sub directory
            storageLocation = Paths.get(baseStorageLocationAsString, request.getStorageSubDirectory());
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
            LOG.warn("[LOCAL STORAGE PLUGIN] Deleting dead link {}", linkPath.toString());
            Files.deleteIfExists(linkPath);
        }
        if (!Files.isSymbolicLink(linkPath)) {
            // If the link does not exist it means that no zip has been created yet
            // Lets create the first one
            Map<String, String> env = new HashMap<>(1);
            env.put(CREATE_ENV_FS, "true");
            Path zipPath = storageLocation.resolve("regards_"
                    + OffsetDateTime.now().format(OffsetDateTimeAdapter.ISO_DATE_TIME_UTC) + ".zip");
            try (FileSystem zipFs = FileSystems
                    .newFileSystem(URI.create(ZIP_PROTOCOL + zipPath.toAbsolutePath().toString()), env)) {
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
                    try (FileChannel targetFC = FileChannel.open(targetPath, StandardOpenOption.WRITE,
                                                                 StandardOpenOption.READ)) {
                        FileLock targetLock = targetFC.lock();
                        try {
                            // create a new zip and make the link points to the new zip
                            Map<String, String> env = new HashMap<>(1);
                            env.put(CREATE_ENV_FS, "true");
                            Path newZipPath = storageLocation.resolve("regards_"
                                    + OffsetDateTime.now().format(OffsetDateTimeAdapter.ISO_DATE_TIME_UTC) + ".zip");
                            try (FileSystem zipFs = FileSystems
                                    .newFileSystem(URI.create(ZIP_PROTOCOL + newZipPath.toAbsolutePath().toString()),
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
        for (FileDeletionRequest request : workingSubset.getFileDeletionRequests()) {
            if (request.getFileReference().getLocation().getUrl().matches(".*regards_.*\\.zip")) {
                deleteFromZip(request, progressManager);
            } else {
                try {
                    URL url = new URL(request.getFileReference().getLocation().getUrl());
                    Path location = Paths.get(url.getPath());
                    if (Files.deleteIfExists(location)) {
                        LOG.debug("[LOCAL STORAGE PLUGIN] File {} deleted", location.toAbsolutePath().toString());
                    } else {
                        LOG.debug("[LOCAL STORAGE PLUGIN] File {} not deleted as it does not exists",
                                  location.toAbsolutePath().toString());
                    }
                    progressManager.deletionSucceed(request);
                } catch (IOException ioe) {
                    String failureCause = String
                            .format("Deletion of StorageDataFile(%s) failed due to the following IOException: %s",
                                    request.getFileReference().getMetaInfo().getChecksum(), ioe.getMessage());
                    LOG.error(failureCause, ioe);
                    progressManager.deletionFailed(request, failureCause);
                }
            }
        }
    }

    private void deleteFromZip(FileDeletionRequest request, IDeletionProgressManager progressManager) {
        Map<String, String> env = new HashMap<>(1);
        env.put(CREATE_ENV_FS, "false");
        String checksum = request.getFileReference().getMetaInfo().getChecksum();
        try {
            Path zipPath = Paths.get(new URL(request.getFileReference().getLocation().getUrl()).getPath());
            if (Files.exists(zipPath) && Files.isReadable(zipPath)) {
                try (FileChannel zipFC = FileChannel.open(zipPath, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
                    ZIP_ACCESS_SEMAPHORE.acquire();
                    FileLock zipLock = zipFC.lock();
                    try {
                        try (FileSystem zipFs = FileSystems
                                .newFileSystem(URI.create(ZIP_PROTOCOL + zipPath.toAbsolutePath().toString()), env)) {
                            Path pathInZip = zipFs.getPath(checksum);
                            Files.deleteIfExists(pathInZip);
                            progressManager.deletionSucceed(request);
                        }
                        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                            if (!zipFile.entries().hasMoreElements()) {
                                Path linkPath = zipPath.getParent().resolve(CURRENT_ZIP_NAME);
                                // Check if it is the current zip file. If it is, delete the symlink
                                if (Files.isSymbolicLink(linkPath)
                                        && zipPath.equals(Files.readSymbolicLink(linkPath))) {
                                    Files.delete(linkPath);
                                }
                                Files.deleteIfExists(zipPath);
                            }
                        }
                    } finally {
                        zipLock.release();
                        ZIP_ACCESS_SEMAPHORE.release();
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.error("[LOCAL STORAGE PLUGIN] Deletion from zip has been interrupted while acquiring semaphore.",
                              e);
                }
            } else {
                LOG.debug("[LOCAL STORAGE PLUGIN] File to delete from a zip file {} but zip file does not exists.",
                          zipPath.toString());
                progressManager.deletionSucceed(request);
            }
        } catch (IOException e) {
            String failureCause = String
                    .format("Deletion of StorageDataFile(%s) failed due to the following IOException: %s",
                            request.getFileReference().getMetaInfo().getChecksum(), e.getMessage());
            LOG.error(failureCause, e);
            progressManager.deletionFailed(request, failureCause);
        }
    }

    @Override
    public InputStream retrieve(FileReference fileRef) throws IOException {
        if (fileRef.getLocation().getUrl().matches(".*regards_.*\\.zip")) {
            return retrieveFromZip(fileRef);
        } else {
            return (new URL(fileRef.getLocation().getUrl())).openStream();
        }
    }

    private InputStream retrieveFromZip(FileReference fileRef) throws IOException {
        Map<String, String> env = new HashMap<>(1);
        env.put(CREATE_ENV_FS, "false");
        String checksum = fileRef.getMetaInfo().getChecksum();
        Path zipPath = Paths.get(new URL(fileRef.getLocation().getUrl()).getPath());
        // File channel and File system are not included into try-finally or try-with-resource because if we do this it does not work.
        // Instead, they are closed thanks to RegardsIS
        // Moreover semaphore and lock are released by RegardsIS too
        FileChannel zipFC = FileChannel.open(zipPath, StandardOpenOption.WRITE, StandardOpenOption.READ);
        try {
            ZIP_ACCESS_SEMAPHORE.acquire();
            FileLock zipLock = zipFC.lock();
            FileSystem zipFs = FileSystems.newFileSystem(URI.create(ZIP_PROTOCOL + zipPath.toAbsolutePath().toString()),
                                                         env);
            Path pathInZip = zipFs.getPath(checksum);
            return RegardsIS.build(Files.newInputStream(pathInZip), zipFs, zipLock, zipFC, ZIP_ACCESS_SEMAPHORE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("[LOCAL STORAGE PLUGIN] Deletion from zip has been interrupted while acquiring semaphore.", e);
            throw new IOException(e);
        }
    }

    @Override
    public boolean allowPhysicalDeletion() {
        return allowPhysicalDeletion;
    }

    private static class RegardsIS extends InputStream {

        private FileLock lock;

        private FileChannel fc;

        private Semaphore semaphore;

        private InputStream source;

        private FileSystem fs;

        public static RegardsIS build(InputStream source, FileSystem fs, FileLock lock, FileChannel fc,
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
            lock.release();
            semaphore.release();
            fs.close();
            fc.close();
        }
    }
}
