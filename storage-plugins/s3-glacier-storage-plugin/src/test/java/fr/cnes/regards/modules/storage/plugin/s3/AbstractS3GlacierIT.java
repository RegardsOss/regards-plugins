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
package fr.cnes.regards.modules.storage.plugin.s3;

import fr.cnes.regards.framework.jpa.multitenant.lock.LockService;
import fr.cnes.regards.framework.jpa.multitenant.lock.LockServiceTask;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.IPluginParam;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.StringPluginParam;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.s3.S3StorageConfiguration;
import fr.cnes.regards.framework.s3.domain.S3Server;
import fr.cnes.regards.framework.s3.test.S3BucketTestUtils;
import fr.cnes.regards.framework.s3.test.S3FileTestUtils;
import fr.cnes.regards.framework.test.integration.RegardsSpringRunner;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.storage.domain.database.FileLocation;
import fr.cnes.regards.modules.storage.domain.database.FileReference;
import fr.cnes.regards.modules.storage.domain.database.FileReferenceMetaInfo;
import fr.cnes.regards.modules.storage.domain.database.request.FileStorageRequest;
import fr.cnes.regards.modules.storage.domain.plugin.IPeriodicActionProgressManager;
import fr.cnes.regards.modules.storage.domain.plugin.IStorageProgressManager;
import fr.cnes.regards.modules.storage.service.glacier.GlacierArchiveService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeType;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Abstract class for {@link S3Glacier} related tests
 *
 * @author Thibaud Michaudel
 **/
@RunWith(RegardsSpringRunner.class)
public abstract class AbstractS3GlacierIT {

    private static final Logger LOGGER = getLogger(AbstractS3GlacierIT.class);

    private static final String TENANT = "TENANT";

    public final String rootPath = System.getProperty("user.name") + "_tests";

    public static final int ARCHIVE_DURATION_IN_HOURS = 1;

    /**
     * Tested class : S3 Glacier plugin.
     */
    protected S3Glacier s3Glacier;

    @Value("${s3.endPoint:UNKNOWN}")
    protected String endPoint;

    @Value("${s3.key:regards}")
    protected String key;

    @Value("${s3.secret:regardspwd}")
    protected String secret;

    @Value("${s3.region:fr-regards-1}")
    protected String region;

    @Value("${s3.bucket:bucket-glacier}")
    protected String bucket;

    @Rule
    public TemporaryFolder workspace = new TemporaryFolder();

    protected IRuntimeTenantResolver runtimeTenantResolver;

    protected LockService lockService;

    protected GlacierArchiveService glacierArchiveService;

    private Random random = new Random();

    @Before
    public void init() throws Throwable {
        S3BucketTestUtils.createBucket(createInputS3Server());
        PluginUtils.setup();
        runtimeTenantResolver = Mockito.mock(IRuntimeTenantResolver.class);
        Mockito.when(runtimeTenantResolver.getTenant()).thenReturn(TENANT);
        lockService = Mockito.mock(LockService.class);
        Mockito.doAnswer((mock) -> {
            LockServiceTask task = mock.getArgument(1);
            task.run();
            return true;
        }).when(lockService).runWithLock(any(), any());
        glacierArchiveService = Mockito.mock(GlacierArchiveService.class);
    }

    @After
    public void cleanBucket() {
        S3FileTestUtils.deleteAllFilesFromRoot(s3Glacier.storageConfiguration, s3Glacier.rootPath);
    }

    protected void loadPlugin(String endpoint,
                              String region,
                              String key,
                              String secret,
                              String bucket,
                              String rootPath) {
        StringPluginParam secretParam = IPluginParam.build(S3OnlineStorage.S3_SERVER_SECRET_PARAM_NAME, secret);
        secretParam.setDecryptedValue(secret);
        // Set plugin configuration
        Collection<IPluginParam> parameters = IPluginParam.set(IPluginParam.build(S3OnlineStorage.S3_SERVER_ENDPOINT_PARAM_NAME,
                                                                                  endpoint),
                                                               IPluginParam.build(S3OnlineStorage.S3_SERVER_REGION_PARAM_NAME,
                                                                                  region),
                                                               IPluginParam.build(S3OnlineStorage.S3_SERVER_KEY_PARAM_NAME,
                                                                                  key),
                                                               secretParam,
                                                               IPluginParam.build(S3OnlineStorage.S3_SERVER_BUCKET_PARAM_NAME,
                                                                                  bucket),
                                                               IPluginParam.build(S3OnlineStorage.S3_SERVER_ROOT_PATH_PARAM_NAME,
                                                                                  rootPath),
                                                               IPluginParam.build(S3Glacier.GLACIER_LOCAL_WORKSPACE_FILE_LIFETIME_IN_HOURS,
                                                                                  ARCHIVE_DURATION_IN_HOURS),
                                                               IPluginParam.build(S3Glacier.GLACIER_PARALLEL_TASK_NUMBER,
                                                                                  1),
                                                               IPluginParam.build(S3Glacier.GLACIER_WORKSPACE_PATH,
                                                                                  workspace.getRoot().toString()),
                                                               IPluginParam.build(S3Glacier.GLACIER_S3_ACCESS_TRY_TIMEOUT,
                                                                                  3600),
                                                               IPluginParam.build(S3Glacier.GLACIER_SMALL_FILE_ARCHIVE_MAX_SIZE,
                                                                                  1600),
                                                               IPluginParam.build(S3Glacier.GLACIER_SMALL_FILE_MAX_SIZE,
                                                                                  500),
                                                               IPluginParam.build(S3Glacier.GLACIER_SMALL_FILE_ARCHIVE_DURATION_IN_HOURS,
                                                                                  ARCHIVE_DURATION_IN_HOURS));

        PluginConfiguration pluginConfiguration = PluginConfiguration.build(S3Glacier.class,
                                                                            "S3 Glacier configuration plugin",
                                                                            parameters);
        // Load plugin
        try {
            s3Glacier = PluginUtils.getPlugin(pluginConfiguration, new ConcurrentHashMap<>());
        } catch (NotAvailablePluginConfigurationException e) {
            Assert.fail(e.getMessage());
        }
        Assert.assertNotNull(s3Glacier);

        // Settings for all available S3 servers for the downloading
        S3StorageConfiguration s3StorageSettingsMock = Mockito.mock(S3StorageConfiguration.class);
        Mockito.when(s3StorageSettingsMock.getStorages()).thenReturn(Collections.singletonList(createInputS3Server()));
        ReflectionTestUtils.setField(s3Glacier, "s3StorageSettings", s3StorageSettingsMock);
        ReflectionTestUtils.setField(s3Glacier, "lockService", lockService);
        ReflectionTestUtils.setField(s3Glacier, "glacierArchiveService", glacierArchiveService);
    }

    private S3Server createInputS3Server() {
        return new S3Server(endPoint, region, key, secret, bucket);
    }

    protected record FileNameAndChecksum(String filename,
                                         String checksum) {

    }

    protected FileStorageRequest createFileStorageRequest(String subDirectory, String fileName, String fileChecksum) {
        FileStorageRequest fileStorageRequest = new FileStorageRequest();
        fileStorageRequest.setId(random.nextLong());
        fileStorageRequest.setOriginUrl("file:./src/test/resources/files/" + fileName);
        fileStorageRequest.setStorageSubDirectory(subDirectory);

        FileReferenceMetaInfo fileReferenceMetaInfo = new FileReferenceMetaInfo();
        fileReferenceMetaInfo.setFileName(fileName);
        fileReferenceMetaInfo.setAlgorithm("md5");
        fileReferenceMetaInfo.setMimeType(MimeType.valueOf("text/plain"));
        fileReferenceMetaInfo.setChecksum(fileChecksum);

        fileStorageRequest.setMetaInfo(fileReferenceMetaInfo);

        return fileStorageRequest;
    }

    protected FileReference createFileReference(FileStorageRequest fileStorageRequest, String rootPath) {
        FileReferenceMetaInfo fileReferenceMetaInfo = new FileReferenceMetaInfo();
        fileReferenceMetaInfo.setFileName(fileStorageRequest.getMetaInfo().getFileName());
        fileReferenceMetaInfo.setAlgorithm(fileStorageRequest.getMetaInfo().getAlgorithm());
        fileReferenceMetaInfo.setMimeType(fileStorageRequest.getMetaInfo().getMimeType());
        fileReferenceMetaInfo.setChecksum(fileStorageRequest.getMetaInfo().getChecksum());
        fileReferenceMetaInfo.setFileSize(fileStorageRequest.getMetaInfo().getFileSize());

        FileLocation fileLocation = new FileLocation();
        fileLocation.setUrl(buildFileLocationUrl(fileStorageRequest, rootPath));

        return new FileReference("regards", fileReferenceMetaInfo, fileLocation);
    }

    private String buildFileLocationUrl(FileStorageRequest fileStorageRequest, String rootPath) {
        return endPoint + File.separator + s3Glacier.bucket + Paths.get(File.separator,
                                                                        rootPath,
                                                                        fileStorageRequest.getStorageSubDirectory())
                                                                   .resolve(fileStorageRequest.getMetaInfo()
                                                                                              .getChecksum());
    }

    protected URL createExpectedURL(String filename) {
        try {
            return new URL(endPoint + File.separator + bucket + (s3Glacier.rootPath != null
                                                                 && !s3Glacier.rootPath.equals("") ?
                File.separator + s3Glacier.rootPath :
                "") + File.separator + filename);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    protected URL createExpectedURL(String node, String filename) {
        return createExpectedURL(node + File.separator + filename);
    }

    protected URL createExpectedURL(String node, String archiveName, String filename) {
        return createExpectedURL(node,
                                 archiveName + S3Glacier.ARCHIVE_EXTENSION + S3Glacier.ARCHIVE_DELIMITER + filename);
    }

    class TestStorageProgressManager implements IStorageProgressManager {

        List<URL> storageSucceed = new ArrayList<>();

        List<URL> storageSucceedWithPendingAction = new ArrayList<>();

        List<String> storagePendingActionSucceed = new ArrayList<>();

        List<String> storageFailed = new ArrayList<>();

        @Override
        public void storageSucceed(FileStorageRequest fileReferenceRequest, URL storedUrl, Long fileSize) {
            storageSucceed.add(storedUrl);
        }

        @Override
        public void storageSucceedWithPendingActionRemaining(FileStorageRequest fileReferenceRequest,
                                                             URL storedUrl,
                                                             Long fileSize,
                                                             Boolean notifyAdministrators) {
            storageSucceedWithPendingAction.add(storedUrl);

        }

        @Override
        public void storagePendingActionSucceed(String storedUrl) {
            storagePendingActionSucceed.add(storedUrl);

        }

        @Override
        public void storageFailed(FileStorageRequest fileReferenceRequest, String cause) {
            storageFailed.add(fileReferenceRequest.getOriginUrl());
        }

        public List<URL> getStorageSucceed() {
            return storageSucceed;
        }

        public List<URL> getStorageSucceedWithPendingAction() {
            return storageSucceedWithPendingAction;
        }

        public List<String> getStoragePendingActionSucceed() {
            return storagePendingActionSucceed;
        }

        public List<String> getStorageFailed() {
            return storageFailed;
        }

        public int countAllReports() {
            return storageSucceed.size()
                   + storageSucceedWithPendingAction.size()
                   + storagePendingActionSucceed.size()
                   + storageFailed.size();
        }

        public void reset() {
            storageSucceed.clear();
            storageSucceedWithPendingAction.clear();
            storagePendingActionSucceed.clear();
            storageFailed.clear();

        }
    }

    class TestPeriodicActionProgressManager implements IPeriodicActionProgressManager {

        List<String> storagePendingActionSucceed = new ArrayList<>();

        List<Path> storagePendingActionError = new ArrayList<>();

        @Override
        public void storagePendingActionSucceed(String pendingActionSucceedUrl) {
            storagePendingActionSucceed.add(pendingActionSucceedUrl);
        }

        @Override
        public void storagePendingActionError(Path pendingActionErrorPath) {
            storagePendingActionError.add(pendingActionErrorPath);
        }

        public List<String> getStoragePendingActionSucceed() {
            return storagePendingActionSucceed;
        }

        public List<Path> getStoragePendingActionError() {
            return storagePendingActionError;
        }

        public int countAllReports() {
            return storagePendingActionSucceed.size() + storagePendingActionError.size();
        }
    }
}
