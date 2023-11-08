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
import fr.cnes.regards.framework.jpa.multitenant.lock.LockServiceResponse;
import fr.cnes.regards.framework.jpa.multitenant.lock.LockServiceTask;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.IPluginParam;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.StringPluginParam;
import fr.cnes.regards.framework.modules.workspace.service.WorkspaceService;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.s3.S3StorageConfiguration;
import fr.cnes.regards.framework.s3.client.GlacierFileStatus;
import fr.cnes.regards.framework.s3.client.S3HighLevelReactiveClient;
import fr.cnes.regards.framework.s3.domain.*;
import fr.cnes.regards.framework.s3.exception.S3ClientException;
import fr.cnes.regards.framework.s3.test.S3BucketTestUtils;
import fr.cnes.regards.framework.s3.test.S3FileTestUtils;
import fr.cnes.regards.framework.test.integration.RegardsSpringRunner;
import fr.cnes.regards.framework.utils.file.ChecksumUtils;
import fr.cnes.regards.framework.utils.file.DownloadUtils;
import fr.cnes.regards.framework.utils.file.ZipUtils;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.storage.domain.database.FileLocation;
import fr.cnes.regards.modules.storage.domain.database.FileReference;
import fr.cnes.regards.modules.storage.domain.database.FileReferenceMetaInfo;
import fr.cnes.regards.modules.storage.domain.database.request.FileCacheRequest;
import fr.cnes.regards.modules.storage.domain.database.request.FileDeletionRequest;
import fr.cnes.regards.modules.storage.domain.database.request.FileStorageRequest;
import fr.cnes.regards.modules.storage.domain.plugin.IDeletionProgressManager;
import fr.cnes.regards.modules.storage.domain.plugin.IPeriodicActionProgressManager;
import fr.cnes.regards.modules.storage.domain.plugin.IRestorationProgressManager;
import fr.cnes.regards.modules.storage.domain.plugin.IStorageProgressManager;
import fr.cnes.regards.modules.storage.s3.common.AbstractS3Storage;
import fr.cnes.regards.modules.storage.service.glacier.GlacierArchiveService;
import io.vavr.Tuple;
import io.vavr.control.Option;
import org.apache.http.client.utils.URIBuilder;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.jupiter.api.Assertions;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.services.s3.model.RestoreObjectResponse;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.*;
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

    protected static final String BUCKET_OUTPUT = "bucket-glacier";

    public static final String ROOT_PATH = System.getProperty("user.name") + "_tests";

    public static final int ARCHIVE_DURATION_IN_HOURS = 1;

    public static final int CACHE_DURATION_IN_HOURS = 10;

    public static final int MULTIPART_PARALLEL_PART = 5;

    public static final int UPLOAD_WITH_MULTIPART_THRESHOLD_IN_MB = 5;

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

    private String rootPath;

    @Rule
    public TemporaryFolder workspace = new TemporaryFolder();

    protected IRuntimeTenantResolver runtimeTenantResolver;

    protected GlacierArchiveService glacierArchiveService;

    private Random random = new Random();

    private S3StorageConfiguration s3StorageSettingsMock;

    private S3HighLevelReactiveClient s3Client;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void init() {
        S3BucketTestUtils.createBucket(createInputS3Server());
        PluginUtils.setup();

        runtimeTenantResolver = Mockito.mock(IRuntimeTenantResolver.class);
        Mockito.when(runtimeTenantResolver.getTenant()).thenReturn(TENANT);
        Mockito.doNothing().when(runtimeTenantResolver).forceTenant(anyString());
        glacierArchiveService = Mockito.mock(GlacierArchiveService.class);

        // Settings for download in all available S3 servers
        s3StorageSettingsMock = Mockito.mock(S3StorageConfiguration.class);
        Mockito.when(s3StorageSettingsMock.getStorages()).thenReturn(Collections.singletonList(createInputS3Server()));

        // Custom S3 Client that ignore restore call that are unavailable for tests in the regards test environment
        Scheduler scheduler = Schedulers.newParallel("s3-reactive-client", 10);
    }

    private LockService mockLockService() throws InterruptedException {
        LockService lockService = Mockito.mock(LockService.class);
        Mockito.doAnswer((mock) -> {
            LockServiceTask task = mock.getArgument(1);
            return new LockServiceResponse<>(true, task.run());
        }).when(lockService).runWithLock(any(), any());
        Mockito.doAnswer((mock) -> {
            LockServiceTask task = mock.getArgument(1);
            return new LockServiceResponse<>(true, task.run());
        }).when(lockService).tryRunWithLock(any(), any(), anyInt(), any());
        Mockito.doAnswer((mock) -> 60000).when(lockService).getTimeToLive();
        return lockService;
    }

    private LockService mockLockServiceError() throws InterruptedException {
        LockService lockService = Mockito.mock(LockService.class);
        Mockito.doThrow(SimulatedException.class).when(lockService).runWithLock(any(), any());
        Mockito.doThrow(SimulatedException.class).when(lockService).tryRunWithLock(any(), any(), anyInt(), any());
        Mockito.doThrow(SimulatedException.class).when(lockService).getTimeToLive();
        return lockService;
    }

    @After
    public void cleanBucket() {
        S3FileTestUtils.deleteAllFilesFromRoot(s3Glacier.storageConfiguration, rootPath);
    }

    @After
    public void disposeClient() {
        s3Client.close();
    }

    protected void loadPlugin(String endpoint,
                              String region,
                              String key,
                              String secret,
                              String bucket,
                              String rootPath) {
        loadPlugin(endpoint,
                   region,
                   key,
                   secret,
                   bucket,
                   rootPath,
                   MockedS3ClientType.MockedS3ClientWithoutRestore,
                   false);
    }

    protected void loadPlugin(String endpoint,
                              String region,
                              String key,
                              String secret,
                              String bucket,
                              String rootPath,
                              MockedS3ClientType clientType,
                              Boolean simulateLockTaskException) {
        this.rootPath = rootPath;
        StringPluginParam secretParam = IPluginParam.build(AbstractS3Storage.S3_SERVER_SECRET_PARAM_NAME, secret);
        secretParam.setDecryptedValue(secret);
        // Set plugin configuration
        Collection<IPluginParam> parameters = IPluginParam.set(IPluginParam.build(AbstractS3Storage.S3_SERVER_ENDPOINT_PARAM_NAME,
                                                                                  endpoint),
                                                               IPluginParam.build(AbstractS3Storage.S3_SERVER_REGION_PARAM_NAME,
                                                                                  region),
                                                               IPluginParam.build(AbstractS3Storage.S3_SERVER_KEY_PARAM_NAME,
                                                                                  key),
                                                               secretParam,
                                                               IPluginParam.build(AbstractS3Storage.S3_SERVER_BUCKET_PARAM_NAME,
                                                                                  bucket),
                                                               IPluginParam.build(AbstractS3Storage.S3_SERVER_ROOT_PATH_PARAM_NAME,
                                                                                  rootPath),
                                                               IPluginParam.build(S3Glacier.GLACIER_ARCHIVE_CACHE_FILE_LIFETIME_IN_HOURS,
                                                                                  CACHE_DURATION_IN_HOURS),
                                                               IPluginParam.build(S3Glacier.GLACIER_PARALLEL_DELETE_AND_RESTORE_TASK_NUMBER,
                                                                                  10),
                                                               IPluginParam.build(S3Glacier.GLACIER_PARALLEL_STORE_TASK_NUMBER,
                                                                                  1),
                                                               IPluginParam.build(S3Glacier.GLACIER_WORKSPACE_PATH,
                                                                                  workspace.getRoot().toString()),
                                                               IPluginParam.build(S3Glacier.GLACIER_S3_ACCESS_TRY_TIMEOUT,
                                                                                  4),
                                                               IPluginParam.build(S3Glacier.GLACIER_SMALL_FILE_ARCHIVE_MAX_SIZE,
                                                                                  1600),
                                                               IPluginParam.build(S3Glacier.GLACIER_SMALL_FILE_MAX_SIZE,
                                                                                  500),
                                                               IPluginParam.build(S3Glacier.GLACIER_SMALL_FILE_ARCHIVE_DURATION_IN_HOURS,
                                                                                  ARCHIVE_DURATION_IN_HOURS),
                                                               IPluginParam.build(S3Glacier.UPLOAD_WITH_MULTIPART_THRESHOLD_IN_MB_PARAM_NAME,
                                                                                  UPLOAD_WITH_MULTIPART_THRESHOLD_IN_MB),
                                                               IPluginParam.build(S3Glacier.MULTIPART_PARALLEL_PARAM_NAME,
                                                                                  MULTIPART_PARALLEL_PART));

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

        Scheduler scheduler = Schedulers.newParallel("s3-reactive-client", 10);
        switch (clientType) {
            case MockedS3ClientWithNoFileAvailable -> s3Client = new MockedS3ClientWithNoFileAvailable(scheduler);
            case MockedS3ClientWithoutRestore -> s3Client = new MockedS3ClientWithoutRestore(scheduler);
            default -> s3Client = new MockedS3Client(scheduler);
        }

        LockService lockService;
        try {
            if (!simulateLockTaskException) {
                lockService = mockLockService();
            } else {
                lockService = mockLockServiceError();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        WorkspaceService workspaceService = Mockito.mock(WorkspaceService.class);
        try {
            Mockito.when(workspaceService.getMicroserviceWorkspace()).thenReturn(temporaryFolder.getRoot().toPath());
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }

        // Apply mocks to the plugin
        ReflectionTestUtils.setField(s3Glacier, "s3StorageSettings", s3StorageSettingsMock);
        ReflectionTestUtils.setField(s3Glacier, "lockService", lockService);
        ReflectionTestUtils.setField(s3Glacier, "glacierArchiveService", glacierArchiveService);
        ReflectionTestUtils.setField(s3Glacier, "runtimeTenantResolver", runtimeTenantResolver);
        ReflectionTestUtils.setField(s3Glacier, "client", s3Client);
        ReflectionTestUtils.setField(s3Glacier, "workspaceService", workspaceService);

    }

    private S3Server createInputS3Server() {
        return new S3Server(endPoint, region, key, secret, bucket);
    }

    protected void copyFileToWorkspace(String rootPath,
                                       String targetDir,
                                       String nodeName,
                                       String fileName,
                                       String workspaceDir) throws IOException, URISyntaxException {
        Path targetFile = Paths.get(workspace.getRoot().getAbsolutePath(), workspaceDir, rootPath, nodeName, targetDir);
        Files.createDirectories(targetFile);
        Files.copy(Path.of(S3GlacierSendArchiveIT.class.getResource("/files/" + fileName).toURI()),
                   targetFile.resolve(fileName));
    }

    protected FileReference createFileReference(String fileName,
                                                String fileChecksum,
                                                long fileSize,
                                                String nodeName,
                                                String archiveName,
                                                boolean pendingActionRemaining) {
        URL storedFileUrl;
        if (archiveName != null) {
            storedFileUrl = createExpectedURL(nodeName, archiveName, fileName);
        } else {
            storedFileUrl = createExpectedURL(nodeName, fileName);
        }

        FileReferenceMetaInfo metaInfo = new FileReferenceMetaInfo(fileChecksum,
                                                                   S3Glacier.MD5_CHECKSUM,
                                                                   fileName,
                                                                   fileSize,
                                                                   MimeType.valueOf("text/plain"));

        FileLocation location = new FileLocation("glacier", storedFileUrl.toString(), pendingActionRemaining);
        FileReference reference = new FileReference("test-owner", metaInfo, location);
        reference.setId(random.nextLong());
        return reference;
    }

    protected StorageCommand.Write createTestArchiveAndBuildWriteCmd(List<String> filesNames,
                                                                     String nodeName,
                                                                     String archiveName)
        throws URISyntaxException, IOException, NoSuchAlgorithmException {
        Path testWorkspace = workspace.getRoot().toPath().resolve("test");
        Path archiveTestPath = createTestArchive(filesNames, nodeName, archiveName, testWorkspace);

        String entryKey = s3Glacier.storageConfiguration.entryKey(testWorkspace.relativize(archiveTestPath).toString());
        String checksum = ChecksumUtils.computeHexChecksum(archiveTestPath, S3Glacier.MD5_CHECKSUM);
        Long archiveSize = Files.size(archiveTestPath);

        Flux<ByteBuffer> buffers = DataBufferUtils.read(archiveTestPath,
                                                        new DefaultDataBufferFactory(),
                                                        MULTIPART_PARALLEL_PART * 1024 * 1024)
                                                  .map(DataBuffer::asByteBuffer);

        StorageEntry storageEntry = StorageEntry.builder()
                                                .config(s3Glacier.storageConfiguration)
                                                .fullPath(entryKey)
                                                .checksum(Option.some(Tuple.of(S3Glacier.MD5_CHECKSUM, checksum)))
                                                .size(Option.some(archiveSize))
                                                .data(buffers)
                                                .build();
        String taskId = "S3GlacierRestore" + archiveName;
        StorageCommand.Write writeCmd = new StorageCommand.Write.Impl(s3Glacier.storageConfiguration,
                                                                      new StorageCommandID(taskId, UUID.randomUUID()),
                                                                      entryKey,
                                                                      storageEntry);
        return writeCmd;
    }

    protected static Path createTestArchive(List<String> filesNames,
                                            String nodeName,
                                            String archiveName,
                                            Path testWorkspace) throws URISyntaxException, IOException {
        ArrayList<File> filesList = new ArrayList<File>();
        for (String fileName : filesNames) {
            filesList.add(Path.of(S3GlacierRestoreIT.class.getResource("/files/" + fileName).toURI()).toFile());
        }
        Path archiveTestPath = testWorkspace.resolve(Path.of(nodeName, archiveName + S3Glacier.ARCHIVE_EXTENSION));
        Files.createDirectories(archiveTestPath.getParent());
        ZipUtils.createZipArchive(archiveTestPath.toFile(), filesList);
        return archiveTestPath;
    }

    protected FileCacheRequest createFileCacheRequest(Path restorationWorkspace,
                                                      String fileName,
                                                      String fileChecksum,
                                                      long fileSize,
                                                      String nodeName,
                                                      String archiveName,
                                                      boolean pendingActionRemaining) {
        FileReference reference = createFileReference(fileName,
                                                      fileChecksum,
                                                      fileSize,
                                                      nodeName,
                                                      archiveName,
                                                      pendingActionRemaining);
        FileCacheRequest request = new FileCacheRequest(reference,
                                                        restorationWorkspace.toString(),
                                                        OffsetDateTime.now().plusDays(1),
                                                        "test-group-id");
        return request;
    }

    protected void checkDeletionOfOneFileSuccessWithPending(TestDeletionProgressManager progressManager,
                                                            String remainingFile,
                                                            String fileChecksum,
                                                            String nodeName,
                                                            String archiveName) {
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> progressManager.countAllReports() == 1);
        Assertions.assertEquals(1,
                                progressManager.getDeletionSucceedWithPendingAction().size(),
                                "There should be one success");
        Assertions.assertEquals(fileChecksum,
                                progressManager.getDeletionSucceedWithPendingAction()
                                               .get(0)
                                               .getFileReference()
                                               .getMetaInfo()
                                               .getChecksum(),
                                "The successful request is not the expected one");
        Assertions.assertEquals(0, progressManager.getDeletionFailed().size());

        Path buildingDirPath = Path.of(workspace.getRoot().getAbsolutePath(),
                                       S3Glacier.ZIP_DIR,
                                       rootPath,
                                       nodeName,
                                       S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName);
        Assertions.assertTrue(Files.exists(buildingDirPath), "The building directory should still exists");
        File buildingDirFile = buildingDirPath.toFile();
        Assertions.assertEquals(1,
                                buildingDirFile.list().length,
                                "There should be only one file remaining in the directory");
        Assertions.assertEquals(remainingFile,
                                buildingDirFile.list()[0],
                                "The remaining file in the directory should be the one that wasn't deleted");
    }

    protected void checkDeletionOfOneFileSuccess(TestDeletionProgressManager progressManager,
                                                 String remainingFile,
                                                 String fileChecksum,
                                                 String nodeName,
                                                 String archiveName) {
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> progressManager.countAllReports() == 1);
        Assertions.assertEquals(1, progressManager.getDeletionSucceed().size(), "There should be one success");
        Assertions.assertEquals(fileChecksum,
                                progressManager.getDeletionSucceed()
                                               .get(0)
                                               .getFileReference()
                                               .getMetaInfo()
                                               .getChecksum(),
                                "The successful request is not the expected one");
        Assertions.assertEquals(0, progressManager.getDeletionFailed().size());

        Path buildingDirPath = Path.of(workspace.getRoot().getAbsolutePath(),
                                       S3Glacier.ZIP_DIR,
                                       rootPath,
                                       nodeName,
                                       S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName);
        Assertions.assertTrue(Files.exists(buildingDirPath), "The building directory should still exists");
        File buildingDirFile = buildingDirPath.toFile();
        Assertions.assertEquals(1,
                                buildingDirFile.list().length,
                                "There should be only one file remaining in the directory");
        Assertions.assertEquals(remainingFile,
                                buildingDirFile.list()[0],
                                "The remaining file in the directory should be the one that wasn't deleted");
    }

    protected FileStorageRequest createFileStorageRequest(String subDirectory, String fileName, String fileChecksum) {
        return createFileStorageRequest(subDirectory, fileName, null, fileChecksum);
    }

    protected FileStorageRequest createFileStorageRequest(String subDirectory,
                                                          String fileName,
                                                          Long fileSize,
                                                          String fileChecksum) {
        FileStorageRequest fileStorageRequest = new FileStorageRequest();
        fileStorageRequest.setId(random.nextLong());
        fileStorageRequest.setOriginUrl("file:./src/test/resources/files/" + fileName);
        fileStorageRequest.setStorageSubDirectory(subDirectory);

        FileReferenceMetaInfo fileReferenceMetaInfo = new FileReferenceMetaInfo();
        fileReferenceMetaInfo.setFileName(fileName);
        fileReferenceMetaInfo.setAlgorithm("md5");
        fileReferenceMetaInfo.setMimeType(MimeType.valueOf("text/plain"));
        fileReferenceMetaInfo.setChecksum(fileChecksum);
        fileReferenceMetaInfo.setFileSize(fileSize);

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
        fileLocation.setStorage("Glacier");
        FileReference reference = new FileReference("regards", fileReferenceMetaInfo, fileLocation);
        reference.setId(random.nextLong());
        return reference;
    }

    private String buildFileLocationUrl(FileStorageRequest fileStorageRequest, String rootPath) {
        return endPoint + File.separator + bucket + Paths.get(File.separator,
                                                              rootPath,
                                                              fileStorageRequest.getStorageSubDirectory() != null ?
                                                                  fileStorageRequest.getStorageSubDirectory() :
                                                                  "")
                                                         .resolve(fileStorageRequest.getMetaInfo().getChecksum());
    }

    protected URL createExpectedURLWithParameter(String filePath, @Nullable String smallFileName) {
        try {
            URIBuilder builder = new URIBuilder(endPoint + File.separator + bucket + (rootPath != null
                                                                                      && !ROOT_PATH.equals("") ?
                File.separator + rootPath :
                "") + File.separator + filePath);
            if (smallFileName != null) {
                builder.addParameter(S3Glacier.SMALL_FILE_PARAMETER_NAME, smallFileName);
            }
            return builder.build().toURL();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    protected URL createExpectedURL(String filename) {
        return createExpectedURLWithParameter(filename, null);
    }

    protected URL createExpectedURL(String node, String filename) {
        return createExpectedURLWithParameter(node + File.separator + filename, null);
    }

    protected URL createExpectedURL(String node, String archiveName, String filename) {
        return createExpectedURLWithParameter(node + File.separator + archiveName + S3Glacier.ARCHIVE_EXTENSION,
                                              filename);
    }

    protected void writeFileOnStorage(StorageCommand.Write writeCmd) {
        createS3Client().write(writeCmd)
                        .flatMap(writeResult -> writeResult.matchWriteResult(Mono::just,
                                                                             unreachable -> Mono.error(new RuntimeException(
                                                                                 "Unreachable endpoint")),
                                                                             failure -> Mono.error(new RuntimeException(
                                                                                 "Write failure in S3 storage"))))
                        .doOnError(t -> {
                            LOGGER.error("Storage error", t);
                            throw new S3ClientException(t);
                        })
                        .doOnSuccess(success -> {
                            LOGGER.info("Storage complete");
                        })
                        .block();
    }

    protected static void checkRestoreSuccess(String fileName,
                                              String fileChecksum,
                                              TestRestoreProgressManager progressManager,
                                              Path restorationWorkspace) throws NoSuchAlgorithmException, IOException {
        Awaitility.await().atMost(Durations.FOREVER).until(() -> progressManager.countAllReports() == 1);
        Assertions.assertEquals(1, progressManager.getRestoreSucceed().size(), "There should be one success");
        Assertions.assertEquals(fileChecksum,
                                progressManager.getRestoreSucceed().get(0).getChecksum(),
                                "The successful request is not the expected one");
        Assertions.assertEquals(0, progressManager.getRestoreFailed().size());

        Assertions.assertTrue(Files.exists(restorationWorkspace) && Files.isDirectory(restorationWorkspace),
                              "The target directory should be created");
        File targetDirFile = restorationWorkspace.toFile();
        Assertions.assertEquals(1, targetDirFile.list().length, "There should be only one file in the target dir");
        File restoredFile = targetDirFile.listFiles()[0];
        Assertions.assertEquals(fileName, restoredFile.getName(), "The restored file is not the expected one");
        Assertions.assertEquals(fileChecksum,
                                ChecksumUtils.computeHexChecksum(restoredFile.toPath(), S3Glacier.MD5_CHECKSUM),
                                "The restored file checksum does not match the expected one");
    }

    protected static void checkRestoreSuccess(String fileChecksum,
                                              TestRestoreProgressManager progressManager,
                                              Path restorationWorkspace) throws NoSuchAlgorithmException, IOException {
        checkRestoreSuccess(fileChecksum, fileChecksum, progressManager, restorationWorkspace);
    }

    protected record FileNameAndChecksum(String filename,
                                         String checksum) {

    }

    protected S3HighLevelReactiveClient createS3Client() {
        Scheduler scheduler = Schedulers.newParallel("s3-reactive-client", 10);
        int maxBytesPerPart = UPLOAD_WITH_MULTIPART_THRESHOLD_IN_MB * 1024 * 1024;
        return new S3HighLevelReactiveClient(scheduler, maxBytesPerPart, MULTIPART_PARALLEL_PART);
    }

    protected InputStream downloadFromS3(String url) throws FileNotFoundException {
        StorageConfig.builder(endPoint, region, key, secret);
        return DownloadUtils.getInputStreamFromS3Source(getEntryKey(url),
                                                        StorageConfig.builder(new S3Server(endPoint,
                                                                                           region,
                                                                                           key,
                                                                                           secret,
                                                                                           bucket)).build(),
                                                        new StorageCommandID("downloadTest", UUID.randomUUID()));
    }

    protected String getEntryKey(String url) {
        return url.replaceFirst(Pattern.quote(endPoint) + "(:[0-9]*)?/*", "")
                  .replaceFirst(Pattern.quote(bucket), "")
                  .substring(1);
    }

    static class TestStorageProgressManager implements IStorageProgressManager {

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

    static class TestPeriodicActionProgressManager implements IPeriodicActionProgressManager {

        List<String> storagePendingActionSucceed = new ArrayList<>();

        List<Path> storagePendingActionError = new ArrayList<>();

        List<String> storageAllPendingActionSucceed = new ArrayList<>();

        @Override
        public void storagePendingActionSucceed(String pendingActionSucceedUrl) {
            storagePendingActionSucceed.add(pendingActionSucceedUrl);
        }

        @Override
        public void allPendingActionSucceed(String storageLocationName) {
            storageAllPendingActionSucceed.add(storageLocationName);
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

        public List<String> getStorageAllPendingActionSucceed() {
            return storageAllPendingActionSucceed;
        }

        public int countAllReports() {
            return storagePendingActionSucceed.size() + storagePendingActionError.size();
        }
    }

    static class TestRestoreProgressManager implements IRestorationProgressManager {

        List<FileCacheRequest> restoreSucceed = new ArrayList<>();

        List<FileCacheRequest> restoreFailed = new ArrayList<>();

        @Override
        public void restoreSucceed(FileCacheRequest fileRequest, Path restoredFilePath) {
            restoreSucceed.add(fileRequest);
        }

        @Override
        public void restoreFailed(FileCacheRequest fileRequest, String cause) {
            restoreFailed.add(fileRequest);
            LOGGER.error(cause);
        }

        public List<FileCacheRequest> getRestoreSucceed() {
            return restoreSucceed;
        }

        public List<FileCacheRequest> getRestoreFailed() {
            return restoreFailed;
        }

        public int countAllReports() {
            return restoreSucceed.size() + restoreFailed.size();
        }
    }

    static class TestDeletionProgressManager implements IDeletionProgressManager {

        List<FileDeletionRequest> deletionSucceed = new ArrayList<>();

        List<FileDeletionRequest> deletionSucceedWithPendingAction = new ArrayList<>();

        List<FileDeletionRequest> deletionFailed = new ArrayList<>();

        @Override
        public void deletionSucceed(FileDeletionRequest fileDeletionRequest) {
            deletionSucceed.add(fileDeletionRequest);
        }

        @Override
        public void deletionFailed(FileDeletionRequest fileDeletionRequest, String cause) {
            LOGGER.error("Deletion of {} failed : {}",
                         fileDeletionRequest.getFileReference().getLocation().getUrl(),
                         cause);
            deletionFailed.add(fileDeletionRequest);
        }

        @Override
        public void deletionSucceedWithPendingAction(FileDeletionRequest fileDeletionRequest) {
            deletionSucceedWithPendingAction.add(fileDeletionRequest);
        }

        public List<FileDeletionRequest> getDeletionSucceed() {
            return deletionSucceed;
        }

        public List<FileDeletionRequest> getDeletionFailed() {
            return deletionFailed;
        }

        public List<FileDeletionRequest> getDeletionSucceedWithPendingAction() {
            return deletionSucceedWithPendingAction;
        }

        public int countAllReports() {
            return deletionSucceed.size() + deletionFailed.size() + deletionSucceedWithPendingAction.size();
        }
    }

    private static class MockedS3ClientWithoutRestore extends MockedS3Client {

        public MockedS3ClientWithoutRestore(Scheduler scheduler) {
            super(scheduler);
        }

        @Override
        public Mono<RestoreObjectResponse> restore(StorageConfig config, String key) {
            LOGGER.debug("Ignoring restore for key {}", key);
            return Mono.just(RestoreObjectResponse.builder().build());
        }
    }

    private static class MockedS3Client extends S3HighLevelReactiveClient {

        private int tryCount;

        public MockedS3Client(Scheduler scheduler) {
            super(scheduler, 10 * 1024 * 1024, 10);
            tryCount = 0;
        }

        @Override
        public Mono<GlacierFileStatus> isFileAvailable(StorageConfig config,
                                                       String key,
                                                       String standardStorageClassName) {
            if (tryCount >= 2) {
                tryCount = 0;
                return Mono.just(GlacierFileStatus.AVAILABLE);
            } else {
                tryCount++;
                return Mono.just(GlacierFileStatus.RESTORE_PENDING);
            }
        }
    }

    private static class MockedS3ClientWithNoFileAvailable extends S3HighLevelReactiveClient {

        private int tryCount;

        public MockedS3ClientWithNoFileAvailable(Scheduler scheduler) {
            super(scheduler, 10 * 1024 * 1024, 10);
            tryCount = 0;
        }

        @Override
        public Mono<GlacierFileStatus> isFileAvailable(StorageConfig config,
                                                       String key,
                                                       String standardStorageClassName) {
            return Mono.just(GlacierFileStatus.NOT_AVAILABLE);
        }
    }

    protected enum MockedS3ClientType {
        MockedS3Client, MockedS3ClientWithoutRestore, MockedS3ClientWithNoFileAvailable
    }
}
