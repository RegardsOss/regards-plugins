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

import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.s3.S3StorageConfiguration;
import fr.cnes.regards.framework.s3.client.S3HighLevelReactiveClient;
import fr.cnes.regards.framework.s3.domain.StorageCommandResult;
import fr.cnes.regards.framework.s3.domain.StorageConfig;
import fr.cnes.regards.modules.fileaccess.dto.request.FileStorageRequestAggregationDto;
import fr.cnes.regards.modules.fileaccess.plugin.domain.IStorageProgressManager;
import fr.cnes.regards.modules.storage.s3.common.AbstractS3Storage;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.net.URL;

/**
 * Mock class to test S3Glacier locks with each tasks.
 *
 * @author Sébastien Binda
 **/
public class S3GlacierMock extends S3Glacier {

    private long fileSize = 0L;

    public <T> S3GlacierMock(LockServiceMock lockServiceMock,
                             S3StorageConfiguration s3settings,
                             int smallFileMaxSize,
                             String rootPath,
                             String url,
                             String bucket,
                             IRuntimeTenantResolver runtimeTenantResolver)
        throws NoSuchFieldException, IllegalAccessException {
        super();
        // Make private fields accessible to override them with mocks
        Field lockServiceField = S3Glacier.class.getDeclaredField("lockService");
        lockServiceField.setAccessible(true);
        lockServiceField.set(this, lockServiceMock);
        Field s3settingsField = AbstractS3Storage.class.getDeclaredField("s3StorageSettings");
        s3settingsField.setAccessible(true);
        s3settingsField.set(this, s3settings);
        Field smallFileMaxSizeField = S3Glacier.class.getDeclaredField("smallFileMaxSize");
        smallFileMaxSizeField.setAccessible(true);
        smallFileMaxSizeField.set(this, smallFileMaxSize);
        Field rootPathField = AbstractS3Storage.class.getDeclaredField("rootPath");
        rootPathField.setAccessible(true);
        rootPathField.set(this, rootPath);
        Field urlField = AbstractS3Storage.class.getDeclaredField("endpoint");
        urlField.setAccessible(true);
        urlField.set(this, url);
        Field bucketField = AbstractS3Storage.class.getDeclaredField("bucket");
        bucketField.setAccessible(true);
        bucketField.set(this, bucket);
        Field runtimeTenantResolverField = AbstractS3Storage.class.getDeclaredField("runtimeTenantResolver");
        runtimeTenantResolverField.setAccessible(true);
        runtimeTenantResolverField.set(this, runtimeTenantResolver);
        Field workspaceField = S3Glacier.class.getDeclaredField("workspacePath");
        workspaceField.setAccessible(true);
        workspaceField.set(this, "/tmp");
        this.storageConfiguration = Mockito.mock(StorageConfig.class);
        Mockito.when(this.storageConfiguration.entryKey(Mockito.any())).thenReturn("");
    }

    @Override
    protected long getFileSize(URL sourceUrl) {
        return fileSize;
    }

    @Override
    protected S3HighLevelReactiveClient createS3Client() {
        S3HighLevelReactiveClient s3clientMock = Mockito.mock(S3HighLevelReactiveClient.class);
        Mockito.when(s3clientMock.write(Mockito.any()))
               .thenReturn(Mono.just(new StorageCommandResult.WriteSuccess(null, 50, null)));
        Mockito.when(s3clientMock.delete(Mockito.any()))
               .thenReturn(Mono.just(new StorageCommandResult.DeleteSuccess(null)));
        return s3clientMock;
    }

    public void mockBigFile() {
        fileSize = 500000000L;
    }

    public void mockSmallFile() {
        fileSize = 10L;
    }

    @Override
    protected void handleStoreRequest(FileStorageRequestAggregationDto request,
                                      IStorageProgressManager progressManager) {
        // Nothing to do
    }
}
