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
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import fr.cnes.regards.framework.gson.adapters.OffsetDateTimeAdapter;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.utils.RsRuntimeException;
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

    /**
     * Base storage location url
     */
    @PluginParameter(name = BASE_STORAGE_LOCATION_PLUGIN_PARAM_NAME, description = "Base storage location url to use",
            label = "Base storage location url")
    private String baseStorageLocationAsString;

    /**
     * can this data storage delete files or not?
     */
    @PluginParameter(name = LOCAL_STORAGE_DELETE_OPTION, defaultValue = "true",
            label = "Enable physical deletion of files",
            description = "If deletion is allowed, files are physically deleted else files are only removed from references")
    private Boolean allowPhysicalDeletion;

    /**
     * Total space, in byte, this data storage is allowed to use
     */
    @PluginParameter(name = LOCAL_STORAGE_TOTAL_SPACE,
            description = "Total space, in byte, this data storage is allowed to use", label = "Total allocated space")
    private Long totalSpace;

    @PluginParameter(name = LOCAL_STORAGE_MAX_FILE_SIZE_FOR_ZIP, label = "Maximum file size for zip",
            description = "Maximum size, in bytes, for a file to be put in a zip", defaultValue = "10000")
    private Long maxFileSizeForZip;

    @PluginParameter(name = LOCAL_STORAGE_MAX_ZIP_SIZE, label = "Maximum zip size",
            description = "Maximum zip size, in bytes", defaultValue = "5000000000")
    private Long maxZipSize;

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
        if (request.getMetaInfo().getFileSize() < maxFileSizeForZip) {
            doStoreInZip(progressManager, request);
        } else {
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
                boolean downloadOk = DownloadUtils.downloadAndCheckChecksum(sourceUrl, fullPathToFile,
                                                                            request.getMetaInfo().getAlgorithm(),
                                                                            request.getMetaInfo().getChecksum());
                if (downloadOk) {
                    File file = fullPathToFile.toFile();
                    if (file.canWrite()) {
                        file.setReadOnly();
                    }
                    Long fileSize = file.length();
                    progressManager.storageSucceed(request, fullPathToFile.toUri().toURL(), fileSize);
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
            }
        }
    }

    private void doStoreInZip(IStorageProgressManager progressManager, FileStorageRequest request) {
        Path zipPath;
        try {
            zipPath = getStorageLocationForZip(request);
        } catch (IOException ioe) {
            String failureCause = String
                    .format("Storage of StorageDataFile(%s) failed due to the following IOException: %s",
                            request.getMetaInfo().getChecksum(), ioe.toString());
            LOG.error(failureCause, ioe);
            progressManager.storageFailed(request, failureCause);
            return;
        }
        // check if file is already in zip
        Map<String, String> env = new HashMap<>(1);
        env.put("create", "true");
        String checksum = request.getMetaInfo().getChecksum();
        boolean downloadOk = false;
        try (FileChannel zipFC = FileChannel.open(zipPath, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
            FileLock zipLock = zipFC.lock();
            try (FileSystem zipFs = FileSystems
                    .newFileSystem(URI.create("jar:file:" + zipPath.toAbsolutePath().toString()), env)) {
                Path pathInZip = zipFs.getPath(request.getMetaInfo().getChecksum());
                if (Files.exists(pathInZip)) {
                    //if it is, there is nothing to move/copy, we just need to say to the system that the file is stored successfully
                    try {
                        LOG.debug("File {} already exists in zip, no replacement.", pathInZip);
                        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                            Long fileSize = zipFile.getEntry(checksum).getSize();
                            progressManager.storageSucceed(request, zipPath.toUri().toURL(), fileSize);
                        }
                    } catch (MalformedURLException e) {
                        LOG.error(e.getMessage(), e);
                        String failureCause = String.format("Invalid URL creation for file %s.", zipPath);
                        progressManager.storageFailed(request, failureCause);
                    }
                    return;
                }
                // add the file into the zip
                try {
                    URL sourceUrl = new URL(request.getOriginUrl());
                    downloadOk = DownloadUtils.downloadAndCheckChecksum(sourceUrl, pathInZip,
                                                                        request.getMetaInfo().getAlgorithm(), checksum);
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
                } catch (NoSuchAlgorithmException e) {
                    LOG.error(e.getMessage(), e);
                    String failureCause = String
                            .format("Invalid checksum algorithm %s. Unable to determine if the file is well formed.",
                                    checksum);
                    progressManager.storageFailed(request, failureCause);
                } catch (IOException ioe) {
                    String failureCause = String
                            .format("Storage of StorageDataFile(%s) failed due to the following IOException: %s",
                                    checksum, ioe.toString());
                    LOG.error(failureCause, ioe);
                    Files.deleteIfExists(pathInZip);
                    progressManager.storageFailed(request, failureCause);
                }
            } finally {
                zipLock.release();
            }
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            String failureCause = String
                    .format("Storage of StorageDataFile(%s) failed because we could not create access the zip(%s) in which it should have been stored",
                            checksum, zipPath);
            progressManager.storageFailed(request, failureCause);
        }
        if (downloadOk) {
            try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                Long fileSize = zipFile.getEntry(checksum).getSize();
                progressManager.storageSucceed(request, zipPath.toUri().toURL(), fileSize);
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
                // storage did not fail so lets just put the given size even if it's not the right one
                try {
                    progressManager.storageSucceed(request, zipPath.toUri().toURL(),
                                                   request.getMetaInfo().getFileSize());
                } catch (MalformedURLException e1) {
                    //this error cannot happen lets just log it and rethrow it
                    LOG.error(e1.getMessage(), e1);
                    throw new RsRuntimeException(e1);
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
            int idx = 0;
            int subFolders = 0;
            String subDir = "";
            String fileChecksum = request.getMetaInfo().getChecksum();
            while ((idx < fileChecksum.length()) && (subFolders < 5)) {
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

    private Path getCurrentZipPath(Path storageLocation) throws IOException {
        // TODO: lock Service to avoid this method being called by multiple thread at the same time
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
                            Files.createLink(linkPath, newZipPath);
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
                return;
            }
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

    private void deleteFromZip(FileDeletionRequest request, IDeletionProgressManager progressManager) {
        Map<String, String> env = new HashMap<>(1);
        env.put("create", "false");
        String checksum = request.getFileReference().getMetaInfo().getChecksum();
        try {
            Path zipPath = Paths.get(new URL(request.getFileReference().getLocation().getUrl()).getPath());
            try (FileChannel zipFC = FileChannel.open(zipPath, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
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
                        }
                    }
                } finally {
                    zipLock.release();
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
        if (fileRef.getLocation().getUrl().matches("regards_.*\\.zip")) {
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
        try (FileSystem zipFs = FileSystems.newFileSystem(URI.create("jar:file:" + zipPath.toAbsolutePath().toString()),
                                                          env)) {
            Path pathInZip = zipFs.getPath(checksum);
            return Files.newInputStream(pathInZip);
        }
    }

    @Override
    public boolean allowPhysicalDeletion() {
        return allowPhysicalDeletion;
    }
}
