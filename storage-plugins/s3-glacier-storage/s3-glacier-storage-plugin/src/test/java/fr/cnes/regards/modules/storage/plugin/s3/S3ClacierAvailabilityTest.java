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

import com.google.common.collect.Lists;
import fr.cnes.regards.framework.s3.client.S3HighLevelReactiveClient;
import fr.cnes.regards.framework.s3.domain.GlacierFileStatus;
import fr.cnes.regards.framework.s3.domain.RestorationStatus;
import fr.cnes.regards.modules.filecatalog.dto.availability.NearlineFileStatusDto;
import fr.cnes.regards.modules.storage.domain.database.FileLocation;
import fr.cnes.regards.modules.storage.domain.database.FileReference;
import fr.cnes.regards.modules.storage.domain.database.FileReferenceMetaInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * @author Stephane Cortine
 */
@RunWith(MockitoJUnitRunner.class)
public class S3ClacierAvailabilityTest {

    private final String END_POINT_S3_GLACIER = "http://minio.cs:80";

    private final String BUCKET_S3_GLACIER = "bucket";

    @InjectMocks
    private S3Glacier s3Glacier;

    @Mock
    private S3HighLevelReactiveClient s3Client;

    @Before
    public void init() {
        ReflectionTestUtils.setField(s3Glacier, "endpoint", END_POINT_S3_GLACIER);
        ReflectionTestUtils.setField(s3Glacier, "bucket", BUCKET_S3_GLACIER);
    }

    @Test
    public void test_file_availability_return_available_with_date_expiration() {
        // Given
        ZonedDateTime dateExpiration = ZonedDateTime.now().plusDays(1);

        Mockito.when(s3Client.isFileAvailable(Mockito.any(), Mockito.any(), Mockito.any()))
               .thenReturn(Mono.just(new GlacierFileStatus(RestorationStatus.AVAILABLE, dateExpiration)));

        // When
        NearlineFileStatusDto nearlineFileStatusDto = s3Glacier.checkAvailability(createFakeFileReference());

        // Then
        Assert.assertNotNull(nearlineFileStatusDto);
        Assert.assertTrue(nearlineFileStatusDto.isAvailable());

        Assert.assertNotNull(nearlineFileStatusDto.getExpirationDate());
        Assert.assertTrue(dateExpiration.toOffsetDateTime().isEqual(nearlineFileStatusDto.getExpirationDate()));
    }

    @Test
    public void test_file_availability_return_available_without_date_expiration() {
        // Given
        Mockito.when(s3Client.isFileAvailable(Mockito.any(), Mockito.any(), Mockito.any()))
               .thenReturn(Mono.just(new GlacierFileStatus(RestorationStatus.AVAILABLE, null)));

        // When
        NearlineFileStatusDto nearlineFileStatusDto = s3Glacier.checkAvailability(createFakeFileReference());

        // Then
        Assert.assertNotNull(nearlineFileStatusDto);
        Assert.assertTrue(nearlineFileStatusDto.isAvailable());

        Assert.assertNull(nearlineFileStatusDto.getExpirationDate());
    }

    @Test
    public void test_file_availability_return_not_available() {
        // Given
        Mockito.when(s3Client.isFileAvailable(Mockito.any(), Mockito.any(), Mockito.any()))
               .thenReturn(Mono.just(new GlacierFileStatus(RestorationStatus.NOT_AVAILABLE, null)));

        // When, then
        test_file_availability();
    }

    @Test
    public void test_file_availability_return_restore_pending() {
        // Given
        Mockito.when(s3Client.isFileAvailable(Mockito.any(), Mockito.any(), Mockito.any()))
               .thenReturn(Mono.just(new GlacierFileStatus(RestorationStatus.RESTORE_PENDING, null)));

        // When, then
        test_file_availability();
    }

    @Test
    public void test_file_availability_return_expired() {
        // Given
        Mockito.when(s3Client.isFileAvailable(Mockito.any(), Mockito.any(), Mockito.any()))
               .thenReturn(Mono.just(new GlacierFileStatus(RestorationStatus.EXPIRED, null)));

        // When, then
        test_file_availability();
    }

    public void test_file_availability() {
        // When
        NearlineFileStatusDto nearlineFileStatusDto = s3Glacier.checkAvailability(createFakeFileReference());

        // Then
        Assert.assertNotNull(nearlineFileStatusDto);
        Assert.assertFalse(nearlineFileStatusDto.isAvailable());

        Assert.assertNull(nearlineFileStatusDto.getExpirationDate());
    }

    // ---------------------
    // -- UTILITY METHODS --
    // ---------------------

    private FileReference createFakeFileReference() {
        FileReferenceMetaInfo metaInfo = new FileReferenceMetaInfo(UUID.randomUUID().toString(),
                                                                   "UUID",
                                                                   "file.test",
                                                                   10L,
                                                                   MediaType.APPLICATION_OCTET_STREAM);
        FileLocation location = new FileLocation("S3Glacier",
                                                 END_POINT_S3_GLACIER + "/" + BUCKET_S3_GLACIER + "/file",
                                                 false);
        return new FileReference(Lists.newArrayList("owner"), metaInfo, location);
    }

}
