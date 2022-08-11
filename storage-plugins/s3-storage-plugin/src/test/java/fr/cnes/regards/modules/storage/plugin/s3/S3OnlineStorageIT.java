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
package fr.cnes.regards.modules.storage.plugin.s3;

import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.IPluginParam;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.StringPluginParam;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.test.integration.RegardsSpringRunner;
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeType;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Instantiate S3 plugin to test with local minio deployed server
 *
 * @author Marc SORDI, Stephane CORTINE
 */
@RunWith(RegardsSpringRunner.class)
@SpringBootTest
@SpringBootConfiguration
public class S3OnlineStorageIT {

    private static final String TENANT = "TENANT";

    @Value("${s3.server}")
    private String endPointS3;

    @Value("${s3.key}")
    private String key;

    @Value("${s3.secret}")
    private String secret;

    @Value("${s3.region}")
    private String region;

    @Value("${s3.bucket}")
    private String bucket;

    @Rule
    public final S3Rule s3Rule = new S3Rule(() -> endPointS3, () -> key, () -> secret, () -> region, () -> bucket);

    private S3OnlineStorage s3OnlineStorage;

    @Before
    public void prepare() {
        // Initialize plugin environment
        PluginUtils.setup();
    }

    @Test
    public void test() {

        System.out.println("http://toto.com/bucket/path/file".replaceFirst(Pattern.quote("http://toto.com") + "/*", "")
                                                             .replaceFirst(Pattern.quote("bucket" + "/"), "")
                                                             .replaceFirst(Pattern.quote("file"), ""));
    }

    private void loadPlugin(String endpoint, String region, String key, String secret, String bucket, String rootPath) {
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
                                                                                  rootPath));

        PluginConfiguration pluginConfiguration = PluginConfiguration.build(S3OnlineStorage.class,
                                                                            "S3 plugin",
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
    }

    @Test
    public void givenS3_whenReference_thenValidateAndRetrieveFile_withRootPath() {
        givenS3_whenReference_thenValidateAndRetrieveFile("/rootPath0/rootPath1/", "");
    }

    @Test
    public void givenS3_whenReference_thenValidateAndRetrieveFile_withRootPathSubDirectory() {
        givenS3_whenReference_thenValidateAndRetrieveFile("/rootPath0", "/dir0/dir1");
    }

    @Test
    public void givenS3_whenReference_thenValidateAndRetrieveFile_withSubDirectory() {
        givenS3_whenReference_thenValidateAndRetrieveFile("", "/dir0/dir1");
    }

    @Test
    public void givenS3_whenReference_thenValidateAndRetrieveFile_only() {
        givenS3_whenReference_thenValidateAndRetrieveFile("", "");
    }

    private void givenS3_whenReference_thenValidateAndRetrieveFile(String rootPath, String subDirectory) {
        loadPlugin(endPointS3, region, key, secret, bucket, rootPath);

        FileStorageRequest fileStorageRequest = createFileStorageRequest(subDirectory);

        FileStorageWorkingSubset fileStorageWorkingSubset = new FileStorageWorkingSubset(Collections.singletonList(
            fileStorageRequest));
        // Store file to S3
        s3OnlineStorage.store(fileStorageWorkingSubset, new IStorageProgressManager() {

            @Override
            public void storageSucceed(FileStorageRequest fileReferenceRequest, URL storedUrl, Long fileSize) {
            }

            @Override
            public void storageSucceedWithPendingActionRemaining(FileStorageRequest fileReferenceRequest,
                                                                 URL storedUrl,
                                                                 Long fileSize,
                                                                 Boolean notifyAdministrators) {

            }

            @Override
            public void storagePendingActionSucceed(String storedUrl) {

            }

            @Override
            public void storageFailed(FileStorageRequest fileReferenceRequest, String cause) {
            }
        });

        // Retrieve file from S3
        FileReference fileReference = createFileReference(fileStorageRequest, rootPath);

        // Validate reference
        Assert.assertTrue(String.format("Invalid URL %s", fileReference.getLocation().getUrl()),
                          s3OnlineStorage.isValidUrl(fileReference.getLocation().getUrl(), new HashSet<>()));
        // Get file from as input stream
        InputStream inputStream = s3OnlineStorage.retrieve(fileReference);
        Assert.assertNotNull(inputStream);

        // Delete file from S3
        FileDeletionRequest fileDeletionRequest = createFileDeletionRequest(fileStorageRequest, rootPath);
        FileDeletionWorkingSubset fileDeletionWorkingSubset = new FileDeletionWorkingSubset(Collections.singletonList(
            fileDeletionRequest));
        s3OnlineStorage.delete(fileDeletionWorkingSubset, new IDeletionProgressManager() {

            @Override
            public void deletionSucceed(FileDeletionRequest fileDeletionRequest) {
                System.out.println("Success");
            }

            @Override
            public void deletionFailed(FileDeletionRequest fileDeletionRequest, String s) {
                System.out.println("error");
            }
        });

        // Retrieve file from S3
        try {
            s3OnlineStorage.retrieve(fileReference);

            Assert.fail("File always exists");
        } catch (Exception exc) {
        }
    }

    private FileStorageRequest createFileStorageRequest(String subDirectory) {
        FileStorageRequest fileStorageRequest = new FileStorageRequest();
        fileStorageRequest.setOriginUrl("file:./src/test/resources/small.txt");
        fileStorageRequest.setStorageSubDirectory(subDirectory);

        FileReferenceMetaInfo fileReferenceMetaInfo = new FileReferenceMetaInfo();
        fileReferenceMetaInfo.setFileName("small.txt");
        fileReferenceMetaInfo.setAlgorithm("MD5");
        fileReferenceMetaInfo.setMimeType(MimeType.valueOf("text/plain"));
        fileReferenceMetaInfo.setChecksum("706126bf6d8553708227dba90694e81c");

        fileStorageRequest.setMetaInfo(fileReferenceMetaInfo);

        return fileStorageRequest;
    }

    private FileReference createFileReference(FileStorageRequest fileStorageRequest, String rootPath) {
        FileReferenceMetaInfo fileReferenceMetaInfo = new FileReferenceMetaInfo();
        fileReferenceMetaInfo.setFileName(fileStorageRequest.getMetaInfo().getFileName());
        fileReferenceMetaInfo.setAlgorithm(fileStorageRequest.getMetaInfo().getAlgorithm());
        fileReferenceMetaInfo.setMimeType(fileStorageRequest.getMetaInfo().getMimeType());
        fileReferenceMetaInfo.setChecksum(fileStorageRequest.getMetaInfo().getChecksum());

        FileLocation fileLocation = new FileLocation();
        fileLocation.setUrl(buildFileLocationUrl(fileStorageRequest, rootPath));

        return new FileReference("regards", fileReferenceMetaInfo, fileLocation);
    }

    private FileDeletionRequest createFileDeletionRequest(FileStorageRequest fileStorageRequest, String rootPath) {
        FileDeletionRequest fileDeletionRequest = new FileDeletionRequest();
        fileDeletionRequest.setJobId("JOB_ID");

        fileDeletionRequest.setFileReference(createFileReference(fileStorageRequest, rootPath));

        return fileDeletionRequest;
    }

    private String buildFileLocationUrl(FileStorageRequest fileStorageRequest, String rootPath) {
        return endPointS3 + File.separator + bucket + Paths.get(rootPath, fileStorageRequest.getStorageSubDirectory())
                                                           .resolve(fileStorageRequest.getMetaInfo().getChecksum());
    }
}
