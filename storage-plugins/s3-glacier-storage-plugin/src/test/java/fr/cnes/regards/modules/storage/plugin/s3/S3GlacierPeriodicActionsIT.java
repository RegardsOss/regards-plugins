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

import fr.cnes.regards.framework.test.report.annotation.Purpose;
import fr.cnes.regards.modules.storage.domain.database.FileLocation;
import fr.cnes.regards.modules.storage.domain.database.FileReference;
import fr.cnes.regards.modules.storage.domain.plugin.IPeriodicActionProgressManager;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Test class for {@link  S3Glacier#runPeriodicAction(IPeriodicActionProgressManager)}
 *
 * @author Thibaud Michaudel
 **/
@SpringBootTest
public class S3GlacierPeriodicActionsIT extends AbstractS3GlacierIT {

    @Test
    @Purpose("Test that an archive is correctly sent when it is closed (not _current)")
    public void test_periodic_save_full_archive() throws IOException, URISyntaxException {
        // Given
        loadPlugin(endPoint, region, key, secret, bucket, rootPath);

        List<String> files = List.of("smallFile1.txt", "smallFile2.txt", "smallFile3.txt", "smallFile4.txt");

        OffsetDateTime now = OffsetDateTime.now();
        String dirName = now.format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));
        String nodeName = "testnode1";

        TestPeriodicActionProgressManager periodicActionProgressManager = new TestPeriodicActionProgressManager();

        for (String file : files) {
            copyFileToWorkspace(dirName, nodeName, file);
        }

        // When
        s3Glacier.runPeriodicAction(periodicActionProgressManager);

        // Then
        Awaitility.await()
                  .atMost(Durations.TEN_SECONDS)
                  .until(() -> periodicActionProgressManager.countAllReports() == 4);

        Assertions.assertEquals(4,
                                periodicActionProgressManager.getStoragePendingActionSucceed().size(),
                                "There should 4 success");
        Assertions.assertEquals(0, periodicActionProgressManager.getStoragePendingActionError().size());
        List<String> expectedReport = files.stream()
                                           .map(f -> createExpectedURL(nodeName, dirName, f).toString())
                                           .toList();
        Assertions.assertTrue(expectedReport.containsAll(periodicActionProgressManager.getStoragePendingActionSucceed()),
                              "The success URL are not the expected ones");
        Assertions.assertTrue(periodicActionProgressManager.getStoragePendingActionSucceed()
                                                           .containsAll(expectedReport),
                              "The success URL are not the expected ones");

        FileLocation archiveLoc = new FileLocation();
        archiveLoc.setUrl(createExpectedURL(nodeName, dirName + S3Glacier.ARCHIVE_EXTENSION).toString());
        FileReference archiveRef = new FileReference();
        archiveRef.setId(1L);
        archiveRef.setLocation(archiveLoc);

        // Get file as input stream from S3 server
        try {
            List<String> savedFiles = new ArrayList<>();
            InputStream inputStream = s3Glacier.retrieve(archiveRef);
            Assert.assertNotNull(inputStream);
            ZipInputStream zipStream = new ZipInputStream(inputStream);
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                savedFiles.add(entry.getName());
            }
            Assertions.assertTrue(files.containsAll(savedFiles), "The stored files are not the expected ones");
            Assertions.assertTrue(savedFiles.containsAll(files), "The stored files are not the expected ones");

        } catch (FileNotFoundException e) {
            Assertions.fail("Test Failure : file isn't stored in the S3 server");
        }
    }

    @Test
    @Purpose("Test that an archive is correctly sent when it is still open (_current) but expired")
    public void test_periodic_save_old_not_full_archive() throws IOException, URISyntaxException {
        // Given
        loadPlugin(endPoint, region, key, secret, bucket, rootPath);

        List<String> files = List.of("smallFile1.txt", "smallFile2.txt", "smallFile3.txt");

        OffsetDateTime now = OffsetDateTime.now();
        String oldCurrentDirName = now.minusHours(ARCHIVE_DURATION_IN_HOURS + 1)
                                      .format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT))
                                   + S3Glacier.CURRENT_ARCHIVE_SUFFIX;
        String nodeName = "testnode1";
        for (String file : files) {
            copyFileToWorkspace(oldCurrentDirName, nodeName, file);
        }

        TestPeriodicActionProgressManager periodicActionProgressManager = new TestPeriodicActionProgressManager();

        // When
        s3Glacier.runPeriodicAction(periodicActionProgressManager);

        // Then
        Awaitility.await()
                  .atMost(Durations.TEN_SECONDS)
                  .until(() -> periodicActionProgressManager.countAllReports() == 3);

        Assertions.assertEquals(3,
                                periodicActionProgressManager.getStoragePendingActionSucceed().size(),
                                "There should 4 success");
        Assertions.assertEquals(0, periodicActionProgressManager.getStoragePendingActionError().size());

        String dirName = oldCurrentDirName.substring(0,
                                                     oldCurrentDirName.length()
                                                     - S3Glacier.CURRENT_ARCHIVE_SUFFIX.length());
        List<String> expectedReport = files.stream()
                                           .map(f -> createExpectedURL(nodeName, dirName, f).toString())
                                           .toList();
        Assertions.assertTrue(expectedReport.containsAll(periodicActionProgressManager.getStoragePendingActionSucceed()),
                              "The success URL are not the expected ones");
        Assertions.assertTrue(periodicActionProgressManager.getStoragePendingActionSucceed()
                                                           .containsAll(expectedReport),
                              "The success URL are not the expected ones");

        FileLocation archiveLoc = new FileLocation();
        archiveLoc.setUrl(createExpectedURL(nodeName, dirName + S3Glacier.ARCHIVE_EXTENSION).toString());
        FileReference archiveRef = new FileReference();
        archiveRef.setId(1L);
        archiveRef.setLocation(archiveLoc);

        // Get file as input stream from S3 server
        try {
            List<String> savedFiles = new ArrayList<>();
            InputStream inputStream = s3Glacier.retrieve(archiveRef);
            Assert.assertNotNull(inputStream);
            ZipInputStream zipStream = new ZipInputStream(inputStream);
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                savedFiles.add(entry.getName());
            }
            Assertions.assertTrue(files.containsAll(savedFiles), "The stored files are not the expected ones");
            Assertions.assertTrue(savedFiles.containsAll(files), "The stored files are not the expected ones");

        } catch (FileNotFoundException e) {
            Assertions.fail("Test Failure : file isn't stored in the S3 server");
        }
    }

    @Test
    @Purpose("Test that an archive is not sent when it open and too young")
    public void test_periodic_save_too_young_archive() throws IOException, URISyntaxException {
        // Given
        loadPlugin(endPoint, region, key, secret, bucket, rootPath);

        OffsetDateTime now = OffsetDateTime.now();
        String oldCurrentDirName = now.minusHours(ARCHIVE_DURATION_IN_HOURS - 1)
                                      .format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT))
                                   + S3Glacier.CURRENT_ARCHIVE_SUFFIX;
        String nodeName = "testnode1";
        copyFileToWorkspace(oldCurrentDirName, nodeName, "smallFile1.txt");
        copyFileToWorkspace(oldCurrentDirName, nodeName, "smallFile2.txt");
        copyFileToWorkspace(oldCurrentDirName, nodeName, "smallFile3.txt");

        TestPeriodicActionProgressManager periodicActionProgressManager = new TestPeriodicActionProgressManager();

        // When
        s3Glacier.runPeriodicAction(periodicActionProgressManager);

        // Then
        Assertions.assertEquals(0,
                                periodicActionProgressManager.storagePendingActionSucceed.size(),
                                "There should be no success as the archive is too young");
        Assertions.assertEquals(0, periodicActionProgressManager.storagePendingActionError.size());

        File nodeDir = Paths.get(workspace.getRoot().getAbsolutePath(), S3Glacier.ZIP_DIR, nodeName).toFile();

        Assertions.assertEquals(1, nodeDir.list().length, "There should be one directory, the _current");
        File currentDir = nodeDir.listFiles()[0];
        Assertions.assertTrue(currentDir.getName().endsWith(S3Glacier.CURRENT_ARCHIVE_SUFFIX),
                              "The directory should be suffixed with the current suffix");

        Assertions.assertEquals(3, currentDir.listFiles().length, "The unsent files should still be in the directory");
    }

    private void copyFileToWorkspace(String targetDir, String nodeName, String fileName)
        throws IOException, URISyntaxException {
        Paths.get(workspace.getRoot().getAbsolutePath(), S3Glacier.ZIP_DIR, nodeName, targetDir);
        Path targetFile = Paths.get(workspace.getRoot().getAbsolutePath(), S3Glacier.ZIP_DIR, nodeName, targetDir);
        Files.createDirectories(targetFile);
        Files.copy(Path.of(S3GlacierPeriodicActionsIT.class.getResource("/files/" + fileName).toURI()),
                   targetFile.resolve(fileName));
    }

}
