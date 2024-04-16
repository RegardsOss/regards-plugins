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
import fr.cnes.regards.modules.fileaccess.dto.FileReferenceWithoutOwnersDto;
import fr.cnes.regards.modules.fileaccess.dto.FileRequestStatus;
import fr.cnes.regards.modules.fileaccess.plugin.domain.FileDeletionWorkingSubset;
import fr.cnes.regards.modules.fileaccess.plugin.domain.IDeletionProgressManager;
import fr.cnes.regards.modules.fileaccess.plugin.dto.FileDeletionRequestDto;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Test class for {@link  S3Glacier#delete(FileDeletionWorkingSubset, IDeletionProgressManager)}
 *
 * @author Thibaud Michaudel
 **/
@SpringBootTest
public class S3GlacierDeleteIT extends AbstractS3GlacierIT {

    @Test
    @Purpose("Test that a small file present in the archive building workspace is correctly deleted")
    public void test_delete_small_file_local_build() throws IOException, URISyntaxException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);

        TestDeletionProgressManager progressManager = new TestDeletionProgressManager();
        String fileName = "smallFile1.txt";
        String fileName2 = "smallFile2.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";

        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));
        copyFileToWorkspace(ROOT_PATH,
                            S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName,
                            nodeName,
                            fileName,
                            S3Glacier.ZIP_DIR);
        copyFileToWorkspace(ROOT_PATH,
                            S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName,
                            nodeName,
                            fileName2,
                            S3Glacier.ZIP_DIR);

        // When
        FileReferenceWithoutOwnersDto reference = createFileReference(fileName,
                                                                      fileChecksum,
                                                                      fileSize,
                                                                      nodeName,
                                                                      archiveName,
                                                                      true);

        FileDeletionRequestDto request = getFileDeletionRequestDto(reference);
        FileDeletionWorkingSubset workingSubset = new FileDeletionWorkingSubset(List.of(request));
        s3Glacier.delete(workingSubset, progressManager);

        //Then
        checkDeletionOfOneFileSuccess(progressManager, fileName2, fileChecksum, nodeName, archiveName);
    }

    @Test
    @Purpose("Test that a small file present in the archive building workspace current directory is correctly deleted")
    public void test_delete_small_file_local_build_current() throws IOException, URISyntaxException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);

        // When
        TestDeletionProgressManager progressManager = new TestDeletionProgressManager();
        String fileName = "smallFile1.txt";
        String fileName2 = "smallFile2.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";

        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));
        copyFileToWorkspace(ROOT_PATH,
                            S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName + S3Glacier.CURRENT_ARCHIVE_SUFFIX,
                            nodeName,
                            fileName,
                            S3Glacier.ZIP_DIR);
        copyFileToWorkspace(ROOT_PATH,
                            S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName + S3Glacier.CURRENT_ARCHIVE_SUFFIX,
                            nodeName,
                            fileName2,
                            S3Glacier.ZIP_DIR);

        FileReferenceWithoutOwnersDto reference = createFileReference(fileName,
                                                                      fileChecksum,
                                                                      fileSize,
                                                                      nodeName,
                                                                      archiveName,
                                                                      true);

        FileDeletionRequestDto request = getFileDeletionRequestDto(reference);
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
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);

        String fileName = "bigFile1.txt";
        String fileChecksum = "aaf14d43dbfb6c33244ec1a25531cb00";
        long fileSize = 22949;
        String nodeName = "deep/dir/testNode";
        TestDeletionProgressManager progressManager = new TestDeletionProgressManager();

        String entryKey = s3Glacier.storageConfiguration.entryKey(Path.of(nodeName, fileName).toString());
        Path filePath = Path.of(S3GlacierRestoreIT.class.getResource("/files/" + fileName).toURI());

        FileReferenceWithoutOwnersDto reference = createFileReference(createFileStorageRequestAggregation(nodeName,
                                                                                                          fileName,
                                                                                                          fileSize,
                                                                                                          fileChecksum),
                                                                      ROOT_PATH);

        FileDeletionRequestDto request = getFileDeletionRequestDto(reference);
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
    @Purpose("Test that a small file archive is correctly restored, then extracted and the file is then deleted")
    public void test_restore_then_delete_small_file() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);
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
        FileReferenceWithoutOwnersDto reference = createFileReference(fileName,
                                                                      fileChecksum,
                                                                      fileSize,
                                                                      nodeName,
                                                                      archiveName,
                                                                      false);

        FileDeletionRequestDto request = getFileDeletionRequestDto(reference);
        FileDeletionWorkingSubset workingSubset = new FileDeletionWorkingSubset(List.of(request));
        s3Glacier.delete(workingSubset, progressManager);

        //Then
        checkDeletionOfOneFileSuccessWithPending(progressManager, fileName2, fileChecksum, nodeName, archiveName);
    }

    @Test
    @Purpose("Test that a small file archive present in an already restored archive is downloaded then extracted and "
             + "the file is then deleted")
    public void test_delete_small_file_already_restored()
        throws IOException, URISyntaxException, NoSuchAlgorithmException {
        // Given
        loadPlugin(endPoint,
                   region,
                   key,
                   secret,
                   BUCKET_OUTPUT,
                   ROOT_PATH,
                   MockedS3ClientType.MockedS3ClientAlreadyAvailable,
                   false,
                   false);
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

        FileReferenceWithoutOwnersDto reference = createFileReference(fileName,
                                                                      fileChecksum,
                                                                      fileSize,
                                                                      nodeName,
                                                                      archiveName,
                                                                      false);

        FileDeletionRequestDto request = getFileDeletionRequestDto(reference);
        FileDeletionWorkingSubset workingSubset = new FileDeletionWorkingSubset(List.of(request));
        s3Glacier.delete(workingSubset, progressManager);

        //Then
        checkDeletionOfOneFileSuccessWithPending(progressManager, fileName2, fileChecksum, nodeName, archiveName);
    }

    @Test
    @Purpose("Test that there is no error when a file to delete is not on the server")
    public void test_delete_small_file_no_such_key() {
        // Given
        loadPlugin(endPoint,
                   region,
                   key,
                   secret,
                   BUCKET_OUTPUT,
                   ROOT_PATH,
                   MockedS3ClientType.MockedS3ClientWithNoFileAvailable,
                   false,
                   false);
        TestDeletionProgressManager progressManager = new TestDeletionProgressManager();

        String fileName = "smallFile1.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";

        // Create the archive that contain the file to retrieve
        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

        // When
        FileReferenceWithoutOwnersDto reference = createFileReference(fileName,
                                                                      fileChecksum,
                                                                      fileSize,
                                                                      nodeName,
                                                                      archiveName,
                                                                      false);

        FileDeletionRequestDto request = getFileDeletionRequestDto(reference);
        FileDeletionWorkingSubset workingSubset = new FileDeletionWorkingSubset(List.of(request));
        s3Glacier.delete(workingSubset, progressManager);

        //Then
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> progressManager.countAllReports() == 1);
        Assertions.assertEquals(1, progressManager.getDeletionSucceed().size(), "There should be one success");
    }

    @Test
    @Purpose("Test that a small file archive is correctly restored, then extracted and the file is then deleted")
    public void test_copy_then_delete_small_file() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);
        TestDeletionProgressManager progressManager = new TestDeletionProgressManager();

        String fileName = "smallFile1.txt";
        String fileName2 = "smallFile2.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";

        // Create the archive that contain the file to retrieve and copy it to the cache
        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

        Path archiveTestPath = createTestArchive(List.of(fileName, fileName2),
                                                 ROOT_PATH + File.separator + nodeName,
                                                 archiveName,
                                                 workspace.getRoot().toPath().resolve("test"));

        Path cacheNodePath = Path.of(workspace.getRoot().toString(), S3Glacier.TMP_DIR, ROOT_PATH, nodeName);
        Files.createDirectories(cacheNodePath);
        Files.copy(archiveTestPath, cacheNodePath.resolve(archiveTestPath.getFileName().toString()));

        // When
        FileReferenceWithoutOwnersDto reference = createFileReference(fileName,
                                                                      fileChecksum,
                                                                      fileSize,
                                                                      nodeName,
                                                                      archiveName,
                                                                      false);

        FileDeletionRequestDto request = getFileDeletionRequestDto(reference);
        FileDeletionWorkingSubset workingSubset = new FileDeletionWorkingSubset(List.of(request));
        s3Glacier.delete(workingSubset, progressManager);

        //Then
        checkDeletionOfOneFileSuccessWithPending(progressManager, fileName2, fileChecksum, nodeName, archiveName);
    }

    @Test
    @Purpose("Test that a small file present in the archive building workspace following a previous deletion is "
             + "correctly deleted")
    public void test_delete_small_file_local_build_with_pending_false() throws IOException, URISyntaxException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);

        TestDeletionProgressManager progressManager = new TestDeletionProgressManager();
        String fileName = "smallFile1.txt";
        String fileName2 = "smallFile2.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";

        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));
        copyFileToWorkspace(ROOT_PATH,
                            S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName,
                            nodeName,
                            fileName,
                            S3Glacier.TMP_DIR);
        copyFileToWorkspace(ROOT_PATH,
                            S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName,
                            nodeName,
                            fileName2,
                            S3Glacier.TMP_DIR);
        Path dirInWorkspacePath = Path.of(workspace.getRoot().toString(),
                                          S3Glacier.ZIP_DIR,
                                          ROOT_PATH,
                                          nodeName,
                                          S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName);
        Files.createDirectories(dirInWorkspacePath.getParent());
        Files.createSymbolicLink(dirInWorkspacePath,
                                 Path.of(workspace.getRoot().toString(),
                                         S3Glacier.TMP_DIR,
                                         ROOT_PATH,
                                         nodeName,
                                         S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName));

        // When
        FileReferenceWithoutOwnersDto reference = createFileReference(fileName,
                                                                      fileChecksum,
                                                                      fileSize,
                                                                      nodeName,
                                                                      archiveName,
                                                                      false);

        FileDeletionRequestDto request = getFileDeletionRequestDto(reference);
        FileDeletionWorkingSubset workingSubset = new FileDeletionWorkingSubset(List.of(request));
        s3Glacier.delete(workingSubset, progressManager);

        //Then
        checkDeletionOfOneFileSuccessWithPending(progressManager, fileName2, fileChecksum, nodeName, archiveName);
    }

    @Test
    @Purpose("Test that a file is deleted when it is located in the archive cache workspace (through à symlink) and "
             + "that the old archive is not extracted again (so any file present in the archive but not in the "
             + "directory is not extracted a second time")
    public void test_simulated_deletion_sequence() throws URISyntaxException, IOException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);
        TestDeletionProgressManager progressManager = new TestDeletionProgressManager();

        String fileName = "smallFile1.txt";
        String fileName2 = "smallFile2.txt";
        String fileName3 = "smallFile3.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";

        // Create the archive that contain the file to retrieve and copy it to the cache
        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));
        createTestArchive(List.of(fileName, fileName2, fileName3),
                          ROOT_PATH + File.separator + nodeName,
                          archiveName,
                          workspace.getRoot().toPath().resolve(S3Glacier.TMP_DIR));

        Path cacheDirPath = Path.of(workspace.getRoot().toString(),
                                    S3Glacier.TMP_DIR,
                                    ROOT_PATH,
                                    nodeName,
                                    S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName);
        Files.createDirectories(cacheDirPath);

        Path zipDirPath = Path.of(workspace.getRoot().toString(),
                                  S3Glacier.ZIP_DIR,
                                  ROOT_PATH,
                                  nodeName,
                                  S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName);
        Files.createDirectories(zipDirPath.getParent());
        Files.createSymbolicLink(zipDirPath, cacheDirPath);

        copyFileToWorkspace(ROOT_PATH,
                            S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName,
                            nodeName,
                            fileName,
                            S3Glacier.TMP_DIR);
        copyFileToWorkspace(ROOT_PATH,
                            S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName,
                            nodeName,
                            fileName2,
                            S3Glacier.TMP_DIR);

        // When
        FileReferenceWithoutOwnersDto reference = createFileReference(fileName,
                                                                      fileChecksum,
                                                                      fileSize,
                                                                      nodeName,
                                                                      archiveName,
                                                                      false);

        FileDeletionRequestDto request = getFileDeletionRequestDto(reference);
        FileDeletionWorkingSubset workingSubset = new FileDeletionWorkingSubset(List.of(request));
        s3Glacier.delete(workingSubset, progressManager);

        //Then
        checkDeletionOfOneFileSuccessWithPending(progressManager, fileName2, fileChecksum, nodeName, archiveName);
    }

    private FileDeletionRequestDto getFileDeletionRequestDto(FileReferenceWithoutOwnersDto reference) {
        return new FileDeletionRequestDto(1L,
                                          "groupIdTest",
                                          FileRequestStatus.TO_DO,
                                          "storage",
                                          reference,
                                          false,
                                          null,
                                          null,
                                          null,
                                          "sessionOwnerTest",
                                          "sessionTest");
    }
}
