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
package fr.cnes.regards.modules.storage.plugin.s3.utils;

import fr.cnes.regards.framework.s3.client.S3HighLevelReactiveClient;
import fr.cnes.regards.framework.s3.domain.GlacierFileStatus;
import fr.cnes.regards.framework.s3.domain.RestorationStatus;
import fr.cnes.regards.framework.s3.domain.StorageConfig;
import fr.cnes.regards.framework.s3.exception.S3ClientException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.model.InvalidObjectStateException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.RestoreObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.annotation.Nullable;
import java.time.ZonedDateTime;

/**
 * Test SGlacierUtils
 *
 * @author Stephane Cortine
 */
public class S3GlacierUtilsTest {

    private S3HighLevelReactiveClient s3ClientMock;

    private ArgumentCaptor<Integer> captorAvailabilityDays;

    @Before
    public void init() {
        s3ClientMock = Mockito.mock(S3HighLevelReactiveClient.class);
        captorAvailabilityDays = ArgumentCaptor.forClass(Integer.class);
    }

    /**
     * Method {@link S3HighLevelReactiveClient#isFileAvailable(StorageConfig, String, String)} returns AVAILABLE.
     * Method {@link S3GlacierUtils#restore(S3HighLevelReactiveClient, StorageConfig, String, String, Integer)}
     * returns FILE_AVAILABLE
     */
    @Test
    public void test_restore_FILE_AVAILABLE_after_AVAILABLE() {
        // Given
        GlacierFileStatus glacierFileStatus = new GlacierFileStatus(RestorationStatus.AVAILABLE,
                                                                    10L,
                                                                    ZonedDateTime.now());

        Mockito.when(s3ClientMock.isFileAvailable(Mockito.any(), Mockito.any(), Mockito.any()))
               .thenReturn(Mono.just(glacierFileStatus));

        // When
        RestoreResponse restoreResponse = S3GlacierUtils.restore(s3ClientMock, null, "", "", 24);

        // Then
        Mockito.verify(s3ClientMock, Mockito.never()).restore(Mockito.any(), Mockito.any(), Mockito.any());

        Assert.assertNotNull(restoreResponse);
        Assert.assertTrue(RestoreStatus.FILE_AVAILABLE == restoreResponse.status());
    }

    /**
     * Method {@link S3HighLevelReactiveClient#isFileAvailable(StorageConfig, String, String)} returns RESTORE_PENDING.
     * Method {@link S3GlacierUtils#restore(S3HighLevelReactiveClient, StorageConfig, String, String, Integer)}
     * returns SUCCESS
     */
    @Test
    public void test_restore_SUCCESS_after_RESTORE_PENDING() {
        // Given
        GlacierFileStatus glacierFileStatus = new GlacierFileStatus(RestorationStatus.RESTORE_PENDING,
                                                                    10L,
                                                                    ZonedDateTime.now());

        Mockito.when(s3ClientMock.isFileAvailable(Mockito.any(), Mockito.any(), Mockito.any()))
               .thenReturn(Mono.just(glacierFileStatus));

        // When
        RestoreResponse restoreResponse = S3GlacierUtils.restore(s3ClientMock, null, "", "", 24);

        // Then
        Mockito.verify(s3ClientMock, Mockito.never()).restore(Mockito.any(), Mockito.any(), Mockito.any());

        Assert.assertNotNull(restoreResponse);
        Assert.assertTrue(RestoreStatus.SUCCESS == restoreResponse.status());
    }

    /**
     * Method {@link S3HighLevelReactiveClient#restore(StorageConfig, String, Integer)} returns RESTORE_ALREADY_IN_PROGRESS.
     */
    @Test
    public void test_restore_SUCCESS_after_RESTORE_ALREADY_IN_PROGRESS() {
        // When
        Mockito.when(s3ClientMock.restore(Mockito.any(), Mockito.any(), captorAvailabilityDays.capture()))
               .thenReturn(Mono.error(S3Exception.builder().message("RestoreAlreadyInProgress").build()));

        test_restore(RestoreStatus.SUCCESS, captorAvailabilityDays, 5, 1);
    }

    /**
     * Method {@link S3HighLevelReactiveClient#restore(StorageConfig, String, Integer)} returns S3Exception.
     */
    @Test
    public void test_restore_CLIENT_EXCEPTION_after_S3Exception() {
        // When
        Mockito.when(s3ClientMock.restore(Mockito.any(), Mockito.any(), captorAvailabilityDays.capture()))
               .thenReturn(Mono.error(S3Exception.builder().message("").build()));

        test_restore(RestoreStatus.CLIENT_EXCEPTION, captorAvailabilityDays, 48, 2);
    }

    /**
     * Method {@link S3HighLevelReactiveClient#restore(StorageConfig, String, Integer)} returns WRONG_STORAGE_CLASS.
     */
    @Test
    public void test_restore_FILE_AVAILABLE_after_WRONG_STORAGE_CLASS() {
        // When
        Mockito.when(s3ClientMock.restore(Mockito.any(), Mockito.any(), captorAvailabilityDays.capture()))
               .thenReturn(Mono.error(InvalidObjectStateException.builder().build()));

        test_restore(RestoreStatus.FILE_AVAILABLE, captorAvailabilityDays, 50, 3);
    }

    /**
     * Method {@link S3HighLevelReactiveClient#restore(StorageConfig, String, Integer)} returns NoSuchKeyException.
     */
    @Test
    public void test_restore_KEY_NOT_FOUND_after_NoSuchKeyException() {
        // When
        Mockito.when(s3ClientMock.restore(Mockito.any(), Mockito.any(), captorAvailabilityDays.capture()))
               .thenReturn(Mono.error(NoSuchKeyException.builder().build()));

        test_restore(RestoreStatus.KEY_NOT_FOUND, captorAvailabilityDays, 0, null);
    }

    /**
     * Method {@link S3HighLevelReactiveClient#restore(StorageConfig, String, Integer)} returns S3ClientException.
     */
    @Test
    public void test_restore_CLIENT_EXCEPTION_after_S3ClientException() {
        // When
        Mockito.when(s3ClientMock.restore(Mockito.any(), Mockito.any(), captorAvailabilityDays.capture()))
               .thenReturn(Mono.error(new S3ClientException("")));

        test_restore(RestoreStatus.CLIENT_EXCEPTION, captorAvailabilityDays, null, null);
    }

    @Test
    public void test_restore_SUCCESS() {
        // When
        Mockito.when(s3ClientMock.restore(Mockito.any(), Mockito.any(), captorAvailabilityDays.capture()))
               .thenReturn(Mono.just(RestoreObjectResponse.builder().build()));

        test_restore(RestoreStatus.SUCCESS, captorAvailabilityDays, 24, 1);
    }

    /**
     * Test restore method and method to convert availability hours in availability days
     */
    private void test_restore(RestoreStatus restoreStatus,
                              ArgumentCaptor<Integer> captorDays,
                              @Nullable Integer availabilityHours,
                              @Nullable Integer expectedAvailabilityDays) {
        // Given
        Mockito.when(s3ClientMock.isFileAvailable(Mockito.any(), Mockito.any(), Mockito.any()))
               .thenReturn(Mono.just(new GlacierFileStatus(RestorationStatus.NOT_AVAILABLE, 10L, ZonedDateTime.now())));

        // When
        RestoreResponse restoreResponse = S3GlacierUtils.restore(s3ClientMock, null, "", "", availabilityHours);

        // Then
        Mockito.verify(s3ClientMock, Mockito.times(1)).restore(Mockito.any(), Mockito.any(), Mockito.any());

        Assert.assertNotNull(restoreResponse);
        Assert.assertTrue(restoreStatus == restoreResponse.status());

        Assert.assertEquals(expectedAvailabilityDays, captorDays.getValue());
    }

}
