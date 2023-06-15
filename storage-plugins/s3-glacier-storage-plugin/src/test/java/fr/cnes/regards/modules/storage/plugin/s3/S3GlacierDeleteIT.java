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

import fr.cnes.regards.framework.s3.domain.StorageCommand;
import fr.cnes.regards.framework.test.report.annotation.Purpose;
import fr.cnes.regards.modules.storage.domain.database.FileReference;
import fr.cnes.regards.modules.storage.domain.database.request.FileDeletionRequest;
import fr.cnes.regards.modules.storage.domain.plugin.FileDeletionWorkingSubset;
import fr.cnes.regards.modules.storage.domain.plugin.IDeletionProgressManager;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Test class for {@link  S3Glacier#delete(FileDeletionWorkingSubset, IDeletionProgressManager)}
 *
 * @author Thibaud Michaudel
 **/
@SpringBootTest
public class S3GlacierDeleteIT extends AbstractS3GlacierIT {

    private static final Logger LOGGER = getLogger(AbstractS3GlacierIT.class);

    @Test
    @Purpose("Test that a small file present in the archive building workspace is correctly deleted")
    public void test_delete_small_file_local_build() throws IOException, URISyntaxException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, rootPath);

        TestDeletionProgressManager progressManager = new TestDeletionProgressManager();
        String fileName = "smallFile1.txt";
        String fileName2 = "smallFile2.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";

        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));
        copyFileToWorkspace(archiveName, nodeName, fileName, S3Glacier.ZIP_DIR);
        copyFileToWorkspace(archiveName, nodeName, fileName2, S3Glacier.ZIP_DIR);

        // When
        FileReference reference = createFileReference(fileName, fileChecksum, fileSize, nodeName, archiveName, true);

        FileDeletionRequest request = new FileDeletionRequest(reference,
                                                              "groupIdTest",
                                                              "sessionOwnerTest",
                                                              "sessionTest");
        FileDeletionWorkingSubset workingSubset = new FileDeletionWorkingSubset(List.of(request));
        s3Glacier.delete(workingSubset, progressManager);

        //Then
        checkDeletionOfOneFileSuccess(progressManager, fileName2, fileChecksum, nodeName, archiveName);
    }

    @Test
    @Purpose("Test that a small file present in the archive building workspace and alone in its directory is "
             + "correctly deleted and that the directory is also deleted")
    public void test_delete_only_small_file_local_build() throws IOException, URISyntaxException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, rootPath);

        // When
        TestDeletionProgressManager progressManager = new TestDeletionProgressManager();
        String fileName = "smallFile1.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";

        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));
        copyFileToWorkspace(archiveName, nodeName, fileName, S3Glacier.ZIP_DIR);

        FileReference reference = createFileReference(fileName, fileChecksum, fileSize, nodeName, archiveName, true);

        FileDeletionRequest request = new FileDeletionRequest(reference,
                                                              "groupIdTest",
                                                              "sessionOwnerTest",
                                                              "sessionTest");
        FileDeletionWorkingSubset workingSubset = new FileDeletionWorkingSubset(List.of(request));
        s3Glacier.delete(workingSubset, progressManager);

        //Then
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> progressManager.countAllReports() == 1);
        Assertions.assertEquals(1, progressManager.getDeletionSucceed().size(), "There should be one success");
        Assertions.assertEquals(fileChecksum,
                                progressManager.getDeletionSucceed()
                                               .get(0)
                                               .getFileReference()
                                               .getMetaInfo()
                                               .getChecksum(),
                                "The successful request is not the expected one");
        Assertions.assertEquals(0, progressManager.getDeletionFailed().size());

        Path buildingDirPath = Path.of(workspace.getRoot().getAbsolutePath(), S3Glacier.ZIP_DIR, nodeName, archiveName);
        Assertions.assertFalse(Files.exists(buildingDirPath),
                               "The building directory should have been deleted as it is now empty");
    }

    @Test
    @Purpose("Test that a small file present in the archive building workspace current directory is correctly deleted")
    public void test_delete_small_file_local_build_current() throws IOException, URISyntaxException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, rootPath);

        // When
        TestDeletionProgressManager progressManager = new TestDeletionProgressManager();
        String fileName = "smallFile1.txt";
        String fileName2 = "smallFile2.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";

        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));
        copyFileToWorkspace(archiveName + S3Glacier.CURRENT_ARCHIVE_SUFFIX, nodeName, fileName, S3Glacier.ZIP_DIR);
        copyFileToWorkspace(archiveName + S3Glacier.CURRENT_ARCHIVE_SUFFIX, nodeName, fileName2, S3Glacier.ZIP_DIR);

        FileReference reference = createFileReference(fileName, fileChecksum, fileSize, nodeName, archiveName, true);

        FileDeletionRequest request = new FileDeletionRequest(reference,
                                                              "groupIdTest",
                                                              "sessionOwnerTest",
                                                              "sessionTest");
        FileDeletionWorkingSubset workingSubset = new FileDeletionWorkingSubset(List.of(request));
        s3Glacier.delete(workingSubset, progressManager);

        //Then
        checkDeletionOfOneFileSuccess(progressManager,
                                      fileName2,
                                      fileChecksum,
                                      nodeName,
                                      archiveName + S3Glacier.CURRENT_ARCHIVE_SUFFIX);
    }

    @Test
    @Purpose("Test that a big file is correctly deleted")
    public void test_delete_big_file() throws URISyntaxException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, rootPath);

        String fileName = "bigFile1.txt";
        String fileChecksum = "aaf14d43dbfb6c33244ec1a25531cb00";
        long fileSize = 22949;
        String nodeName = "deep/dir/testNode";
        TestDeletionProgressManager progressManager = new TestDeletionProgressManager();

        String entryKey = s3Glacier.storageConfiguration.entryKey(Path.of(nodeName, fileName).toString());
        Path filePath = Path.of(S3GlacierRestoreIT.class.getResource("/files/" + fileName).toURI());

        FileReference reference = createFileReference(createFileStorageRequest(nodeName,
                                                                               fileName,
                                                                               fileSize,
                                                                               fileChecksum), rootPath);

        FileDeletionRequest request = new FileDeletionRequest(reference,
                                                              "groupIdTest",
                                                              "sessionOwnerTest",
                                                              "sessionTest");
        FileDeletionWorkingSubset workingSubset = new FileDeletionWorkingSubset(List.of(request));

        // When
        s3Glacier.delete(workingSubset, progressManager);

        // Then
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> progressManager.countAllReports() == 1);
        Assertions.assertEquals(1, progressManager.getDeletionSucceed().size(), "There should be one success");
        Assertions.assertEquals(fileChecksum,
                                progressManager.getDeletionSucceed()
                                               .get(0)
                                               .getFileReference()
                                               .getMetaInfo()
                                               .getChecksum(),
                                "The successful request is not the expected one");
        Assertions.assertEquals(0, progressManager.getDeletionFailed().size());

    }

    @Test
    @Purpose("Test that a small file archived is correctly restored, then extracted and the file is then deleted")
    public void test_restore_then_delete_small_file() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, rootPath);
        TestDeletionProgressManager progressManager = new TestDeletionProgressManager();

        String fileName = "smallFile1.txt";
        String fileName2 = "smallFile2.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";

        // Create the archive that contain the file to retrieve
        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

        StorageCommand.Write writeCmd = createTestArchiveAndBuildWriteCmd(List.of(fileName, fileName2),
                                                                          nodeName,
                                                                          archiveName);
        writeFileOnStorage(writeCmd);

        // When

        FileReference reference = createFileReference(fileName, fileChecksum, fileSize, nodeName, archiveName, false);

        FileDeletionRequest request = new FileDeletionRequest(reference,
                                                              "groupIdTest",
                                                              "sessionOwnerTest",
                                                              "sessionTest");
        FileDeletionWorkingSubset workingSubset = new FileDeletionWorkingSubset(List.of(request));
        s3Glacier.delete(workingSubset, progressManager);

        //Then
        checkDeletionOfOneFileSuccess(progressManager, fileName2, fileChecksum, nodeName, archiveName);
    }

    @Test
    @Purpose("Test that a small file archived is correctly restored, then extracted and the file is then deleted")
    public void test_copy_then_delete_small_file() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, rootPath);
        TestDeletionProgressManager progressManager = new TestDeletionProgressManager();

        String fileName = "smallFile1.txt";
        String fileName2 = "smallFile2.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";

        // Create the archive that contain the file to retrieve and copy it to the cache
        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

        Path archiveTestPath = createTestArchive(List.of(fileName, fileName2),
                                                 nodeName,
                                                 archiveName,
                                                 workspace.getRoot().toPath().resolve("test"));

        Path cacheNodePath = Path.of(workspace.getRoot().toString(), S3Glacier.TMP_DIR, nodeName);
        Files.createDirectories(cacheNodePath);
        Files.copy(archiveTestPath, cacheNodePath.resolve(archiveTestPath.getFileName().toString()));

        // When
        FileReference reference = createFileReference(fileName, fileChecksum, fileSize, nodeName, archiveName, false);

        FileDeletionRequest request = new FileDeletionRequest(reference,
                                                              "groupIdTest",
                                                              "sessionOwnerTest",
                                                              "sessionTest");
        FileDeletionWorkingSubset workingSubset = new FileDeletionWorkingSubset(List.of(request));
        s3Glacier.delete(workingSubset, progressManager);

        //Then
        checkDeletionOfOneFileSuccess(progressManager, fileName2, fileChecksum, nodeName, archiveName);
    }

    @Test
    @Purpose("Test that a small file present in the archive building workspace following a previous deletion is "
             + "correctly deleted")
    public void test_delete_small_file_local_build_with_pending_false() throws IOException, URISyntaxException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, rootPath);

        TestDeletionProgressManager progressManager = new TestDeletionProgressManager();
        String fileName = "smallFile1.txt";
        String fileName2 = "smallFile2.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";

        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));
        copyFileToWorkspace(archiveName, nodeName, fileName, S3Glacier.ZIP_DIR);
        copyFileToWorkspace(archiveName, nodeName, fileName2, S3Glacier.ZIP_DIR);

        // When
        FileReference reference = createFileReference(fileName, fileChecksum, fileSize, nodeName, archiveName, false);

        FileDeletionRequest request = new FileDeletionRequest(reference,
                                                              "groupIdTest",
                                                              "sessionOwnerTest",
                                                              "sessionTest");
        FileDeletionWorkingSubset workingSubset = new FileDeletionWorkingSubset(List.of(request));
        s3Glacier.delete(workingSubset, progressManager);

        //Then
        checkDeletionOfOneFileSuccess(progressManager, fileName2, fileChecksum, nodeName, archiveName);
    }
}
