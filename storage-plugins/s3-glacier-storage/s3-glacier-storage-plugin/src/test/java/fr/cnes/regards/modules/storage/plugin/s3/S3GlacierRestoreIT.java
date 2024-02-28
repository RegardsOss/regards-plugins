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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import fr.cnes.regards.framework.s3.domain.StorageCommand;
import fr.cnes.regards.framework.s3.domain.StorageCommandID;
import fr.cnes.regards.framework.s3.domain.StorageEntry;
import fr.cnes.regards.framework.test.report.annotation.Purpose;
import fr.cnes.regards.framework.utils.file.ZipUtils;
import fr.cnes.regards.modules.fileaccess.plugin.domain.FileRestorationWorkingSubset;
import fr.cnes.regards.modules.fileaccess.plugin.domain.IRestorationProgressManager;
import fr.cnes.regards.modules.fileaccess.plugin.dto.FileCacheRequestDto;
import io.vavr.Tuple;
import io.vavr.control.Option;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Test class for {@link  S3Glacier#retrieve(FileRestorationWorkingSubset, IRestorationProgressManager)}
 *
 * @author Thibaud Michaudel
 **/
@SpringBootTest
public class S3GlacierRestoreIT extends AbstractS3GlacierIT {

    @Test
    @Purpose("Test that a file present in the archive building workspace is correctly restored")
    public void test_restore_local_build() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);

        // When
        TestRestoreProgressManager progressManager = new TestRestoreProgressManager();
        Path restorationWorkspace = workspace.getRoot().toPath().resolve("target");
        String fileName = "smallFile1.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";

        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));
        copyFileToWorkspace(ROOT_PATH,
                            S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName,
                            nodeName,
                            fileName,
                            S3Glacier.ZIP_DIR);
        FileCacheRequestDto request = createFileCacheRequestDto(restorationWorkspace,
                                                                fileName,
                                                                fileChecksum,
                                                                fileSize,
                                                                nodeName,
                                                                archiveName,
                                                                true);
        FileRestorationWorkingSubset workingSubset = new FileRestorationWorkingSubset(List.of(request));
        s3Glacier.retrieve(workingSubset, progressManager);

        // Then
        checkRestoreSuccess(fileChecksum, progressManager, restorationWorkspace);

    }

    @Test
    @Purpose("Test that a file present in the current building archive is correctly restored")
    public void test_restore_local_build_current() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);

        // When
        TestRestoreProgressManager progressManager = new TestRestoreProgressManager();
        Path restorationWorkspace = workspace.getRoot().toPath().resolve("target");
        String fileName = "smallFile1.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";

        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));
        copyFileToWorkspace(ROOT_PATH,
                            S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName + S3Glacier.CURRENT_ARCHIVE_SUFFIX,
                            nodeName,
                            fileName,
                            S3Glacier.ZIP_DIR);
        FileCacheRequestDto request = createFileCacheRequestDto(restorationWorkspace,
                                                                fileName,
                                                                fileChecksum,
                                                                fileSize,
                                                                nodeName,
                                                                archiveName,
                                                                true);
        FileRestorationWorkingSubset workingSubset = new FileRestorationWorkingSubset(List.of(request));
        s3Glacier.retrieve(workingSubset, progressManager);

        // Then
        checkRestoreSuccess(fileChecksum, progressManager, restorationWorkspace);

    }

    @Test
    @Purpose("Test that a file present in the archive building workspace is correctly restored")
    public void test_restore_local_cache_file() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);

        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));
        String fileName = "smallFile1.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";
        TestRestoreProgressManager progressManager = new TestRestoreProgressManager();
        Path restorationWorkspace = workspace.getRoot().toPath().resolve("target");
        copyFileToWorkspace(ROOT_PATH,
                            S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName,
                            nodeName,
                            fileName,
                            S3Glacier.TMP_DIR);

        // When
        FileCacheRequestDto request = createFileCacheRequestDto(restorationWorkspace,
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
    @Purpose("Test that a file present in an archive in the archive building workspace is correctly restored")
    public void test_restore_local_cache_archive() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);

        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));
        String fileName = "smallFile1.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";
        TestRestoreProgressManager progressManager = new TestRestoreProgressManager();
        Path restorationWorkspace = workspace.getRoot().toPath().resolve("target");
        File file = Path.of(S3GlacierRestoreIT.class.getResource("/files/" + fileName).toURI()).toFile();
        Path archivePath = Path.of(workspace.getRoot().toString(),
                                   S3Glacier.TMP_DIR,
                                   ROOT_PATH,
                                   nodeName,
                                   archiveName + S3Glacier.ARCHIVE_EXTENSION);
        Files.createDirectories(archivePath.getParent());
        ZipUtils.createZipArchive(archivePath.toFile(), List.of(file));

        // When
        FileCacheRequestDto request = createFileCacheRequestDto(restorationWorkspace,
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
    @Purpose("Test that a small file that is not already present in the glacier is correctly retrieved after being "
             + "restored")
    public void test_small_restore_glacier() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);

        String fileName = "smallFile1.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";
        TestRestoreProgressManager progressManager = new TestRestoreProgressManager();

        // Create the archive that contain the file to retrieve
        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

        StorageCommand.Write writeCmd = createTestArchiveAndBuildWriteCmd(List.of(fileName), nodeName, archiveName);

        Path restorationWorkspace = workspace.getRoot().toPath().resolve("target");

        // When
        FileCacheRequestDto request = createFileCacheRequestDto(restorationWorkspace,
                                                                fileName,
                                                                fileChecksum,
                                                                fileSize,
                                                                nodeName,
                                                                archiveName,
                                                                false);

        FileRestorationWorkingSubset workingSubset = new FileRestorationWorkingSubset(List.of(request));

        writeFileOnStorage(writeCmd);
        s3Glacier.retrieve(workingSubset, progressManager);

        // Then
        checkRestoreSuccess(fileChecksum, progressManager, restorationWorkspace);
    }

    @Test
    @Purpose("Test that after the progress manager is correctly notified when the restoring of a file fail")
    public void test_small_restore_glacier_failure() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);

        String fileName = "smallFile1.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";
        TestRestoreProgressManager progressManager = new TestRestoreProgressManager();

        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

        Path restorationWorkspace = workspace.getRoot().toPath().resolve("target");

        // When
        FileCacheRequestDto request = createFileCacheRequestDto(restorationWorkspace,
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
        Assertions.assertEquals(1, progressManager.getRestoreFailed().size(), "There should be one failure");
        Assertions.assertEquals(fileChecksum,
                                progressManager.getRestoreFailed().get(0).getChecksum(),
                                "the failed request file checksum is not the expected one");
        Assertions.assertEquals(0, progressManager.getRestoreSucceed().size(), "There should be no success");

    }

    @Test
    @Purpose("Test that a big file that is not already present in the glacier is correctly retrieved after being "
             + "restored in internal cache")
    public void test_big_restore_glacier_internalCache()
        throws IOException, URISyntaxException, NoSuchAlgorithmException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);

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
                                                        UPLOAD_WITH_MULTIPART_THRESHOLD_IN_MB * 1024 * 1024)
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

        Path restorationWorkspace = workspace.getRoot().toPath().resolve("target");

        // When
        FileCacheRequestDto request = createFileCacheRequestDto(restorationWorkspace,
                                                                fileName,
                                                                fileChecksum,
                                                                fileSize,
                                                                nodeName,
                                                                null,
                                                                false);

        FileRestorationWorkingSubset workingSubset = new FileRestorationWorkingSubset(List.of(request));

        writeFileOnStorage(writeCmd);
        s3Glacier.retrieve(workingSubset, progressManager);

        // Then
        checkRestoreSuccess(fileName, fileChecksum, progressManager, restorationWorkspace);
    }

    @Test
    @Purpose("Test that a big file that is not already present in the glacier is correctly retrieved after being "
             + "restored in external cache")
    public void test_big_restore_glacier_externalCache() throws URISyntaxException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH, MockedS3ClientType.S3Client, false, true);

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
                                                        UPLOAD_WITH_MULTIPART_THRESHOLD_IN_MB * 1024 * 1024)
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

        Path restorationWorkspace = workspace.getRoot().toPath().resolve("target");

        // When
        // restorationWorkspace is not useful here because the test is realized in external cache, but set in order
        // to follow the logic business. The request doesn't know if it uses internal cache or external cache.
        FileCacheRequestDto request = createFileCacheRequestDto(restorationWorkspace,
                                                                fileName,
                                                                fileChecksum,
                                                                fileSize,
                                                                nodeName,
                                                                null,
                                                                false);

        FileRestorationWorkingSubset workingSubset = new FileRestorationWorkingSubset(List.of(request));

        writeFileOnStorage(writeCmd);
        s3Glacier.retrieve(workingSubset, progressManager);

        // Then
        Awaitility.await().atMost(Durations.FIVE_SECONDS).until(() -> progressManager.countAllReports() == 1);
        Assertions.assertEquals(1, progressManager.getRestoreSucceed().size(), "There should be one success");
        Assertions.assertEquals(fileChecksum,
                                progressManager.getRestoreSucceed().get(0).getChecksum(),
                                "The successful request is not the expected one");
        Assertions.assertEquals(0, progressManager.getRestoreFailed().size());

        Assertions.assertEquals(fileSize, progressManager.getFileSize());
        Assertions.assertEquals(s3Glacier.storageConfiguration.entryKeyUrl(entryKey),
                                progressManager.getRestoredFileUrl());
        Assertions.assertNull(progressManager.getExpirationDate());

        Assertions.assertFalse(Files.isDirectory(restorationWorkspace),
                               "The target directory(internal cache) should not be created, so nothing file copied in"
                               + " internal cache");
    }

    @Test
    @Purpose("Test that the process fail correctly when there is an unexpected error thrown by the LockService")
    public void test_unexpected_restore_error()
        throws IOException, URISyntaxException, NoSuchAlgorithmException, InterruptedException {
        // Given
        loadPlugin(endPoint,
                   region,
                   key,
                   secret,
                   BUCKET_OUTPUT,
                   ROOT_PATH,
                   MockedS3ClientType.MockedS3Client,
                   true,
                   false);

        String fileName = "smallFile1.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";
        TestRestoreProgressManager progressManager = new TestRestoreProgressManager();

        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

        Path restorationWorkspace = workspace.getRoot().toPath().resolve("target");

        FileCacheRequestDto request = createFileCacheRequestDto(restorationWorkspace,
                                                                fileName,
                                                                fileChecksum,
                                                                fileSize,
                                                                nodeName,
                                                                archiveName,
                                                                false);

        FileRestorationWorkingSubset workingSubset = new FileRestorationWorkingSubset(List.of(request));

        Logger fooLogger = (Logger) LoggerFactory.getLogger(S3Glacier.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        fooLogger.addAppender(listAppender);

        // When
        s3Glacier.retrieve(workingSubset, progressManager);

        // Then
        Awaitility.await()
                  .atMost(Durations.TEN_SECONDS)
                  .until(() -> listAppender.list.stream()
                                                .map(ILoggingEvent::getMessage)
                                                .anyMatch(message -> message.equals("Error during retrieval process")));
    }
}
