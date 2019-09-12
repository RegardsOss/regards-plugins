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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
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
import fr.cnes.regards.modules.storagelight.domain.plugin.PluginConfUpdatable;

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

    /**
     * Class logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(LocalDataStorage.class);

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
        workingSubset.getFileReferenceRequests().stream().forEach(data -> {
            doStore(progressManager, data);
        });
    }

    private void doStore(IStorageProgressManager progressManager, FileStorageRequest request) {
        String fullPathToFile;
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
        if (Files.exists(Paths.get(fullPathToFile))) {
            Long fileSize = Paths.get(fullPathToFile).toFile().length();
            //if it is, there is nothing to move/copy, we just need to say to the system that the file is stored successfully
            try {
                LOG.debug("File {} already exists, no replacement.", fullPathToFile);
                progressManager.storageSucceed(request, new URL("file", "", fullPathToFile), fileSize);
            } catch (MalformedURLException e) {
                LOG.error(e.getMessage(), e);
                String failureCause = String.format("Invalid URL creation for file {}.", fullPathToFile);
                progressManager.storageFailed(request, failureCause);
            }
            return;
        }

        try {
            URL sourceUrl = new URL(request.getOriginUrl());
            boolean downloadOk = DownloadUtils.downloadAndCheckChecksum(sourceUrl, Paths.get(fullPathToFile),
                                                                        request.getMetaInfo().getAlgorithm(),
                                                                        request.getMetaInfo().getChecksum());
            if (!downloadOk) {
                String failureCause = String
                        .format("Storage of StorageDataFile(%s) failed at the following location: %s. Its checksum once stored does not match with expected one",
                                request.getMetaInfo().getChecksum(), fullPathToFile);
                Files.deleteIfExists(Paths.get(fullPathToFile));
                progressManager.storageFailed(request, failureCause);
            } else {
                File file = Paths.get(fullPathToFile).toFile();
                if (file.canWrite()) {
                    file.setReadOnly();
                }
                Long fileSize = file.length();
                progressManager.storageSucceed(request, new URL("file", "", fullPathToFile), fileSize);
            }
        } catch (NoSuchAlgorithmException e) {
            LOG.error(e.getMessage(), e);
            String failureCause = String
                    .format("Invalid checksum algorithm {}. Unable to determine if the file is well formed.",
                            request.getMetaInfo().getChecksum());
            progressManager.storageFailed(request, failureCause);
        } catch (IOException ioe) {
            String failureCause = String
                    .format("Storage of StorageDataFile(%s) failed due to the following IOException: %s",
                            request.getMetaInfo().getChecksum(), ioe.toString());
            LOG.error(failureCause, ioe);
            Paths.get(fullPathToFile).toFile().delete();
            progressManager.storageFailed(request, failureCause);
        }
    }

    public String getStorageLocation(FileStorageRequest request) throws IOException {
        String checksum = request.getMetaInfo().getChecksum();
        String storageLocation = Paths.get(baseStorageLocationAsString).toString();
        if (request.getStorageSubDirectory() != null) {
            // Storage directory is provider. use it
            storageLocation = storageLocation + request.getStorageSubDirectory();
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
            storageLocation = Paths.get(storageLocation, subDir).toString();
        }
        if (Files.notExists(Paths.get(storageLocation))) {
            Files.createDirectories(Paths.get(storageLocation));
        }
        // files are stored with the checksum as their name and their extension is based on the url, first '.' after the last '/' of the url
        return storageLocation + "/" + checksum;
    }

    @Override
    public void delete(FileDeletionWorkingSubset workingSubset, IDeletionProgressManager progressManager) {
        for (FileDeletionRequest request : workingSubset.getFileDeletionRequests()) {
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

    @Override
    public InputStream retrieve(FileReference fileRef) throws IOException {
        return (new URL(fileRef.getLocation().getUrl())).openStream();
    }

    @Override
    public PluginConfUpdatable allowConfigurationUpdate(PluginConfiguration newConfiguration,
            PluginConfiguration currentConfiguration, boolean filesAlreadyStored) {
        return PluginConfUpdatable.allowUpdate();
    }

    @Override
    public boolean allowPhysicalDeletion() {
        return allowPhysicalDeletion;
    }

    @Override
    public Long getTotalSpaceInMo() {
        return totalSpace;
    }
}
