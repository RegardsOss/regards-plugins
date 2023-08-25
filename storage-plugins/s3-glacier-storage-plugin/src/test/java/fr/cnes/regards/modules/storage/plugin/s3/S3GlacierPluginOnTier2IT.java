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
import fr.cnes.regards.framework.s3.domain.StorageCommandID;
import fr.cnes.regards.framework.s3.domain.StorageEntry;
import fr.cnes.regards.framework.test.report.annotation.Purpose;
import fr.cnes.regards.modules.storage.domain.database.FileReference;
import fr.cnes.regards.modules.storage.domain.database.request.FileCacheRequest;
import fr.cnes.regards.modules.storage.domain.database.request.FileDeletionRequest;
import fr.cnes.regards.modules.storage.domain.plugin.FileDeletionWorkingSubset;
import fr.cnes.regards.modules.storage.domain.plugin.FileRestorationWorkingSubset;
import io.vavr.Tuple;
import io.vavr.control.Option;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Test class to validate that the S3Glacier plugin can handle being used on a tier 2 S3 storage.
 * (ie, it means that the plugin methods does not fail when the restoration process on a file with standard (Tier 2)
 * storage class.
 *
 * @author Thibaud Michaudel
 **/
@SpringBootTest
public class S3GlacierPluginOnTier2IT extends AbstractS3GlacierIT {

    @Test
    @Purpose("Test that a small file on tier 2 storage is retrieved even if the restore method is called")
    public void test_small_restore_glacier() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH, false, false);

        String fileName = "smallFile1.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";
        TestRestoreProgressManager progressManager = new TestRestoreProgressManager();

        // Create the archive that contain the file to retrieve
        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

        StorageCommand.Write writeCmd = createTestArchiveAndBuildWriteCmd(List.of(fileName), nodeName, archiveName);

        Path restorationWorkspace = workspace.getRoot().toPath().resolve("target");

        writeFileOnStorage(writeCmd);

        // When
        FileCacheRequest request = createFileCacheRequest(restorationWorkspace,
                                                          fileName,
                                                          fileChecksum,
                                                          fileSize,
                                                          nodeName,
                                                          archiveName,
                                                          false);

        FileRestorationWorkingSubset workingSubset = new FileRestorationWorkingSubset(List.of(request));

        s3Glacier.retrieve(workingSubset, progressManager);

        // Then
        checkRestoreSuccess(fileChecksum, progressManager, restorationWorkspace);
    }

    @Test
    @Purpose("Test that the restoration fail if the given key doesn't exist")
    public void test_small_restore_no_such_key_glacier()
        throws IOException, URISyntaxException, NoSuchAlgorithmException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH, false, false);

        String fileName = "smallFile1.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";
        TestRestoreProgressManager progressManager = new TestRestoreProgressManager();

        // Create the archive that contain the file to retrieve
        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

        Path restorationWorkspace = workspace.getRoot().toPath().resolve("target");

        // When
        FileCacheRequest request = createFileCacheRequest(restorationWorkspace,
                                                          fileName,
                                                          fileChecksum,
                                                          fileSize,
                                                          nodeName,
                                                          archiveName,
                                                          false);

        FileRestorationWorkingSubset workingSubset = new FileRestorationWorkingSubset(List.of(request));

        s3Glacier.retrieve(workingSubset, progressManager);

        // Then
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> progressManager.countAllReports() == 1);
        Assertions.assertEquals(1,
                                progressManager.getRestoreFailed().size(),
                                "There should be a restoration failed because the key does not exist");
    }

    @Test
    @Purpose("Test that a big file on tier 2 storage is retrieved even if the restore method is called")
    public void test_big_restore_tier2() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH, false, false);

        String fileName = "bigFile1.txt";
        String fileChecksum = "aaf14d43dbfb6c33244ec1a25531cb00";
        long fileSize = 22949;
        String nodeName = "deep/dir/testNode";
        TestRestoreProgressManager progressManager = new TestRestoreProgressManager();

        // Create the archive that contain the file to retrieve
        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

        String entryKey = s3Glacier.storageConfiguration.entryKey(Path.of(nodeName, fileName).toString());
        Path filePath = Path.of(S3GlacierRestoreIT.class.getResource("/files/" + fileName).toURI());

        Flux<ByteBuffer> buffers = DataBufferUtils.read(filePath,
                                                        new DefaultDataBufferFactory(),
                                                        MULTIPART_PARALLEL_PART * 1024 * 1024)
                                                  .map(DataBuffer::asByteBuffer);

        StorageEntry storageEntry = StorageEntry.builder()
                                                .config(s3Glacier.storageConfiguration)
                                                .fullPath(entryKey)
                                                .checksum(Option.some(Tuple.of(S3Glacier.MD5_CHECKSUM, fileChecksum)))
                                                .size(Option.some(fileSize))
                                                .data(buffers)
                                                .build();
        String taskId = "S3GlacierRestore" + archiveName;
        StorageCommand.Write writeCmd = new StorageCommand.Write.Impl(s3Glacier.storageConfiguration,
                                                                      new StorageCommandID(taskId, UUID.randomUUID()),
                                                                      entryKey,
                                                                      storageEntry);
        writeFileOnStorage(writeCmd);

        Path restorationWorkspace = workspace.getRoot().toPath().resolve("target");

        // When
        FileCacheRequest request = createFileCacheRequest(restorationWorkspace,
                                                          fileName,
                                                          fileChecksum,
                                                          fileSize,
                                                          nodeName,
                                                          null,
                                                          false);

        FileRestorationWorkingSubset workingSubset = new FileRestorationWorkingSubset(List.of(request));

        s3Glacier.retrieve(workingSubset, progressManager);

        // Then
        checkRestoreSuccess(fileName, fileChecksum, progressManager, restorationWorkspace);
    }

    @Test
    @Purpose("Test that a small file on tier 2 storage is restored then deleted even if the restore method is called")
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

        FileReference reference = createFileReference(fileName, fileChecksum, fileSize, nodeName, archiveName, false);

        FileDeletionRequest request = new FileDeletionRequest(reference,
                                                              "groupIdTest",
                                                              "sessionOwnerTest",
                                                              "sessionTest");
        FileDeletionWorkingSubset workingSubset = new FileDeletionWorkingSubset(List.of(request));
        s3Glacier.delete(workingSubset, progressManager);

        //Then
        checkDeletionOfOneFileSuccessWithPending(progressManager, fileName2, fileChecksum, nodeName, archiveName);
    }
}
