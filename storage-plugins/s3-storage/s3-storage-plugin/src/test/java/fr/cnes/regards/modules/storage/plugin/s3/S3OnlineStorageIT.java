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
package fr.cnes.regards.modules.storage.plugin.s3;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.dto.parameter.parameter.IPluginParam;
import fr.cnes.regards.framework.modules.plugins.dto.parameter.parameter.StringPluginParam;
import fr.cnes.regards.framework.modules.workspace.service.WorkspaceService;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.s3.S3StorageConfiguration;
import fr.cnes.regards.framework.s3.domain.S3Server;
import fr.cnes.regards.framework.s3.test.S3BucketTestUtils;
import fr.cnes.regards.framework.test.integration.RegardsSpringRunner;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.fileaccess.dto.FileLocationDto;
import fr.cnes.regards.modules.fileaccess.dto.FileReferenceMetaInfoDto;
import fr.cnes.regards.modules.fileaccess.dto.FileReferenceWithoutOwnersDto;
import fr.cnes.regards.modules.fileaccess.dto.FileRequestStatus;
import fr.cnes.regards.modules.fileaccess.dto.output.worker.FileNamingStrategy;
import fr.cnes.regards.modules.fileaccess.dto.request.FileStorageRequestAggregationDto;
import fr.cnes.regards.modules.fileaccess.plugin.domain.FileDeletionWorkingSubset;
import fr.cnes.regards.modules.fileaccess.plugin.domain.FileStorageWorkingSubset;
import fr.cnes.regards.modules.fileaccess.plugin.domain.IDeletionProgressManager;
import fr.cnes.regards.modules.fileaccess.plugin.domain.IStorageProgressManager;
import fr.cnes.regards.modules.fileaccess.plugin.dto.FileDeletionRequestDto;
import fr.cnes.regards.modules.storage.s3.common.service.S3ClientCreatorService;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Instantiate S3 plugin to test with local minio deployed in server.
 * Simulate the using of 2 minios(S3 server) with 2 different buckets (input, ouput).
 *
 * @author Marc SORDI
 * @author Stephane CORTINE
 */
@RunWith(RegardsSpringRunner.class)
@SpringBootTest
@SpringBootConfiguration
public class S3OnlineStorageIT {

    private static final String TENANT = "TENANT";

    private static final String FILE_NAME = "small.txt";

    private static final String MD5_CHECKSUM_FILE = "706126bf6d8553708227dba90694e81c";

    private static final String MD5_ALGORITHM = "MD5";

    @Value("${s3.endPoint:UNKNOWN}")
    private String endPoint;

    @Value("${s3.key:regards}")
    private String key;

    @Value("${s3.secret:regardspwd}")
    private String secret;

    @Value("${s3.region:fr-regards-1}")
    private String region;

    /**
     * Bucket used in order to download from this location.
     */
    @Value("${s3.bucket.input:bucketinput}")
    private String bucketInput;

    /**
     * Bucket used in order to store in this location.
     */
    @Value("${s3.bucket.output:bucketoutput}")
    private String bucketOutput;

    /**
     * Tested class : S3 plugin.
     */
    private S3OnlineStorage s3OnlineStorage;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void prepare() {
        S3BucketTestUtils.createBucket(createInputS3Server());
        S3BucketTestUtils.createBucket(createOutputS3Server());

        // Initialize plugin environment
        PluginUtils.setup();
    }

    @After
    public void reset() {
        S3BucketTestUtils.deleteBucket(createInputS3Server());
        S3BucketTestUtils.deleteBucket(createOutputS3Server());
    }

    private void loadPlugin(String endpoint,
                            String region,
                            String key,
                            String secret,
                            String bucket,
                            String rootPath,
                            String namingStrategy) {
        StringPluginParam secretParam = IPluginParam.build(S3OnlineStorage.S3_SERVER_SECRET_PARAM_NAME, secret);
        secretParam.setValue(secret);
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
                                                               IPluginParam.build(S3OnlineStorage.FILE_NAMING_STRATEGY,
                                                                                  namingStrategy));

        PluginConfiguration pluginConfiguration = PluginConfiguration.build(S3OnlineStorage.class,
                                                                            "S3 Storage configuration plugin",
                                                                            parameters);
        // Load plugin
        try {
            s3OnlineStorage = PluginUtils.getPlugin(pluginConfiguration, new ConcurrentHashMap<>());
        } catch (NotAvailablePluginConfigurationException e) {
            Assert.fail(e.getMessage());
        }
        Assert.assertNotNull(s3OnlineStorage);

        IRuntimeTenantResolver resolverMock = Mockito.mock(IRuntimeTenantResolver.class);
        Mockito.when(resolverMock.getTenant()).thenReturn(TENANT);
        ReflectionTestUtils.setField(s3OnlineStorage, "runtimeTenantResolver", resolverMock);

        WorkspaceService workspaceService = Mockito.mock(WorkspaceService.class);
        try {
            Mockito.when(workspaceService.getMicroserviceWorkspace()).thenReturn(temporaryFolder.getRoot().toPath());
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }
        ReflectionTestUtils.setField(s3OnlineStorage, "workspaceService", workspaceService);

        // Settings for all available S3 servers for the downloading
        S3StorageConfiguration s3StorageSettingsMock = Mockito.mock(S3StorageConfiguration.class);
        Mockito.when(s3StorageSettingsMock.getStorages()).thenReturn(Collections.singletonList(createInputS3Server()));
        ReflectionTestUtils.setField(s3OnlineStorage, "s3StorageSettings", s3StorageSettingsMock);

        // Create the s3 creator service

        S3ClientCreatorService s3ClientService = Mockito.spy(S3ClientCreatorService.class);

        try {
            Field field = S3ClientCreatorService.class.getDeclaredField("parallelNumber");
            field.setAccessible(true);
            field.set(s3ClientService, 10);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
        Mockito.when(s3ClientService.createS3Client(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt()))
               .thenCallRealMethod();

        ReflectionTestUtils.setField(s3OnlineStorage, "s3ClientService", s3ClientService);
    }

    @Test
    public void givenS3_whenReference_thenValidateAndRetrieveFile_withRootPath() {
        givenS3_whenReference_thenValidateAndRetrieveFile(createFileStorageRequestAggregationDto(""),
                                                          "/rootPath0/rootPath1/");
    }

    @Test
    public void givenS3_whenReference_thenValidateAndRetrieveFile_withRootPath_AndOriginalName() {
        givenS3_whenReference_thenValidateAndRetrieveFile(createFileStorageRequestAggregationDto(""),
                                                          "/rootPath0/rootPath1/",
                                                          FileNamingStrategy.Constants.FILENAME);
    }

    @Test
    public void givenS3_whenReference_thenValidateAndRetrieveFile_withRootPathSubDirectory() {
        givenS3_whenReference_thenValidateAndRetrieveFile(createFileStorageRequestAggregationDto("/dir0/dir1"),
                                                          "/rootPath0");
    }

    @Test
    public void givenS3_whenReference_thenValidateAndRetrieveFile_withSubDirectory() {
        givenS3_whenReference_thenValidateAndRetrieveFile(createFileStorageRequestAggregationDto("/dir0/dir1"), "");
    }

    @Test
    public void givenS3_whenReference_thenValidateAndRetrieveFile_only() {
        givenS3_whenReference_thenValidateAndRetrieveFile(createFileStorageRequestAggregationDto("/dir0/dir1"), "");
    }

    @Test
    public void givenS3_whenReference_from_S3Server_thenValidateAndRetrieveFile_withRootPath() throws IOException {
        // Given
        S3BucketTestUtils.store("./src/test/resources/small.txt", "", createInputS3Server());
        //When, then
        givenS3_whenReference_thenValidateAndRetrieveFile(createFileStorageRequestAggregationDtoFromS3Server(""),
                                                          "/rootPath0/rootPath1/");
    }

    @Test
    public void givenS3_whenReference_from_S3Server_thenValidateAndRetrieveFile_withRootPathSubDirectory()
        throws IOException {
        // Given
        S3BucketTestUtils.store("./src/test/resources/small.txt", "", createInputS3Server());
        //When, then
        givenS3_whenReference_thenValidateAndRetrieveFile(createFileStorageRequestAggregationDtoFromS3Server(
            "/dir0/dir1"), "/rootPath0");
    }

    @Test
    public void givenS3_whenReference_from_S3Server_thenValidateAndRetrieveFile_withSubDirectory() throws IOException {
        // Given
        S3BucketTestUtils.store("./src/test/resources/small.txt", "", createInputS3Server());
        //When, then
        givenS3_whenReference_thenValidateAndRetrieveFile(createFileStorageRequestAggregationDtoFromS3Server(
            "/dir0/dir1"), "");
    }

    @Test
    public void givenS3_whenReference_from_S3Server_thenValidateAndRetrieveFile_only() throws IOException {
        // Given
        S3BucketTestUtils.store("./src/test/resources/small.txt", "", createInputS3Server());
        //When, then
        givenS3_whenReference_thenValidateAndRetrieveFile(createFileStorageRequestAggregationDtoFromS3Server(
            "/dir0/dir1"), "");
    }

    private void givenS3_whenReference_thenValidateAndRetrieveFile(FileStorageRequestAggregationDto fileStorageRequest,
                                                                   String rootPath) {
        givenS3_whenReference_thenValidateAndRetrieveFile(fileStorageRequest,
                                                          rootPath,
                                                          FileNamingStrategy.Constants.CHECKSUM);
    }

    private void givenS3_whenReference_thenValidateAndRetrieveFile(FileStorageRequestAggregationDto fileStorageRequest,
                                                                   String rootPath,
                                                                   String namingStrategy) {
        // Given
        loadPlugin(endPoint, region, key, secret, bucketOutput, rootPath, namingStrategy);

        // When, then
        // Store file in S3 server
        s3OnlineStorage.store(new FileStorageWorkingSubset(Collections.singletonList(fileStorageRequest)),
                              createStorageProgressManager());

        // Create file reference for S3 server
        FileReferenceWithoutOwnersDto fileReference = createFileReferenceDto(fileStorageRequest, rootPath);
        // Validate reference
        Assert.assertTrue(String.format("Invalid URL %s", fileReference.getLocation().getUrl()),
                          s3OnlineStorage.isValidUrl(fileReference.getLocation().getUrl(), new HashSet<>()));
        String expectedFileName = FileNamingStrategy.Constants.FILENAME.equals(namingStrategy) ?
            FILE_NAME :
            MD5_CHECKSUM_FILE;
        Assert.assertEquals(String.format("Invalid S3 file name %s", expectedFileName),
                            expectedFileName,
                            new File(fileReference.getLocation().getUrl()).getName());
        Assert.assertEquals("Invalid file size", 427L, fileReference.getMetaInfo().getFileSize().longValue());

        // Get file as input stream from S3 server
        try {
            InputStream inputStream = s3OnlineStorage.retrieve(fileReference);
            Assert.assertNotNull(inputStream);
        } catch (ModuleException | FileNotFoundException e) {
            Assert.fail("Test Failure : file does not store in S3 server");
        }

        // Delete file from S3 server
        FileDeletionRequestDto fileDeletionRequest = createFileDeletionRequestDto(fileStorageRequest, rootPath);
        s3OnlineStorage.delete(new FileDeletionWorkingSubset(Collections.singletonList(fileDeletionRequest)),
                               createDeletionProgressManager());

        // Retrieve file in S3 server but the file does not exist anymore
        try {
            s3OnlineStorage.retrieve(fileReference);

            Assert.fail("Test Failure : file always exists");
        } catch (Exception exc) {
        }
    }

    private FileStorageRequestAggregationDto createFileStorageRequestAggregationDto(String subDirectory) {
        FileReferenceMetaInfoDto fileReferenceMetaInfo = new FileReferenceMetaInfoDto(MD5_CHECKSUM_FILE,
                                                                                      MD5_ALGORITHM,
                                                                                      FILE_NAME,
                                                                                      null,
                                                                                      null,
                                                                                      null,
                                                                                      "text/plain",
                                                                                      null);

        return new FileStorageRequestAggregationDto(1L,
                                                    null,
                                                    "file:./src/test/resources/" + FILE_NAME,
                                                    "storage",
                                                    fileReferenceMetaInfo,
                                                    subDirectory,
                                                    "sessionOwner",
                                                    "session",
                                                    null,
                                                    null,
                                                    null,
                                                    null);
    }

    private FileStorageRequestAggregationDto createFileStorageRequestAggregationDtoFromS3Server(String subDirectory) {

        FileReferenceMetaInfoDto fileReferenceMetaInfo = new FileReferenceMetaInfoDto(MD5_CHECKSUM_FILE,
                                                                                      MD5_ALGORITHM,
                                                                                      FILE_NAME,
                                                                                      null,
                                                                                      null,
                                                                                      null,
                                                                                      "text/plain",
                                                                                      null);

        return new FileStorageRequestAggregationDto(1L,
                                                    null,
                                                    endPoint
                                                    + File.separator
                                                    + bucketInput
                                                    + File.separator
                                                    + FILE_NAME,
                                                    "storage",
                                                    fileReferenceMetaInfo,
                                                    subDirectory,
                                                    "sessionOwner",
                                                    "session",
                                                    null,
                                                    null,
                                                    null,
                                                    null);
    }

    private FileReferenceWithoutOwnersDto createFileReferenceDto(FileStorageRequestAggregationDto fileStorageRequest,
                                                                 String rootPath) {
        FileReferenceMetaInfoDto fileReferenceMetaInfo = new FileReferenceMetaInfoDto(fileStorageRequest.getMetaInfo()
                                                                                                        .getChecksum(),
                                                                                      fileStorageRequest.getMetaInfo()
                                                                                                        .getAlgorithm(),
                                                                                      fileStorageRequest.getMetaInfo()
                                                                                                        .getFileName(),
                                                                                      fileStorageRequest.getMetaInfo()
                                                                                                        .getFileSize(),
                                                                                      null,
                                                                                      null,
                                                                                      fileStorageRequest.getMetaInfo()
                                                                                                        .getMimeType(),
                                                                                      null);
        FileLocationDto fileLocation = new FileLocationDto("storage",
                                                           buildFileLocationUrl(fileStorageRequest, rootPath));
        return new FileReferenceWithoutOwnersDto(1L, null, fileReferenceMetaInfo, fileLocation, false, false);
    }

    private FileDeletionRequestDto createFileDeletionRequestDto(FileStorageRequestAggregationDto fileStorageRequest,
                                                                String rootPath) {
        FileDeletionRequestDto fileDeletionRequest = new FileDeletionRequestDto(1L,
                                                                                null,
                                                                                FileRequestStatus.TO_DO,
                                                                                null,
                                                                                createFileReferenceDto(
                                                                                    fileStorageRequest,
                                                                                    rootPath),
                                                                                false,
                                                                                null,
                                                                                null,
                                                                                "JOB_ID",
                                                                                null,
                                                                                null);
        return fileDeletionRequest;
    }

    private String buildFileLocationUrl(FileStorageRequestAggregationDto fileStorageRequest, String rootPath) {
        return endPoint + File.separator + bucketOutput + Paths.get(File.separator,
                                                                    rootPath,
                                                                    s3OnlineStorage.getEntryKey(fileStorageRequest));
    }

    private S3Server createInputS3Server() {
        return new S3Server(endPoint, region, key, secret, bucketInput);
    }

    private S3Server createOutputS3Server() {
        return new S3Server(endPoint, region, key, secret, bucketOutput, "");
    }

    private IStorageProgressManager createStorageProgressManager() {
        return new IStorageProgressManager() {

            @Override
            public void storageSucceed(FileStorageRequestAggregationDto fileReferenceRequest,
                                       URL storedUrl,
                                       Long fileSize) {
            }

            @Override
            public void storageSucceedWithPendingActionRemaining(FileStorageRequestAggregationDto fileReferenceRequest,
                                                                 URL storedUrl,
                                                                 Long fileSize,
                                                                 Boolean notifyAdministrators) {

            }

            @Override
            public void storagePendingActionSucceed(String storedUrl) {

            }

            @Override
            public void storageFailed(FileStorageRequestAggregationDto fileReferenceRequest, String cause) {
            }
        };
    }

    private IDeletionProgressManager createDeletionProgressManager() {
        return new IDeletionProgressManager() {

            @Override
            public void deletionSucceed(FileDeletionRequestDto fileDeletionRequest) {
            }

            @Override
            public void deletionSucceedWithPendingAction(FileDeletionRequestDto fileDeletionRequest) {

            }

            @Override
            public void deletionFailed(FileDeletionRequestDto fileDeletionRequest, String s) {
            }
        };
    }
}
