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
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import fr.cnes.regards.framework.gson.adapters.OffsetDateTimeAdapter;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.locks.service.ILockService;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.utils.file.DownloadUtils;
import fr.cnes.regards.modules.storagelight.domain.database.FileReference;
import fr.cnes.regards.modules.storagelight.domain.database.request.FileCacheRequest;
import fr.cnes.regards.modules.storagelight.domain.database.request.FileDeletionRequest;
import fr.cnes.regards.modules.storagelight.domain.database.request.FileStorageRequest;
import fr.cnes.regards.modules.storagelight.domain.plugin.FileDeletionWorkingSubset;
import fr.cnes.regards.modules.storagelight.domain.plugin.FileRestorationWorkingSubset;
import fr.cnes.regards.modules.storagelight.domain.plugin.FileStorageWorkingSubset;
import fr.cnes.regards.modules.storagelight.domain.plugin.IDeletionProgressManager;
import fr.cnes.regards.modules.storagelight.domain.plugin.IOnlineStorageLocation;
import fr.cnes.regards.modules.storagelight.domain.plugin.IStorageProgressManager;

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

    private static final Semaphore zipAccessSemaphore = new Semaphore(1, true);

    private static final String GET_CURRENT_ZIP_PATH_LOCK_NAME = "getCurrentZipPath#";

    /**
     * Base storage location url
     */
    @PluginParameter(name = BASE_STORAGE_LOCATION_PLUGIN_PARAM_NAME, description = "Base storage location url to use",
            label = "Base storage location url")
    private String baseStorageLocationAsString;

    @PluginParameter(name = LOCAL_STORAGE_DELETE_OPTION, defaultValue = "true",
            label = "Enable physical deletion of files",
            description = "If deletion is allowed, files are physically deleted else files are only removed from references")
    private Boolean allowPhysicalDeletion;

    @PluginParameter(name = LOCAL_STORAGE_MAX_FILE_SIZE_FOR_ZIP, label = "Maximum file size for zip",
            description = "Maximum size, in bytes, for a file to be put in a zip", defaultValue = "10000")
    private Long maxFileSizeForZip;

    @PluginParameter(name = LOCAL_STORAGE_MAX_ZIP_SIZE, label = "Maximum zip size",
            description = "Maximum zip size, in bytes", defaultValue = "500000000")
    private Long maxZipSize;

    @Autowired
    private ILockService lockService;

    @Override
    public Collection<FileStorageWorkingSubset> prepareForStorage(
            Collection<FileStorageRequest> fileReferenceRequests) {
        Collection<FileStorageWorkingSubset> workingSubSets = Lists.newArrayList();
        workingSubSets.add(new FileStorageWorkingSubset(fileReferenceRequests));
        return workingSubSets;
    }

    @Override
    public Collection<FileDeletionWorkingSubset> prepareForDeletion(
            Collection<FileDeletionRequest> fileDeletionRequests) {
        Collection<FileDeletionWorkingSubset> workingSubSets = Lists.newArrayList();
        workingSubSets.add(new FileDeletionWorkingSubset(Sets.newHashSet(fileDeletionRequests)));
        return workingSubSets;
    }

    @Override
    public Collection<FileRestorationWorkingSubset> prepareForRestoration(Collection<FileCacheRequest> requests) {
        Collection<FileRestorationWorkingSubset> workingSubSets = Lists.newArrayList();
        workingSubSets.add(new FileRestorationWorkingSubset(Sets.newHashSet(requests)));
        return workingSubSets;
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
            progressManager.storageFailed(request, failureCause);
            return;
        }
        //check if file is already at the right place or not. Unless we are instructed not to(for updates for example)
        if (Files.exists(fullPathToFile)) {
            Long fileSize = fullPathToFile.toFile().length();
            //if it is, there is nothing to move/copy, we just need to say to the system that the file is stored successfully
            try {
                LOG.debug("File {} already exists, no replacement.", fullPathToFile);
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
                        String.format("Download error for file %s. Cause : ", request.getOriginUrl(), e.getMessage()));
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
                String failureCause = String
                        .format("Storage of StorageDataFile(%s) failed at the following location: %s. Its checksum once stored does not match with expected one",
                                request.getMetaInfo().getChecksum(), fullPathToFile);
                Files.deleteIfExists(fullPathToFile);
                progressManager.storageFailed(request, failureCause);
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

    private void doStoreInZip(IStorageProgressManager progressManager, FileStorageRequest request, File file) {
        Path zipPath = null;
        try {
            zipPath = getStorageLocationForZip(request);
            ;
            lockZip(zipPath);
            // check if file is already in zip
            Map<String, String> env = new HashMap<>(1);
            env.put("create", "true");
            String checksum = request.getMetaInfo().getChecksum();
            boolean downloadOk = false;
            try (FileChannel zipFC = FileChannel.open(zipPath, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
                zipAccessSemaphore.acquire();
                FileLock zipLock = zipFC.lock();
                try (FileSystem zipFs = FileSystems
                        .newFileSystem(URI.create("jar:file:" + zipPath.toAbsolutePath().toString()), env)) {
                    Path pathInZip = zipFs.getPath(request.getMetaInfo().getChecksum());
                    if (Files.exists(pathInZip)) {
                        //if it is, there is nothing to move/copy, we just need to say to the system that the file is stored successfully
                        LOG.debug("File {} already exists in zip, no replacement.", pathInZip);
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
                            String failureCause = String
                                    .format("Storage of StorageDataFile(%s) failed at the following location: %s, in zip: %s. Its checksum once stored does not match with expected one",
                                            checksum, pathInZip, zipPath);
                            Files.deleteIfExists(pathInZip);
                            progressManager.storageFailed(request, failureCause);
                        }
                    }
                } finally {
                    zipLock.release();
                    zipAccessSemaphore.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("Storage into zip has been interrupted while acquiring semaphore.", e);
            }
            if (downloadOk) {
                try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                    Long fileSize = zipFile.getEntry(checksum).getSize();
                    progressManager.storageSucceed(request, zipPath.toUri().toURL(), fileSize);
                }
            }
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
            try {
                file.delete();
            } finally {
                if (zipPath != null) {
                    unlockZip(zipPath);
                }
            }
        }
    }

    // this method is public because of tests

    public Path getStorageLocation(FileStorageRequest request) throws IOException {
        String checksum = request.getMetaInfo().getChecksum();
        Path storageLocation = Paths.get(baseStorageLocationAsString);
        if (request.getStorageSubDirectory() != null) {
            // Storage directory is provider. use it
            storageLocation = storageLocation.resolve(request.getStorageSubDirectory());
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

    // this method is public because of tests
    public Path getStorageLocationForZip(FileStorageRequest request) throws IOException {
        Path storageLocation = Paths.get(baseStorageLocationAsString);
        if (!Strings.isNullOrEmpty(request.getStorageSubDirectory())) {
            // add the sub directory
            storageLocation = storageLocation.resolve(request.getStorageSubDirectory());
        } else {
            // add "zips" to the storage location to get all zips in the same subdirectory
            storageLocation = storageLocation.resolve(ZIP_DIR_NAME);
        }
        Files.createDirectories(storageLocation);
        return getCurrentZipPath(storageLocation);
    }

    private void lockZip(Path zipToLock) throws IOException {
        if (!lockService.waitForlock(GET_CURRENT_ZIP_PATH_LOCK_NAME + zipToLock.toString(), this, 3600, 100)) {
            throw new IOException(
                    "A file should have been put into a zip but we could not ensure that it could be done properly so the process was aborted");
        }
    }

    private void unlockZip(Path zipToLock) {
        lockService.releaseLock(GET_CURRENT_ZIP_PATH_LOCK_NAME + zipToLock.toString(), this);
    }

    private Path getCurrentZipPath(Path storageLocation) throws IOException {
        // To avoid issue with knowing which zip is the right one, lets have a symlink on the current zip
        Path linkPath = storageLocation.resolve(CURRENT_ZIP_NAME);
        if (!Files.isSymbolicLink(linkPath)) {
            // If the link does not exist it means that no zip has been created yet
            // Lets create the first one
            Map<String, String> env = new HashMap<>(1);
            env.put("create", "true");
            Path zipPath = storageLocation.resolve("regards_"
                    + OffsetDateTime.now().format(OffsetDateTimeAdapter.ISO_DATE_TIME_UTC) + ".zip");
            try (FileSystem zipFs = FileSystems
                    .newFileSystem(URI.create("jar:file:" + zipPath.toAbsolutePath().toString()), env)) {
                // now that zip has been created, lets create the link.
                Files.createSymbolicLink(linkPath, zipPath);
            }
            return zipPath;
        }
        if (Files.isSymbolicLink(linkPath)) {
            // if the link does exist, it means that a zip has already been created
            // in this case, we have to check its size to be sure we can still add files to it
            Path targetPath = Files.readSymbolicLink(linkPath);
            if (targetPath.toFile().length() > maxZipSize) {
                // we have to create a new one
                try (FileChannel targetFC = FileChannel.open(targetPath, StandardOpenOption.WRITE,
                                                             StandardOpenOption.READ)) {
                    FileLock targetLock = targetFC.lock();
                    try {
                        // create a new zip and make the link points to the new zip
                        Map<String, String> env = new HashMap<>(1);
                        env.put("create", "true");
                        Path newZipPath = storageLocation.resolve("regards_"
                                + OffsetDateTime.now().format(OffsetDateTimeAdapter.ISO_DATE_TIME_UTC) + ".zip");
                        try (FileSystem zipFs = FileSystems
                                .newFileSystem(URI.create("jar:file:" + newZipPath.toAbsolutePath().toString()), env)) {
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
                        LOG.debug("File {} deleted", location.toAbsolutePath().toString());
                    } else {
                        LOG.debug("File {} not deleted as it does not exists", location.toAbsolutePath().toString());
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
        env.put("create", "false");
        String checksum = request.getFileReference().getMetaInfo().getChecksum();
        Path zipPath = null;
        try {
            zipPath = Paths.get(new URL(request.getFileReference().getLocation().getUrl()).getPath());
            lockZip(zipPath);
            try (FileChannel zipFC = FileChannel.open(zipPath, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
                zipAccessSemaphore.acquire();
                FileLock zipLock = zipFC.lock();
                try {
                    try (FileSystem zipFs = FileSystems
                            .newFileSystem(URI.create("jar:file:" + zipPath.toAbsolutePath().toString()), env)) {
                        Path pathInZip = zipFs.getPath(checksum);
                        Files.deleteIfExists(pathInZip);
                        progressManager.deletionSucceed(request);
                    }
                    try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                        if (!zipFile.entries().hasMoreElements()) {
                            Files.deleteIfExists(zipPath);
                            // Check if it is the current zip file. If it is, delete the symlink
                            Path linkPath = zipPath.getParent().resolve(CURRENT_ZIP_NAME);
                            if (Files.exists(linkPath) && zipPath.equals(linkPath.toRealPath())) {
                                Files.delete(linkPath);
                            }
                        }
                    }
                } finally {
                    zipLock.release();
                    zipAccessSemaphore.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("Deletion from zip has been interrupted while acquiring semaphore.", e);
            } finally {
                if (zipPath != null) {
                    unlockZip(zipPath);
                }
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
        env.put("create", "false");
        String checksum = fileRef.getMetaInfo().getChecksum();
        Path zipPath = Paths.get(new URL(fileRef.getLocation().getUrl()).getPath());
        // File channel and File system are not included into try-finally or try-with-resource because if we do this it does not work.
        // Instead, they are closed thanks to RegardsIS
        // Moreover semaphore and lock are released by RegardsIS too
        FileChannel zipFC = FileChannel.open(zipPath, StandardOpenOption.WRITE, StandardOpenOption.READ);
        try {
            zipAccessSemaphore.acquire();
            FileLock zipLock = zipFC.lock();
            FileSystem zipFs = FileSystems.newFileSystem(URI.create("jar:file:" + zipPath.toAbsolutePath().toString()),
                                                         env);
            Path pathInZip = zipFs.getPath(checksum);
            return new RegardsIS(Files.newInputStream(pathInZip), zipFs, zipLock, zipFC, zipAccessSemaphore);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Deletion from zip has been interrupted while acquiring semaphore.", e);
            throw new IOException(e);
        }
    }

    @Override
    public boolean allowPhysicalDeletion() {
        return allowPhysicalDeletion;
    }

    private class RegardsIS extends InputStream {

        private final FileLock lock;

        private final FileChannel fc;

        private final Semaphore semaphore;

        private final InputStream source;

        private final FileSystem fs;

        public RegardsIS(InputStream source, FileSystem fs, FileLock lock, FileChannel fc, Semaphore semaphore) {
            this.source = source;
            this.fs = fs;
            this.lock = lock;
            this.fc = fc;
            this.semaphore = semaphore;
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
