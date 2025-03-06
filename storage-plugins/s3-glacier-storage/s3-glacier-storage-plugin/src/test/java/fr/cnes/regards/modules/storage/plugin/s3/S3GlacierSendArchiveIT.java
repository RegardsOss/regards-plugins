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

import fr.cnes.regards.framework.s3.domain.StorageCommand;
import fr.cnes.regards.framework.s3.utils.StorageConfigUtils;
import fr.cnes.regards.framework.test.report.annotation.Purpose;
import fr.cnes.regards.framework.utils.file.DownloadUtils;
import fr.cnes.regards.modules.fileaccess.plugin.domain.IPeriodicActionProgressManager;
import fr.cnes.regards.modules.storage.plugin.s3.utils.S3GlacierUtils;
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
import java.nio.file.attribute.PosixFilePermission;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Test class from send archive actions from {@link  S3Glacier#runPeriodicAction(IPeriodicActionProgressManager)}
 *
 * @author Thibaud Michaudel
 **/
@SpringBootTest
public class S3GlacierSendArchiveIT extends AbstractS3GlacierIT {

    @Test
    @Purpose("Test that an archive is correctly sent when it is closed (not _current)")
    public void test_periodic_save_full_archive() throws IOException, URISyntaxException {
        // Given
        loadPlugin(endPoint, region, key, secret, bucket, ROOT_PATH);

        List<String> files = List.of("smallFile1.txt", "smallFile2.txt", "smallFile3.txt", "smallFile4.txt");

        OffsetDateTime now = OffsetDateTime.now();
        String dirName = S3Glacier.BUILDING_DIRECTORY_PREFIX
                         + now.format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));
        String nodeName = "testnode1";

        TestPeriodicActionProgressManager periodicActionProgressManager = new TestPeriodicActionProgressManager();

        for (String file : files) {
            copyFileToWorkspace(ROOT_PATH, dirName, nodeName, file, S3Glacier.ZIP_DIR);
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
        Assertions.assertEquals(1,
                                periodicActionProgressManager.getStorageAllPendingActionSucceed().size(),
                                "there should have been a AllPendingActionSucceed sent to the progress manager");
        List<String> expectedReport = files.stream()
                                           .map(f -> createExpectedURL(nodeName,
                                                                       S3GlacierUtils.removePrefix(dirName),
                                                                       f).toString())
                                           .toList();
        Assertions.assertTrue(expectedReport.containsAll(periodicActionProgressManager.getStoragePendingActionSucceed()),
                              "The success URL are not the expected ones");
        Assertions.assertTrue(periodicActionProgressManager.getStoragePendingActionSucceed()
                                                           .containsAll(expectedReport),
                              "The success URL are not the expected ones");

        // Get file as input stream from S3 server
        try {
            List<String> savedFiles = new ArrayList<>();
            InputStream inputStream = downloadFromS3(createExpectedURL(nodeName,
                                                                       S3GlacierUtils.removePrefix(dirName)
                                                                       + S3Glacier.ARCHIVE_EXTENSION).toString());
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

        // Check that the directory has been deleted
        Assertions.assertFalse(Files.exists(Path.of(workspace.getRoot().toString(),
                                                    S3Glacier.ZIP_DIR,
                                                    nodeName,
                                                    dirName)), "The archive should have been deleted");

        // Check that the zip has been deleted
        Assertions.assertFalse(Files.exists(Path.of(workspace.getRoot().toString(),
                                                    S3Glacier.ZIP_DIR,
                                                    nodeName,
                                                    dirName + S3Glacier.ARCHIVE_EXTENSION)),
                               "The archive should have been deleted");
    }

    @Test
    @Purpose("Test that an archive is correctly sent when it is still open (_current) but expired")
    public void test_periodic_save_old_not_full_archive() throws IOException, URISyntaxException {
        // Given
        loadPlugin(endPoint, region, key, secret, bucket, ROOT_PATH);

        List<String> files = List.of("smallFile1.txt", "smallFile2.txt", "smallFile3.txt");

        OffsetDateTime now = OffsetDateTime.now();
        String oldCurrentDirName = S3Glacier.BUILDING_DIRECTORY_PREFIX
                                   + now.minusHours(ARCHIVE_DURATION_IN_HOURS + 1)
                                        .format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT))
                                   + S3Glacier.CURRENT_ARCHIVE_SUFFIX;
        String nodeName = "testnode1";
        for (String file : files) {
            copyFileToWorkspace(ROOT_PATH, oldCurrentDirName, nodeName, file, S3Glacier.ZIP_DIR);
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
                                "There should be 3 success");
        Assertions.assertEquals(0, periodicActionProgressManager.getStoragePendingActionError().size());
        Assertions.assertEquals(1,
                                periodicActionProgressManager.getStorageAllPendingActionSucceed().size(),
                                "there should have been a AllPendingActionSucceed sent to the progress manager");
        String dirName = S3GlacierUtils.removeSuffix(oldCurrentDirName);

        List<String> expectedReport = files.stream()
                                           .map(f -> createExpectedURL(nodeName,
                                                                       S3GlacierUtils.removePrefix(dirName),
                                                                       f).toString())
                                           .toList();
        Assertions.assertTrue(expectedReport.containsAll(periodicActionProgressManager.getStoragePendingActionSucceed()),
                              "The success URL are not the expected ones");
        Assertions.assertTrue(periodicActionProgressManager.getStoragePendingActionSucceed()
                                                           .containsAll(expectedReport),
                              "The success URL are not the expected ones");

        // Get file as input stream from S3 server
        try {
            List<String> savedFiles = new ArrayList<>();
            InputStream inputStream = downloadFromS3(createExpectedURL(nodeName,
                                                                       S3GlacierUtils.removePrefix(dirName)
                                                                       + S3Glacier.ARCHIVE_EXTENSION).toString());
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

        // Check that the directory has been deleted
        Assertions.assertFalse(Files.exists(Path.of(workspace.getRoot().toString(),
                                                    S3Glacier.ZIP_DIR,
                                                    nodeName,
                                                    dirName)), "The archive should have been deleted");

        // Check that the zip has been deleted
        Assertions.assertFalse(Files.exists(Path.of(workspace.getRoot().toString(),
                                                    S3Glacier.ZIP_DIR,
                                                    nodeName,
                                                    dirName + S3Glacier.ARCHIVE_EXTENSION)),
                               "The archive should have been deleted");
    }

    @Test
    @Purpose("Test that an archive is not sent when it open and too young")
    public void test_periodic_save_too_young_archive() throws IOException, URISyntaxException {
        // Given
        loadPlugin(endPoint, region, key, secret, bucket, ROOT_PATH);

        OffsetDateTime now = OffsetDateTime.now();
        String oldCurrentDirName = S3Glacier.BUILDING_DIRECTORY_PREFIX
                                   + now.minusHours(ARCHIVE_DURATION_IN_HOURS - 1)
                                        .format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT))
                                   + S3Glacier.CURRENT_ARCHIVE_SUFFIX;
        String nodeName = "testnode1";
        copyFileToWorkspace(ROOT_PATH, oldCurrentDirName, nodeName, "smallFile1.txt", S3Glacier.ZIP_DIR);
        copyFileToWorkspace(ROOT_PATH, oldCurrentDirName, nodeName, "smallFile2.txt", S3Glacier.ZIP_DIR);
        copyFileToWorkspace(ROOT_PATH, oldCurrentDirName, nodeName, "smallFile3.txt", S3Glacier.ZIP_DIR);

        TestPeriodicActionProgressManager periodicActionProgressManager = new TestPeriodicActionProgressManager();

        // When
        s3Glacier.runPeriodicAction(periodicActionProgressManager);

        // Then
        Assertions.assertEquals(0,
                                periodicActionProgressManager.storagePendingActionSucceed.size(),
                                "There should be no success as the archive is too young");
        Assertions.assertEquals(0, periodicActionProgressManager.storagePendingActionError.size());
        Assertions.assertEquals(1,
                                periodicActionProgressManager.getStorageAllPendingActionSucceed().size(),
                                "there should have been a AllPendingActionSucceed sent to the progress manager");

        File nodeDir = Paths.get(workspace.getRoot().getAbsolutePath(), S3Glacier.ZIP_DIR, ROOT_PATH, nodeName)
                            .toFile();

        Assertions.assertEquals(1, nodeDir.list().length, "There should be one directory, the _current");
        File currentDir = nodeDir.listFiles()[0];
        Assertions.assertTrue(currentDir.getName().endsWith(S3Glacier.CURRENT_ARCHIVE_SUFFIX),
                              "The directory should be suffixed with the current suffix");

        Assertions.assertEquals(3, currentDir.listFiles().length, "The unsent files should still be in the directory");
    }

    @Test
    @Purpose("Test that an archive is correctly sent when it is closed (not _current) and created from a file "
             + "deletion (the directory is then a symLink)")
    public void test_periodic_save_archive_following_deletion() throws IOException, URISyntaxException {
        // Given
        loadPlugin(endPoint, region, key, secret, bucket, ROOT_PATH);

        List<String> files = List.of("smallFile1.txt", "smallFile2.txt", "smallFile3.txt", "smallFile4.txt");

        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));
        String dirName = S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName;
        String nodeName = "testnode1";

        createTestArchive(files,
                          ROOT_PATH + File.separator + nodeName,
                          archiveName,
                          workspace.getRoot().toPath().resolve(S3Glacier.TMP_DIR));

        TestPeriodicActionProgressManager periodicActionProgressManager = new TestPeriodicActionProgressManager();

        for (String file : files) {
            copyFileToWorkspace(ROOT_PATH, dirName, nodeName, file, S3Glacier.TMP_DIR);
        }
        Path dirInWorkspacePath = Path.of(workspace.getRoot().toString(),
                                          S3Glacier.ZIP_DIR,
                                          ROOT_PATH,
                                          nodeName,
                                          dirName);
        Files.createDirectories(dirInWorkspacePath.getParent());
        Files.createSymbolicLink(dirInWorkspacePath,
                                 Path.of(workspace.getRoot().toString(),
                                         S3Glacier.TMP_DIR,
                                         ROOT_PATH,
                                         nodeName,
                                         dirName));

        // Check that the symlink exists
        Assertions.assertTrue(Files.exists(Path.of(workspace.getRoot().toString(),
                                                   S3Glacier.ZIP_DIR,
                                                   ROOT_PATH,
                                                   nodeName,
                                                   dirName)), "The symlink should exist");

        // Check that the directory exists
        Assertions.assertTrue(Files.exists(Path.of(workspace.getRoot().toString(),
                                                   S3Glacier.TMP_DIR,
                                                   ROOT_PATH,
                                                   nodeName,
                                                   dirName)), "The directory should exist");

        // Check that the archive exists
        Assertions.assertTrue(Files.exists(Path.of(workspace.getRoot().toString(),
                                                   S3Glacier.TMP_DIR,
                                                   ROOT_PATH,
                                                   nodeName,
                                                   archiveName + S3Glacier.ARCHIVE_EXTENSION)),
                              "The archive should exist");

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
        Assertions.assertEquals(1,
                                periodicActionProgressManager.getStorageAllPendingActionSucceed().size(),
                                "there should have been a AllPendingActionSucceed sent to the progress manager");
        List<String> expectedReport = files.stream()
                                           .map(f -> createExpectedURL(nodeName,
                                                                       S3GlacierUtils.removePrefix(dirName),
                                                                       f).toString())
                                           .toList();
        Assertions.assertTrue(expectedReport.containsAll(periodicActionProgressManager.getStoragePendingActionSucceed()),
                              "The success URL are not the expected ones");
        Assertions.assertTrue(periodicActionProgressManager.getStoragePendingActionSucceed()
                                                           .containsAll(expectedReport),
                              "The success URL are not the expected ones");

        // Get file as input stream from S3 server
        try {
            List<String> savedFiles = new ArrayList<>();
            InputStream inputStream = downloadFromS3(createExpectedURL(nodeName,
                                                                       S3GlacierUtils.removePrefix(dirName)
                                                                       + S3Glacier.ARCHIVE_EXTENSION).toString());
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

        // Check that the symlink has been deleted
        Assertions.assertFalse(Files.exists(Path.of(workspace.getRoot().toString(),
                                                    S3Glacier.ZIP_DIR,
                                                    ROOT_PATH,
                                                    nodeName,
                                                    dirName)), "The symlink should have been deleted");

        // Check that the directory has not been deleted as it's in the cache workspace
        Assertions.assertTrue(Files.exists(Path.of(workspace.getRoot().toString(),
                                                   S3Glacier.TMP_DIR,
                                                   ROOT_PATH,
                                                   nodeName,
                                                   dirName)), "The directory should not have been deleted");

        // Check that the zip has been deleted
        Assertions.assertFalse(Files.exists(Path.of(workspace.getRoot().toString(),
                                                    S3Glacier.TMP_DIR,
                                                    ROOT_PATH,
                                                    nodeName,
                                                    archiveName + S3Glacier.ARCHIVE_EXTENSION)),
                               "The archive should have been deleted");
    }

    @Test
    @Purpose("Test that the files are not deleted when the archive creation fails")
    public void test_zip_error() throws IOException, URISyntaxException {
        // Given
        loadPlugin(endPoint, region, key, secret, bucket, ROOT_PATH);

        List<String> files = List.of("smallFile1.txt", "smallFile2.txt", "smallFile3.txt", "smallFile4.txt");

        OffsetDateTime now = OffsetDateTime.now();
        String dirDate = now.format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));
        String dirName = S3Glacier.BUILDING_DIRECTORY_PREFIX + dirDate;
        String nodeName = "testnode1";

        TestPeriodicActionProgressManager periodicActionProgressManager = new TestPeriodicActionProgressManager();

        for (String file : files) {
            copyFileToWorkspace(ROOT_PATH, dirName, nodeName, file, S3Glacier.ZIP_DIR);
        }

        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.OTHERS_READ);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        perms.add(PosixFilePermission.GROUP_EXECUTE);
        perms.add(PosixFilePermission.OTHERS_EXECUTE);
        Path nodePath = Path.of(workspace.getRoot().toString(), S3Glacier.ZIP_DIR, ROOT_PATH, nodeName);
        try {
            // Simulate zip error
            Files.setPosixFilePermissions(nodePath, perms);

            // When
            s3Glacier.runPeriodicAction(periodicActionProgressManager);

            // Then
            Awaitility.await()
                      .atMost(Durations.TEN_SECONDS)
                      .until(() -> periodicActionProgressManager.countAllReports() == 4);

            Assertions.assertEquals(4,
                                    periodicActionProgressManager.getStoragePendingActionError().size(),
                                    "There should 4 errors");
            Assertions.assertEquals(0,
                                    periodicActionProgressManager.getStorageAllPendingActionSucceed().size(),
                                    "there should have been no AllPendingActionSucceed sent to the progress manager");
            Assertions.assertTrue(Files.exists(nodePath.resolve(dirName)));
            try (Stream<Path> filesInDir = Files.list(nodePath.resolve(dirName))) {
                long count = filesInDir.count();
                Assertions.assertEquals(files.size(), count);
            }

            Assertions.assertFalse(Files.exists(nodePath.resolve(dirDate + S3Glacier.ARCHIVE_EXTENSION)));
        } finally {
            perms.add(PosixFilePermission.OWNER_WRITE);
            perms.add(PosixFilePermission.GROUP_WRITE);
            perms.add(PosixFilePermission.OTHERS_WRITE);
            Files.setPosixFilePermissions(nodePath, perms);
        }
    }

    @Test
    @Purpose("Test that the archive and the directory are correctly deleted when the archive is empty")
    public void test_last_file_deletion() throws URISyntaxException, IOException, NoSuchAlgorithmException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);

        TestPeriodicActionProgressManager progressManager = new TestPeriodicActionProgressManager();

        String fileName = "smallFile1.txt";
        String nodeName = "deep/dir/testNode";

        // Create the archive that contain the file to retrieve
        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

        StorageCommand.Write writeCmd = createTestArchiveAndBuildWriteCmd(List.of(fileName), nodeName, archiveName);
        writeFileOnStorage(writeCmd);

        // Create the empty dir to simulate a building dir with no file left
        Path emptyDirPath = Path.of(workspace.getRoot().toString(),
                                    S3Glacier.TMP_DIR,
                                    ROOT_PATH,
                                    nodeName,
                                    S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName);
        Files.createDirectories(emptyDirPath);

        // Create the symbolic link to the empty dir
        Path symbolicLinkSourcePath = Path.of(workspace.getRoot().toString(),
                                              S3Glacier.ZIP_DIR,
                                              ROOT_PATH,
                                              nodeName,
                                              S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName);
        Files.createDirectories(symbolicLinkSourcePath.getParent());
        Files.createSymbolicLink(symbolicLinkSourcePath, emptyDirPath);

        Assertions.assertTrue(Files.exists(emptyDirPath), "The building directory should exist");
        String entryKey = StorageConfigUtils.entryKey(s3Glacier.getStorageConfiguration(),
                                                      Path.of(nodeName, archiveName + S3Glacier.ARCHIVE_EXTENSION)
                                                          .toString());
        Assertions.assertTrue(DownloadUtils.existsS3(entryKey, s3Glacier.getStorageConfiguration()),
                              "The archive should exist on the server");

        // When
        s3Glacier.runPeriodicAction(progressManager);

        Awaitility.await()
                  .atMost(Durations.TEN_SECONDS)
                  .until(() -> progressManager.getStorageAllPendingActionSucceed().size() == 1);

        Assertions.assertFalse(Files.exists(emptyDirPath),
                               "The building directory should have been deleted as it is now empty");
        Assertions.assertFalse(DownloadUtils.existsS3(entryKey, s3Glacier.getStorageConfiguration()),
                               "The archive should have been deleted from the server");

    }
}
