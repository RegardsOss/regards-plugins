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

import fr.cnes.regards.framework.test.report.annotation.Purpose;
import fr.cnes.regards.framework.utils.file.ChecksumUtils;
import fr.cnes.regards.modules.fileaccess.dto.FileReferenceWithoutOwnersDto;
import fr.cnes.regards.modules.fileaccess.dto.request.FileStorageRequestAggregationDto;
import fr.cnes.regards.modules.fileaccess.plugin.domain.FileStorageWorkingSubset;
import fr.cnes.regards.modules.fileaccess.plugin.domain.IStorageProgressManager;
import fr.cnes.regards.modules.storage.plugin.smallfiles.utils.SmallFilesUtils;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Test class for {@link  S3Glacier#store(FileStorageWorkingSubset, IStorageProgressManager)}
 *
 * @author Thibaud Michaudel
 **/
@SpringBootTest
@SpringBootConfiguration
public class S3GlacierStoreIT extends AbstractS3GlacierIT {

    @Test
    @Purpose("Test that a big file is correctly stored on the S3 server when sent through the plugin")
    public void test_submit_big_file() {
        // Given
        loadPlugin(endPoint, region, key, secret, bucket, ROOT_PATH);

        // When
        String fileChecksum = "aaf14d43dbfb6c33244ec1a25531cb00";
        FileStorageRequestAggregationDto request1 = createFileStorageRequestAggregation("",
                                                                                        "bigFile1.txt",
                                                                                        fileChecksum);
        FileStorageWorkingSubset workingSet = new FileStorageWorkingSubset(List.of(request1));
        TestStorageProgressManager storageProgressManager = new TestStorageProgressManager();
        s3Glacier.store(workingSet, storageProgressManager);

        // Then
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> storageProgressManager.countAllReports() == 1);

        Assertions.assertEquals(1, storageProgressManager.getStorageSucceed().size(), "There should be one success");
        Assertions.assertEquals(createExpectedURL(fileChecksum),
                                storageProgressManager.getStorageSucceed().get(0),
                                "The success URL is not the expected one");
        Assertions.assertEquals(0, storageProgressManager.getStorageSucceedWithPendingAction().size());
        Assertions.assertEquals(0, storageProgressManager.getStorageFailed().size());

        // Create file reference for S3 server
        FileReferenceWithoutOwnersDto FileReferenceWithoutOwnersDto = createFileReference(request1, ROOT_PATH);
        // Validate reference
        Assert.assertTrue(String.format("Invalid URL %s", FileReferenceWithoutOwnersDto.getLocation().getUrl()),
                          s3Glacier.isValidUrl(FileReferenceWithoutOwnersDto.getLocation().getUrl(), new HashSet<>()));
        Assert.assertEquals("Invalid file size",
                            22949L,
                            FileReferenceWithoutOwnersDto.getMetaInfo().getFileSize().longValue());

        // Get file as input stream from S3 server
        try {
            InputStream inputStream = downloadFromS3(FileReferenceWithoutOwnersDto.getLocation().getUrl());
            Assert.assertNotNull(inputStream);
            inputStream.close();
        } catch (FileNotFoundException e) {
            Assert.fail("Test Failure : file isn't stored in the S3 server");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Purpose("Test that a small file is correctly stored in the the zip building workspace when sent through the plugin")
    public void test_submit_small_file() throws NoSuchAlgorithmException, IOException {
        // Given
        String rootPath = ROOT_PATH + File.separator + "deep/node";
        loadPlugin(endPoint, region, key, secret, bucket, rootPath);

        // When
        String nodeName = "deep/dir/testNode";
        String file1Name = "smallFile1.txt";
        String file1Checksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";

        FileStorageRequestAggregationDto request1 = createFileStorageRequestAggregation(nodeName,
                                                                                        file1Name,
                                                                                        file1Checksum);
        FileStorageWorkingSubset workingSet = new FileStorageWorkingSubset(List.of(request1));
        TestStorageProgressManager storageProgressManager = new TestStorageProgressManager();
        s3Glacier.store(workingSet, storageProgressManager);

        //Then
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> storageProgressManager.countAllReports() == 1);

        File nodeDir = Paths.get(workspace.getRoot().getAbsolutePath(), S3Glacier.ZIP_DIR, rootPath, nodeName).toFile();

        Assertions.assertEquals(1, nodeDir.list().length, "There should be one directory, the _current");
        File currentDir = nodeDir.listFiles()[0];
        Assertions.assertTrue(currentDir.getName().endsWith(S3Glacier.CURRENT_ARCHIVE_SUFFIX),
                              "The directory should be suffixed with the current suffix");
        Assertions.assertEquals(1, currentDir.list().length, "The file we submitted should be the only one");
        Assertions.assertEquals(file1Name, currentDir.list()[0], "The file should be the one we submitted");
        Assertions.assertEquals(file1Checksum,
                                ChecksumUtils.computeHexChecksum(currentDir.listFiles()[0].toPath(),
                                                                 S3Glacier.MD5_CHECKSUM),
                                "The file checksum should be the one we submitted");

        Assertions.assertEquals(1,
                                storageProgressManager.getStorageSucceedWithPendingAction().size(),
                                "There should be 1 success with pending action");
        String archiveName = SmallFilesUtils.removeSuffix(currentDir.getName());
        Assertions.assertEquals(createExpectedURL(nodeName, SmallFilesUtils.removePrefix(archiveName), file1Name),
                                storageProgressManager.getStorageSucceedWithPendingAction().get(0),
                                "The success url is not the expected one");
        Assertions.assertEquals(0, storageProgressManager.getStorageFailed().size());
        Assertions.assertEquals(0, storageProgressManager.getStorageSucceed().size());
    }

    @Test
    @Purpose("Test that a small file is correctly stored in the the zip building workspace when sent through the plugin")
    public void test_submit_small_file_already_exists() {
        // Given
        loadPlugin(endPoint, region, key, secret, bucket, ROOT_PATH);

        String nodeName = "deep/dir/testNode";
        String file1Name = "smallFile1.txt";
        String file1Checksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        FileStorageRequestAggregationDto request1 = createFileStorageRequestAggregation(nodeName,
                                                                                        file1Name,
                                                                                        file1Checksum);
        FileStorageWorkingSubset workingSet = new FileStorageWorkingSubset(List.of(request1));
        TestStorageProgressManager storageProgressManager = new TestStorageProgressManager();
        s3Glacier.store(workingSet, storageProgressManager);
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> storageProgressManager.countAllReports() == 1);

        // When
        FileStorageRequestAggregationDto request2 = createFileStorageRequestAggregation(nodeName,
                                                                                        "smallFile2.txt",
                                                                                        "0e7de3ed4befa2c4a42ec404520d99ff");
        request2.getMetaInfo().setFileName(file1Name);
        FileStorageWorkingSubset workingSet2 = new FileStorageWorkingSubset(List.of(request2));
        s3Glacier.store(workingSet2, storageProgressManager);
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> storageProgressManager.countAllReports() == 2);

        FileStorageRequestAggregationDto request3 = createFileStorageRequestAggregation(nodeName,
                                                                                        "smallFile3.txt",
                                                                                        "00a0b9954478ebd589d7b7c698d71d57");
        request3.getMetaInfo().setFileName(file1Name);
        FileStorageWorkingSubset workingSet3 = new FileStorageWorkingSubset(List.of(request3));
        s3Glacier.store(workingSet3, storageProgressManager);
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> storageProgressManager.countAllReports() == 3);

        // Then
        File nodeDir = Paths.get(workspace.getRoot().getAbsolutePath(), S3Glacier.ZIP_DIR, ROOT_PATH, nodeName)
                            .toFile();

        Assertions.assertEquals(1, nodeDir.list().length, "There should be one directory, the _current");
        File currentDir = nodeDir.listFiles()[0];
        Assertions.assertTrue(currentDir.getName().endsWith(S3Glacier.CURRENT_ARCHIVE_SUFFIX),
                              "The directory should be suffixed with the current suffix");

        List<FileNameAndChecksum> actualFiles = Arrays.stream(currentDir.listFiles()).map(f -> {
            try {
                return new FileNameAndChecksum(f.getName(),
                                               ChecksumUtils.computeHexChecksum(f.toPath(), S3Glacier.MD5_CHECKSUM));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).toList();
        List<FileNameAndChecksum> expectedFiles = List.of(new FileNameAndChecksum("smallFile1.txt",
                                                                                  "83e93a40da8ad9e6ed0ab9ef852e7e39"),
                                                          new FileNameAndChecksum("smallFile1_2.txt",
                                                                                  "0e7de3ed4befa2c4a42ec404520d99ff"),
                                                          new FileNameAndChecksum("smallFile1_3.txt",
                                                                                  "00a0b9954478ebd589d7b7c698d71d57"));

        Assertions.assertTrue(actualFiles.containsAll(expectedFiles), "The stored files don't match the expected ones");
        Assertions.assertTrue(expectedFiles.containsAll(actualFiles), "The stored files don't match the expected ones");

        Assertions.assertEquals(3,
                                storageProgressManager.getStorageSucceedWithPendingAction().size(),
                                "There should be 3 success with pending action");

        String archiveName = SmallFilesUtils.removeSuffix(currentDir.getName());
        List<URL> expectedUrls = List.of(createExpectedURL(nodeName,
                                                           SmallFilesUtils.removePrefix(archiveName),
                                                           "smallFile1.txt"),
                                         createExpectedURL(nodeName,
                                                           SmallFilesUtils.removePrefix(archiveName),
                                                           "smallFile1_2.txt"),
                                         createExpectedURL(nodeName,
                                                           SmallFilesUtils.removePrefix(archiveName),
                                                           "smallFile1_3.txt"));
        Assertions.assertTrue(storageProgressManager.getStorageSucceedWithPendingAction().containsAll(expectedUrls));
        Assertions.assertTrue(expectedUrls.containsAll(storageProgressManager.getStorageSucceedWithPendingAction()));
        Assertions.assertEquals(0, storageProgressManager.getStorageFailed().size());
        Assertions.assertEquals(0, storageProgressManager.getStorageSucceed().size());

    }

    @Test
    @Purpose("Test that a small file is correctly stored in the the zip building workspace when sent through the plugin")
    public void test_submit_small_file_already_exists_different_checksum()
        throws NoSuchAlgorithmException, IOException {
        // Given
        loadPlugin(endPoint, region, key, secret, bucket, ROOT_PATH);

        String nodeName = "deep/dir/testNode";
        String file1Name = "smallFile1.txt";
        String file1Checksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        FileStorageRequestAggregationDto request1 = createFileStorageRequestAggregation(nodeName,
                                                                                        file1Name,
                                                                                        file1Checksum);
        FileStorageWorkingSubset workingSet = new FileStorageWorkingSubset(List.of(request1));
        TestStorageProgressManager storageProgressManager = new TestStorageProgressManager();
        s3Glacier.store(workingSet, storageProgressManager);
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> storageProgressManager.countAllReports() == 1);
        storageProgressManager.reset();

        // When
        FileStorageRequestAggregationDto request2 = createFileStorageRequestAggregation(nodeName,
                                                                                        file1Name,
                                                                                        file1Checksum);
        FileStorageWorkingSubset workingSet2 = new FileStorageWorkingSubset(List.of(request2));
        s3Glacier.store(workingSet2, storageProgressManager);

        // Then
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> storageProgressManager.countAllReports() == 1);
        File nodeDir = Paths.get(workspace.getRoot().getAbsolutePath(), S3Glacier.ZIP_DIR, ROOT_PATH, nodeName)
                            .toFile();

        Assertions.assertEquals(1, nodeDir.list().length, "There should be one directory, the _current");
        File currentDir = nodeDir.listFiles()[0];
        Assertions.assertTrue(currentDir.getName().endsWith(S3Glacier.CURRENT_ARCHIVE_SUFFIX),
                              "The directory should be suffixed with the current suffix");
        Assertions.assertEquals(1, currentDir.list().length, "The file we submitted should be the only one");
        Assertions.assertEquals(file1Name, currentDir.list()[0], "The file should be the one we submitted");
        Assertions.assertEquals(file1Checksum,
                                ChecksumUtils.computeHexChecksum(currentDir.listFiles()[0].toPath(),
                                                                 S3Glacier.MD5_CHECKSUM),
                                "The file checksum should be the one we submitted");

        if (storageProgressManager.getStorageSucceedWithPendingAction().size() != 1) {
            System.out.println("Success :"
                               + storageProgressManager.getStorageSucceed().size()
                               + " ; "
                               + "Pending : "
                               + storageProgressManager.getStorageSucceedWithPendingAction().size()
                               + " ; "
                               + "FinishedPending : "
                               + storageProgressManager.getStoragePendingActionSucceed().size()
                               + " ; "
                               + "Error : "
                               + storageProgressManager.getStorageFailed().size());
        }
        Assertions.assertEquals(1,
                                storageProgressManager.getStorageSucceedWithPendingAction().size(),
                                "There should be 1 success with pending action");
        String archiveName = SmallFilesUtils.removeSuffix(currentDir.getName());
        Assertions.assertEquals(createExpectedURL(nodeName, SmallFilesUtils.removePrefix(archiveName), file1Name),
                                storageProgressManager.getStorageSucceedWithPendingAction().get(0),
                                "The success url is not the expected one");
        Assertions.assertEquals(0, storageProgressManager.getStorageFailed().size());
        Assertions.assertEquals(0, storageProgressManager.getStorageSucceed().size());

    }

    @Test
    @Purpose("Test that the _current folder is correctly rolled over when it reach the max size")
    public void test_rollover_current_dir() {
        loadPlugin(endPoint, region, key, secret, bucket, ROOT_PATH);

        String nodeName = "deep/dir/testNode";
        FileStorageRequestAggregationDto request1 = createFileStorageRequestAggregation(nodeName,
                                                                                        "smallFile1.txt",
                                                                                        "83e93a40da8ad9e6ed0ab9ef852e7e39");
        FileStorageRequestAggregationDto request2 = createFileStorageRequestAggregation(nodeName,
                                                                                        "smallFile2.txt",
                                                                                        "0e7de3ed4befa2c4a42ec404520d99ff");
        FileStorageRequestAggregationDto request3 = createFileStorageRequestAggregation(nodeName,
                                                                                        "smallFile3.txt",
                                                                                        "00a0b9954478ebd589d7b7c698d71d57");
        FileStorageRequestAggregationDto request4 = createFileStorageRequestAggregation(nodeName,
                                                                                        "smallFile4.txt",
                                                                                        "6169ec97b7dc25d2c25a0216bd4f81b9");
        FileStorageRequestAggregationDto request5 = createFileStorageRequestAggregation(nodeName,
                                                                                        "smallFile5.txt",
                                                                                        "eef5db37af89c5e9c1a3ad587f2dcf5f");
        FileStorageWorkingSubset workingSet = new FileStorageWorkingSubset(List.of(request1,
                                                                                   request2,
                                                                                   request3,
                                                                                   request4,
                                                                                   request5));
        TestStorageProgressManager storageProgressManager = new TestStorageProgressManager();
        s3Glacier.store(workingSet, storageProgressManager);
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> storageProgressManager.countAllReports() == 5);

        File nodeDir = Paths.get(workspace.getRoot().getAbsolutePath(), S3Glacier.ZIP_DIR, ROOT_PATH, nodeName)
                            .toFile();
        Assertions.assertEquals(2,
                                nodeDir.list().length,
                                "There should be two directories in the node, one _current and one that got rolled over");
        File currentDir = Arrays.stream(nodeDir.listFiles())
                                .filter(f -> f.getName().endsWith(S3Glacier.CURRENT_ARCHIVE_SUFFIX))
                                .findFirst()
                                .get();

        File rolledOverDir = Arrays.stream(nodeDir.listFiles())
                                   .filter(f -> !f.getName().endsWith(S3Glacier.CURRENT_ARCHIVE_SUFFIX))
                                   .findFirst()
                                   .get();

        Assertions.assertEquals(4, rolledOverDir.list().length, "Rolled over directories should have 4 files");
        Assertions.assertEquals(1, currentDir.list().length, "Current directory should have 1 file");

        Assertions.assertEquals(5,
                                storageProgressManager.getStorageSucceedWithPendingAction().size(),
                                "There should be 5 success with pending action");
        Assertions.assertEquals(0, storageProgressManager.getStorageFailed().size());
        Assertions.assertEquals(0, storageProgressManager.getStorageSucceed().size());
    }
}
