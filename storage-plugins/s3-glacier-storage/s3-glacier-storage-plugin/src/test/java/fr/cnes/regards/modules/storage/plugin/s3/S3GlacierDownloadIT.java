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

import fr.cnes.regards.modules.fileaccess.plugin.domain.FileStorageWorkingSubset;
import fr.cnes.regards.modules.fileaccess.plugin.domain.NearlineDownloadException;
import fr.cnes.regards.modules.fileaccess.plugin.domain.NearlineFileNotAvailableException;
import fr.cnes.regards.modules.filecatalog.dto.FileReferenceWithoutOwnersDto;
import fr.cnes.regards.modules.filecatalog.dto.request.FileStorageRequestAggregationDto;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;

/**
 * Test class for {@link  S3Glacier#download(FileReferenceWithoutOwnersDto)}
 *
 * @author Thomas GUILLOU
 **/
@SpringBootTest
public class S3GlacierDownloadIT extends AbstractS3GlacierIT {

    /**
     * Test on mocked S3 Client (see MockedS3Client) with a isFileAvailable method returns :
     * <ul>
     *     <li>1. RestorationStatus.RESTORE_PENDING</li>
     *     <li>2. RestorationStatus.RESTORE_PENDING</li>
     *     <li>3. RestorationStatus.AVAILABLE</li>
     * </ul>
     */
    @Test
    public void test_download() throws IOException {
        // Given
        loadPlugin(endPoint, region, key, secret, bucket, ROOT_PATH);

        String fileChecksum = "aaf14d43dbfb6c33244ec1a25531cb00";
        FileStorageRequestAggregationDto request1 = createFileStorageRequestAggregation("",
                                                                                        "bigFile1.txt",
                                                                                        fileChecksum);
        FileStorageWorkingSubset workingSet = new FileStorageWorkingSubset(List.of(request1));
        AbstractS3GlacierIT.TestStorageProgressManager storageProgressManager = new AbstractS3GlacierIT.TestStorageProgressManager();
        s3Glacier.store(workingSet, storageProgressManager);
        // Check the file is stored in S3 server
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> storageProgressManager.countAllReports() == 1);

        Assertions.assertEquals(1, storageProgressManager.getStorageSucceed().size(), "There should be one success");
        Assertions.assertEquals(createExpectedURL(fileChecksum),
                                storageProgressManager.getStorageSucceed().get(0),
                                "The success URL is not the expected one");
        Assertions.assertEquals(0, storageProgressManager.getStorageSucceedWithPendingAction().size());
        Assertions.assertEquals(0, storageProgressManager.getStorageFailed().size());

        // Create file reference for S3 server
        FileReferenceWithoutOwnersDto fileReference = createFileReference(request1, ROOT_PATH);
        // Validate reference
        Assert.assertTrue(String.format("Invalid URL %s", fileReference.getLocation().getUrl()),
                          s3Glacier.isValidUrl(fileReference.getLocation().getUrl(), new HashSet<>()));
        Assert.assertEquals("Invalid file size", 22949L, fileReference.getMetaInfo().getFileSize().longValue());

        // When : download fails because restoration is pending (1 time)
        NearlineFileNotAvailableException exception = Assertions.assertThrows(NearlineFileNotAvailableException.class,
                                                                              () -> s3Glacier.download(fileReference));
        // Then
        Assertions.assertTrue(exception.getMessage().contains("pending"));

        // When : download fails because restoration is pending (2 times)
        exception = Assertions.assertThrows(NearlineFileNotAvailableException.class,
                                            () -> s3Glacier.download(fileReference));
        // Then
        Assertions.assertTrue(exception.getMessage().contains("pending"));

        // When : download success because the file is restored (3 times)
        InputStream inputStream = null;
        try {
            inputStream = s3Glacier.download(fileReference);

            // Then
            Assertions.assertNotNull(inputStream);
            inputStream.close();
        } catch (NearlineFileNotAvailableException | NearlineDownloadException e) {
            // Then
            Assertions.fail("File is supposed to be available", e);
        }
    }

    @Test
    public void test_download_with_file_not_available() {
        // Given
        FileStorageRequestAggregationDto request1 = createFileStorageRequestAggregation("",
                                                                                        "bigFile1.txt",
                                                                                        "aaf14d43dbfb6c33244ec1a25531cb00");
        FileReferenceWithoutOwnersDto fileReference = createFileReference(request1, ROOT_PATH);
        loadPlugin(endPoint,
                   region,
                   key,
                   secret,
                   bucket,
                   ROOT_PATH,
                   AbstractS3GlacierIT.MockedS3ClientType.MockedS3ClientWithNoFileAvailable,
                   false,
                   false);
        // When
        NearlineFileNotAvailableException exception = Assertions.assertThrows(NearlineFileNotAvailableException.class,
                                                                              () -> s3Glacier.download(fileReference));
        // Then
        Assertions.assertTrue(exception.getMessage().contains("not available"));
    }

}
