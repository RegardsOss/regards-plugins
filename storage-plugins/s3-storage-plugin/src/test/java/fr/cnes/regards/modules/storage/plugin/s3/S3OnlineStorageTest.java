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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Instantiate S3 plugin to test with local minio deployed server
 *
 * @author Marc SORDI, Stephane CORTINE
 */
@RunWith(RegardsSpringRunner.class)
@SpringBootTest
@SpringBootConfiguration
public class S3OnlineStorageTest {

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

    private static final String TENANT = "TENANT";

    @Before
    public void prepare() {
        // Initialize plugin environment
        PluginUtils.setup();
    }

    private void loadPlugin(String endpoint, String region, String key, String secret, String bucket, String rootPath) {
        // Set plugin configuration
        Collection<IPluginParam> parameters = IPluginParam.set(IPluginParam.build(S3OnlineStorage.S3_SERVER_ENDPOINT_PARAM_NAME,
                                                                                  endpoint),
                                                               IPluginParam.build(S3OnlineStorage.S3_SERVER_REGION_PARAM_NAME,
                                                                                  region),
                                                               IPluginParam.build(S3OnlineStorage.S3_SERVER_KEY_PARAM_NAME,
                                                                                  key),
                                                               IPluginParam.build(S3OnlineStorage.S3_SERVER_SECRET_PARAM_NAME,
                                                                                  secret),
                                                               IPluginParam.build(S3OnlineStorage.S3_SERVER_BUCKET_PARAM_NAME,
                                                                                  bucket),
                                                               IPluginParam.build(S3OnlineStorage.S3_SERVER_ROOT_PATH_PARAM_NAME,
                                                                                  rootPath));

        PluginConfiguration pluginConfiguration = PluginConfiguration.build(S3OnlineStorage.class,
                                                                            "S3 plugin",
                                                                            parameters);
        // Load plugin
        try {
            s3OnlineStorage = PluginUtils.getPlugin(pluginConfiguration, new HashMap<>());
        } catch (NotAvailablePluginConfigurationException e) {
            Assert.fail(e.getMessage());
        }
        Assert.assertNotNull(s3OnlineStorage);

        IRuntimeTenantResolver resolverMock = Mockito.mock(IRuntimeTenantResolver.class);
        Mockito.when(resolverMock.getTenant()).thenReturn(TENANT);
        ReflectionTestUtils.setField(s3OnlineStorage, "runtimeTenantResolver", resolverMock);
    }

    @Test
    public void givenS3_whenReference_thenValidateAndRetrieveFile() {

        loadPlugin(endPointS3, region, key, secret, bucket, "");

        FileStorageRequest fileStorageRequest = createFileStorageRequest();

        FileStorageWorkingSubset fileStorageWorkingSubset = new FileStorageWorkingSubset(Collections.singletonList(
            fileStorageRequest));
        // Store file to S3
        s3OnlineStorage.store(fileStorageWorkingSubset, new IStorageProgressManager() {

            @Override
            public void storageSucceed(FileStorageRequest fileReferenceRequest, URL storedUrl, Long fileSize) {
            }

            @Override
            public void storageFailed(FileStorageRequest fileReferenceRequest, String cause) {
            }
        });

        // Retrieve file from S3
        FileReference fileReference = new FileReference("regards",
                                                        new FileReferenceMetaInfo(fileStorageRequest.getMetaInfo()
                                                                                                    .getChecksum(),
                                                                                  fileStorageRequest.getMetaInfo()
                                                                                                    .getAlgorithm(),
                                                                                  fileStorageRequest.getMetaInfo()
                                                                                                    .getFileName(),
                                                                                  fileStorageRequest.getMetaInfo()
                                                                                                    .getFileSize(),
                                                                                  MimeType.valueOf("text/plain")),
                                                        new FileLocation("S3",
                                                                         endPointS3
                                                                         + File.separator
                                                                         + bucket
                                                                         + File.separator
                                                                         + fileStorageRequest.getMetaInfo()
                                                                                             .getChecksum()));

        // Validate reference
        Assert.assertTrue(String.format("Invalid URL %s", fileReference.getLocation().getUrl()),
                          s3OnlineStorage.isValidUrl(fileReference.getLocation().getUrl(), new HashSet<>()));
        // Get file from as input stream
        InputStream inputStream = s3OnlineStorage.retrieve(fileReference);
        Assert.assertNotNull(inputStream);

        // Delete file from S3
        FileDeletionRequest fileDeletionRequest = createFileDeletionRequest();
        FileDeletionWorkingSubset fileDeletionWorkingSubset = new FileDeletionWorkingSubset(Collections.singletonList(
            fileDeletionRequest));
        s3OnlineStorage.delete(fileDeletionWorkingSubset, new IDeletionProgressManager() {

            @Override
            public void deletionSucceed(FileDeletionRequest fileDeletionRequest) {
            }

            @Override
            public void deletionFailed(FileDeletionRequest fileDeletionRequest, String s) {
            }
        });

        // Retrieve file from S3
        try {
            s3OnlineStorage.retrieve(fileReference);

            Assert.fail("File always exists");
        } catch (Exception exc) {
        }
    }

    private FileStorageRequest createFileStorageRequest() {
        FileStorageRequest fileStorageRequest = new FileStorageRequest();
        fileStorageRequest.setOriginUrl("file:./src/test/resources/small.txt");

        FileReferenceMetaInfo fileReferenceMetaInfo = new FileReferenceMetaInfo();
        fileReferenceMetaInfo.setFileName("small.txt");
        fileReferenceMetaInfo.setAlgorithm("MD5");
        fileReferenceMetaInfo.setMimeType(MimeType.valueOf("text/plain"));
        fileReferenceMetaInfo.setChecksum("706126bf6d8553708227dba90694e81c");

        fileStorageRequest.setMetaInfo(fileReferenceMetaInfo);

        return fileStorageRequest;
    }

    private FileDeletionRequest createFileDeletionRequest() {
        FileDeletionRequest fileDeletionRequest = new FileDeletionRequest();
        fileDeletionRequest.setJobId("JOB_ID");

        FileReferenceMetaInfo fileReferenceMetaInfo = new FileReferenceMetaInfo();
        fileReferenceMetaInfo.setFileName("small.txt");
        fileReferenceMetaInfo.setAlgorithm("MD5");
        fileReferenceMetaInfo.setMimeType(MimeType.valueOf("text/plain"));
        fileReferenceMetaInfo.setChecksum("706126bf6d8553708227dba90694e81c");

        FileLocation fileLocation = new FileLocation();
        fileLocation.setUrl(endPointS3
                            + File.pathSeparator
                            + bucket
                            + File.pathSeparator
                            + fileReferenceMetaInfo.getChecksum());

        FileReference fileReference = new FileReference();
        fileReference.setMetaInfo(fileReferenceMetaInfo);
        fileReference.setLocation(fileLocation);

        fileDeletionRequest.setFileReference(fileReference);

        return fileDeletionRequest;
    }
}
