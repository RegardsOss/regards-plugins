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
package fr.cnes.regards.modules.storage.plugin.local;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.IPluginParam;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.storage.domain.database.FileLocation;
import fr.cnes.regards.modules.storage.domain.database.FileReference;
import fr.cnes.regards.modules.storage.domain.database.FileReferenceMetaInfo;
import fr.cnes.regards.modules.storage.domain.database.request.FileDeletionRequest;
import fr.cnes.regards.modules.storage.domain.database.request.FileStorageRequest;
import fr.cnes.regards.modules.storage.domain.plugin.FileDeletionWorkingSubset;
import fr.cnes.regards.modules.storage.domain.plugin.FileStorageWorkingSubset;
import fr.cnes.regards.modules.storage.domain.plugin.IDeletionProgressManager;
import fr.cnes.regards.modules.storage.domain.plugin.IStorageProgressManager;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;

/**
 * @author sbinda
 */
public class LocalDataStorageTest {

    private final IStorageProgressManager storageProgress = Mockito.mock(IStorageProgressManager.class);

    private final IDeletionProgressManager deletionProgress = Mockito.mock(IDeletionProgressManager.class);

    private final String baseStorageLocation = "target/local-storage";

    private LocalDataStorage plugin;

    @Before
    public void init()
        throws NotAvailablePluginConfigurationException, IOException, NoSuchFieldException, IllegalAccessException {
        PluginUtils.setup();
        Set<IPluginParam> params = Sets.newHashSet();
        params.add(IPluginParam.build(LocalDataStorage.BASE_STORAGE_LOCATION_PLUGIN_PARAM_NAME, baseStorageLocation));
        params.add(IPluginParam.build(LocalDataStorage.LOCAL_STORAGE_DELETE_OPTION, true));
        params.add(IPluginParam.build(LocalDataStorage.LOCAL_STORAGE_TOTAL_SPACE, 10_000_000L));
        plugin = PluginUtils.getPlugin(PluginConfiguration.build(LocalDataStorage.class, null, params),
                                       Maps.newHashMap());

        Mockito.reset(storageProgress);

        if (Files.exists(Paths.get("target/local-storage"))) {
            Files.walk(Paths.get("target/local-storage")).forEach(d -> {
                d.toFile().setExecutable(true);
                d.toFile().setWritable(true);
                d.toFile().setReadable(true);
            });
            FileUtils.deleteDirectory(Paths.get("target/local-storage").toFile());
        }
    }

    @After
    public void after() throws IOException {
        Files.walk(Paths.get(baseStorageLocation)).forEach(p -> p.toFile().delete());
    }

    @Test
    public void store() throws IOException {
        Set<FileStorageRequest> files = Sets.newHashSet();
        Path testFilePath = Paths.get("src", "test", "resources", "file.test");

        FileStorageRequest storageRequest = new FileStorageRequest("owner",
                                                                   new FileReferenceMetaInfo(
                                                                       "edc900745c5d15d773fbcdc0b376f00c",
                                                                       "MD5",
                                                                       "file.name",
                                                                       null,
                                                                       MediaType.APPLICATION_OCTET_STREAM),
                                                                   testFilePath.toUri().toString(),
                                                                   "localStorage",
                                                                   Optional.empty(),
                                                                   "group",
                                                                   "TEST",
                                                                   "session-001");

        files.add(storageRequest);
        FileStorageWorkingSubset ws = new FileStorageWorkingSubset(files);
        Mockito.verify(storageProgress, Mockito.never()).storageSucceed(Mockito.any(), Mockito.any(), Mockito.any());
        plugin.store(ws, storageProgress);
        Mockito.verify(storageProgress, Mockito.times(1))
               .storageSucceed(Mockito.eq(storageRequest), Mockito.any(), Mockito.any());
        Assert.assertTrue("", Files.exists(plugin.getStorageLocationForZip(storageRequest)));
    }

    @Test
    public void store_file_exists() throws IOException {
        Set<FileStorageRequest> files = Sets.newHashSet();
        Path testFilePath = Paths.get("src", "test", "resources", "file.test");

        FileStorageRequest storageRequest = new FileStorageRequest("owner",
                                                                   new FileReferenceMetaInfo(
                                                                       "edc900745c5d15d773fbcdc0b376f00c",
                                                                       "MD5",
                                                                       "file.name",
                                                                       null,
                                                                       MediaType.APPLICATION_OCTET_STREAM),
                                                                   testFilePath.toUri().toString(),
                                                                   "localStorage",
                                                                   Optional.empty(),
                                                                   "group",
                                                                   "TEST",
                                                                   "session-001");
        files.add(storageRequest);
        // rather than mimicking the storage logic, lets just ask for storage and then try it again
        store();
        FileStorageWorkingSubset ws = new FileStorageWorkingSubset(files);
        // from the store tests
        Mockito.verify(storageProgress, Mockito.times(1)).storageSucceed(Mockito.any(), Mockito.any(), Mockito.any());
        plugin.store(ws, storageProgress);
        Mockito.verify(storageProgress, Mockito.times(2)).storageSucceed(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void store_error_missing_file() throws MalformedURLException {
        Set<FileStorageRequest> files = Sets.newHashSet();
        Path unknownFilePath = Paths.get("src", "test", "resources", "unknown.test");

        FileStorageRequest storageRequest = new FileStorageRequest("owner",
                                                                   new FileReferenceMetaInfo(
                                                                       "edc900745c5d15d773fbcdc0b376f00c",
                                                                       "MD5",
                                                                       "unknown.test",
                                                                       null,
                                                                       MediaType.APPLICATION_OCTET_STREAM),
                                                                   unknownFilePath.toString(),
                                                                   "localStorage",
                                                                   Optional.empty(),
                                                                   "group",
                                                                   "TEST",
                                                                   "session-001");
        files.add(storageRequest);
        FileStorageWorkingSubset ws = new FileStorageWorkingSubset(files);
        Mockito.verify(storageProgress, Mockito.never()).storageSucceed(Mockito.any(), Mockito.any(), Mockito.any());
        plugin.store(ws, storageProgress);
        Mockito.verify(storageProgress, Mockito.never()).storageSucceed(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(storageProgress, Mockito.times(1)).storageFailed(Mockito.eq(storageRequest), Mockito.any());
    }

    @Test
    public void store_error_invalid_md5() throws MalformedURLException {
        Set<FileStorageRequest> files = Sets.newHashSet();

        FileStorageRequest storageRequest = new FileStorageRequest("owner",
                                                                   new FileReferenceMetaInfo(
                                                                       "abcde123456789abcde123456789abcd",
                                                                       "MD5",
                                                                       "file.name",
                                                                       null,
                                                                       MediaType.APPLICATION_OCTET_STREAM),
                                                                   (new URL("file",
                                                                            null,
                                                                            "src/test/resources/file.test")).toString(),
                                                                   "localStorage",
                                                                   Optional.empty(),
                                                                   "group",
                                                                   "TEST",
                                                                   "session-001");
        files.add(storageRequest);
        FileStorageWorkingSubset ws = new FileStorageWorkingSubset(files);
        Mockito.verify(storageProgress, Mockito.never()).storageSucceed(Mockito.any(), Mockito.any(), Mockito.any());
        plugin.store(ws, storageProgress);
        Mockito.verify(storageProgress, Mockito.never()).storageSucceed(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(storageProgress, Mockito.times(1)).storageFailed(Mockito.eq(storageRequest), Mockito.any());
    }

    @Test
    public void deleteFromZipAndZip() throws IOException {
        store();
        Path testFilePath = Paths.get("src", "test", "resources", "file.test");

        FileStorageRequest storageRequest = new FileStorageRequest("owner",
                                                                   new FileReferenceMetaInfo(
                                                                       "edc900745c5d15d773fbcdc0b376f00c",
                                                                       "MD5",
                                                                       "file.name",
                                                                       null,
                                                                       MediaType.APPLICATION_OCTET_STREAM),
                                                                   testFilePath.toUri().toString(),
                                                                   "localStorage",
                                                                   Optional.empty(),
                                                                   "group",
                                                                   "TEST",
                                                                   "session-001");
        Path zipPath = plugin.getCurrentZipPath(plugin.getStorageLocationForZip(storageRequest));
        Set<FileDeletionRequest> files = Sets.newHashSet();
        FileReference fileRef = new FileReference("owner",
                                                  new FileReferenceMetaInfo("edc900745c5d15d773fbcdc0b376f00c",
                                                                            "MD5",
                                                                            "file.name",
                                                                            null,
                                                                            MediaType.APPLICATION_OCTET_STREAM),
                                                  new FileLocation("local-storage",
                                                                   zipPath.toUri().toURL().toString(),
                                                                   false));
        fileRef.setId(1L);
        FileDeletionRequest deletionRequest = new FileDeletionRequest(fileRef, "groupId", "TEST", "session-001");
        files.add(deletionRequest);
        FileDeletionWorkingSubset ws = new FileDeletionWorkingSubset(files);
        Assert.assertTrue("", Files.exists(zipPath));

        Mockito.verify(deletionProgress, Mockito.never()).deletionSucceed(Mockito.eq(deletionRequest));
        Mockito.verify(deletionProgress, Mockito.never())
               .deletionFailed(Mockito.eq(deletionRequest), Mockito.anyString());
        plugin.delete(ws, deletionProgress);
        Mockito.verify(deletionProgress, Mockito.times(1)).deletionSucceed(deletionRequest);
        Mockito.verify(deletionProgress, Mockito.never())
               .deletionFailed(Mockito.eq(deletionRequest), Mockito.anyString());
        Assert.assertTrue("ZIP archive should still be there", Files.notExists(zipPath));
    }

    @Test
    public void deleteFromZipAndNotZip() throws IOException {
        //store first file
        store();
        // store second file
        Set<FileStorageRequest> files = Sets.newHashSet();
        Path testFilePath = Paths.get("src", "test", "resources", "file2.test");

        FileStorageRequest storageRequest = new FileStorageRequest("owner",
                                                                   new FileReferenceMetaInfo(
                                                                       "b4b2c823e4a4cf98d755f76679c83918",
                                                                       "MD5",
                                                                       "file2.name",
                                                                       null,
                                                                       MediaType.APPLICATION_OCTET_STREAM),
                                                                   testFilePath.toUri().toString(),
                                                                   "localStorage",
                                                                   Optional.empty(),
                                                                   "group",
                                                                   "TEST",
                                                                   "session-001");

        files.add(storageRequest);
        FileStorageWorkingSubset ws = new FileStorageWorkingSubset(files);
        plugin.store(ws, storageProgress);

        // delete first file
        testFilePath = Paths.get("src", "test", "resources", "file.test");
        storageRequest = new FileStorageRequest("owner",
                                                new FileReferenceMetaInfo("edc900745c5d15d773fbcdc0b376f00c",
                                                                          "MD5",
                                                                          "file.name",
                                                                          testFilePath.toFile().length(),
                                                                          MediaType.APPLICATION_OCTET_STREAM),
                                                testFilePath.toUri().toString(),
                                                "localStorage",
                                                Optional.empty(),
                                                "group",
                                                "TEST",
                                                "session-001");
        Path zipPath = plugin.getCurrentZipPath(plugin.getStorageLocationForZip(storageRequest));
        Set<FileDeletionRequest> fileDeletionRequests = Sets.newHashSet();
        FileReference fileRef = new FileReference("owner",
                                                  new FileReferenceMetaInfo("edc900745c5d15d773fbcdc0b376f00c",
                                                                            "MD5",
                                                                            "file.name",
                                                                            null,
                                                                            MediaType.APPLICATION_OCTET_STREAM),
                                                  new FileLocation("local-storage",
                                                                   zipPath.toUri().toURL().toString(),
                                                                   false));
        fileRef.setId(1L);
        FileDeletionRequest deletionRequest = new FileDeletionRequest(fileRef, "groupId", "TEST", "session-001");
        fileDeletionRequests.add(deletionRequest);
        FileDeletionWorkingSubset fileDeletionWorkingSubset = new FileDeletionWorkingSubset(fileDeletionRequests);
        Assert.assertTrue("", Files.exists(zipPath));

        Mockito.verify(deletionProgress, Mockito.never()).deletionSucceed(Mockito.eq(deletionRequest));
        Mockito.verify(deletionProgress, Mockito.never())
               .deletionFailed(Mockito.eq(deletionRequest), Mockito.anyString());
        plugin.delete(fileDeletionWorkingSubset, deletionProgress);
        Mockito.verify(deletionProgress, Mockito.times(1)).deletionSucceed(deletionRequest);
        Mockito.verify(deletionProgress, Mockito.never())
               .deletionFailed(Mockito.eq(deletionRequest), Mockito.anyString());
        Assert.assertTrue("ZIP archive should still be there", Files.exists(zipPath));
    }

    @Test
    public void delete() throws IOException {
        URL urlToDelete = new URL("file", null, "target/local-storage/test/huhu/fileToDelete.test");
        Set<FileDeletionRequest> files = Sets.newHashSet();

        FileReference fileRef = new FileReference("owner",
                                                  new FileReferenceMetaInfo("edc900745c5d15d773fbcdc0b376f00c",
                                                                            "MD5",
                                                                            "file.name",
                                                                            null,
                                                                            MediaType.APPLICATION_OCTET_STREAM),
                                                  new FileLocation("local-storage", urlToDelete.toString(), false));
        fileRef.setId(1L);
        FileDeletionRequest deletionRequest = new FileDeletionRequest(fileRef, "groupId", "TEST", "session-001");
        files.add(deletionRequest);
        FileDeletionWorkingSubset ws = new FileDeletionWorkingSubset(files);

        Files.createDirectories(Paths.get(urlToDelete.getPath()).getParent());
        Files.copy(Paths.get("src/test/resources/file.test"), Paths.get(urlToDelete.getPath()));
        Assert.assertTrue("", Files.exists(Paths.get(urlToDelete.getPath())));

        Mockito.verify(deletionProgress, Mockito.never()).deletionSucceed(Mockito.eq(deletionRequest));
        Mockito.verify(deletionProgress, Mockito.never())
               .deletionFailed(Mockito.eq(deletionRequest), Mockito.anyString());
        plugin.delete(ws, deletionProgress);
        Mockito.verify(deletionProgress, Mockito.times(1)).deletionSucceed(deletionRequest);
        Mockito.verify(deletionProgress, Mockito.never())
               .deletionFailed(Mockito.eq(deletionRequest), Mockito.anyString());
    }

    @Test
    public void delete_missing_file() throws IOException {
        URL urlToDelete = new URL("file", null, "target/local-storage/test/fileToDelete.test");
        Set<FileDeletionRequest> files = Sets.newHashSet();

        FileReference fileRef = new FileReference("owner",
                                                  new FileReferenceMetaInfo("edc900745c5d15d773fbcdc0b376f00c",
                                                                            "MD5",
                                                                            "file.name",
                                                                            null,
                                                                            MediaType.APPLICATION_OCTET_STREAM),
                                                  new FileLocation("local-storage", urlToDelete.toString(), false));
        fileRef.setId(1L);
        FileDeletionRequest deletionRequest = new FileDeletionRequest(fileRef, "groupId", "TEST", "session-001");
        files.add(deletionRequest);
        FileDeletionWorkingSubset ws = new FileDeletionWorkingSubset(files);

        Assert.assertFalse("", Files.exists(Paths.get(urlToDelete.getPath())));

        Mockito.verify(deletionProgress, Mockito.never()).deletionSucceed(Mockito.eq(deletionRequest));
        Mockito.verify(deletionProgress, Mockito.never())
               .deletionFailed(Mockito.eq(deletionRequest), Mockito.anyString());
        plugin.delete(ws, deletionProgress);
        Mockito.verify(deletionProgress, Mockito.times(1)).deletionSucceed(deletionRequest);
        Mockito.verify(deletionProgress, Mockito.never())
               .deletionFailed(Mockito.eq(deletionRequest), Mockito.anyString());
        //To avoid issues with @After and still have a nice context on other tests, lets create a the base storage directory
        Files.createDirectories(Paths.get(baseStorageLocation));
    }

    @Test
    public void delete_error() throws IOException {
        URL urlToDelete = new URL("file", null, "target/local-storage/test/fileToDelete.test");
        Set<FileDeletionRequest> files = Sets.newHashSet();

        FileReference fileRef = new FileReference("owner",
                                                  new FileReferenceMetaInfo("edc900745c5d15d773fbcdc0b376f00c",
                                                                            "MD5",
                                                                            "file.name",
                                                                            null,
                                                                            MediaType.APPLICATION_OCTET_STREAM),
                                                  new FileLocation("local-storage", urlToDelete.toString(), false));
        fileRef.setId(1L);
        FileDeletionRequest deletionRequest = new FileDeletionRequest(fileRef, "groupId", "TEST", "session-001");
        files.add(deletionRequest);
        FileDeletionWorkingSubset ws = new FileDeletionWorkingSubset(files);

        Files.createDirectories(Paths.get(urlToDelete.getPath()).getParent());
        Files.copy(Paths.get("src/test/resources/file.test"), Paths.get(urlToDelete.getPath()));
        Assert.assertTrue("", Files.exists(Paths.get(urlToDelete.getPath())));
        Paths.get(urlToDelete.getPath()).getParent().toFile().setWritable(false);
        Assert.assertFalse("", Files.isWritable(Paths.get(urlToDelete.getPath()).getParent()));

        Mockito.verify(deletionProgress, Mockito.never()).deletionSucceed(Mockito.eq(deletionRequest));
        Mockito.verify(deletionProgress, Mockito.never())
               .deletionFailed(Mockito.eq(deletionRequest), Mockito.anyString());
        plugin.delete(ws, deletionProgress);
        Mockito.verify(deletionProgress, Mockito.never()).deletionSucceed(deletionRequest);
        Mockito.verify(deletionProgress, Mockito.times(1))
               .deletionFailed(Mockito.eq(deletionRequest), Mockito.anyString());
    }

}
