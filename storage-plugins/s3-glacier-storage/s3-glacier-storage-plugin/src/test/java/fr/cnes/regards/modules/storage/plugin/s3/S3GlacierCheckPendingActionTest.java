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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import fr.cnes.regards.framework.s3.domain.StorageCommand;
import fr.cnes.regards.framework.test.report.annotation.Purpose;
import fr.cnes.regards.modules.fileaccess.dto.FileReferenceWithoutOwnersDto;
import fr.cnes.regards.modules.fileaccess.plugin.domain.IPeriodicActionProgressManager;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Test class for {@link S3Glacier#runCheckPendingAction(IPeriodicActionProgressManager, Set)}
 *
 * @author Thibaud Michaudel
 **/
@SpringBootTest
public class S3GlacierCheckPendingActionTest extends AbstractS3GlacierIT {

    @Test
    public void test_local_present_remote_present() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        // Given
        loadPlugin(endPoint, region, key, secret, bucket, ROOT_PATH);

        TestPeriodicActionProgressManager progressManager = new TestPeriodicActionProgressManager();

        String fileName = "smallFile1.txt";
        String fileName2 = "smallFile2.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        String fileChecksum2 = "0e7de3ed4befa2c4a42ec404520d99ff";
        long fileSize = 446L;
        long fileSize2 = 445L;
        String nodeName = "deep/dir/testNode";
        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

        String oldCurrentDirName = S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName + S3Glacier.CURRENT_ARCHIVE_SUFFIX;
        FileReferenceWithoutOwnersDto fileRef = createFileReference(fileName,
                                                                    fileChecksum,
                                                                    fileSize,
                                                                    nodeName,
                                                                    archiveName,
                                                                    true);
        FileReferenceWithoutOwnersDto fileRef2 = createFileReference(fileName2,
                                                                     fileChecksum2,
                                                                     fileSize2,
                                                                     nodeName,
                                                                     archiveName,
                                                                     true);

        copyFileToWorkspace(ROOT_PATH, oldCurrentDirName, nodeName, fileName, S3Glacier.ZIP_DIR);
        copyFileToWorkspace(ROOT_PATH, oldCurrentDirName, nodeName, fileName2, S3Glacier.ZIP_DIR);

        StorageCommand.Write writeCmd = createTestArchiveAndBuildWriteCmd(List.of(fileName, fileName2),
                                                                          nodeName,
                                                                          archiveName);

        writeFileOnStorage(writeCmd);

        Path filePath = Path.of(workspace.getRoot().toString(),
                                S3Glacier.ZIP_DIR,
                                ROOT_PATH,
                                nodeName,
                                S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName + S3Glacier.CURRENT_ARCHIVE_SUFFIX,
                                fileName);

        Path filePath2 = Path.of(workspace.getRoot().toString(),
                                 S3Glacier.ZIP_DIR,
                                 ROOT_PATH,
                                 nodeName,
                                 S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName + S3Glacier.CURRENT_ARCHIVE_SUFFIX,
                                 fileName2);
        Assertions.assertTrue(Files.exists(filePath));
        Assertions.assertTrue(Files.exists(filePath2));
        // When
        s3Glacier.runCheckPendingAction(progressManager, new HashSet<>(Arrays.asList(fileRef, fileRef2)));

        // Then
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> progressManager.countAllReports() == 2);

        Assertions.assertEquals(2,
                                progressManager.getStoragePendingActionSucceed().size(),
                                "There should have been two successes");

        Assertions.assertFalse(Files.exists(filePath), "The file should have been deleted");
        Assertions.assertFalse(Files.exists(filePath2), "The file should have been deleted");
        Assertions.assertFalse(Files.exists(filePath.getParent()), "The directory should have been deleted");

    }

    @Test
    @Purpose("Nominal case, nothing to do ")
    public void test_local_present_remote_absent() throws IOException, URISyntaxException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);

        TestPeriodicActionProgressManager progressManager = new TestPeriodicActionProgressManager();

        String fileName = "smallFile1.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";
        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

        String oldCurrentDirName = S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName + S3Glacier.CURRENT_ARCHIVE_SUFFIX;

        FileReferenceWithoutOwnersDto fileRef = createFileReference(fileName,
                                                                    fileChecksum,
                                                                    fileSize,
                                                                    nodeName,
                                                                    archiveName,
                                                                    true);

        copyFileToWorkspace(ROOT_PATH, oldCurrentDirName, nodeName, fileName, S3Glacier.ZIP_DIR);

        Logger glacierLogger = (Logger) getLogger(S3Glacier.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        glacierLogger.addAppender(listAppender);

        // When
        s3Glacier.runCheckPendingAction(progressManager, new HashSet<>(Collections.singletonList(fileRef)));

        // Then

        // Wait for the process end log
        Awaitility.await()
                  .atMost(Durations.TEN_SECONDS)
                  .until(() -> listAppender.list.stream()
                                                .map(ILoggingEvent::getMessage)
                                                .anyMatch(m -> m.equals("Glacier periodic pending actions ended")));

        Assertions.assertEquals(0,
                                progressManager.countAllReports(),
                                "There should be no report in the progress manager");

    }

    @Test
    public void test_local_absent_remote_present() throws URISyntaxException, IOException, NoSuchAlgorithmException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);
        TestPeriodicActionProgressManager progressManager = new TestPeriodicActionProgressManager();

        String fileName = "smallFile1.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";

        // Create the archive that contain the file to retrieve
        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

        StorageCommand.Write writeCmd = createTestArchiveAndBuildWriteCmd(List.of(fileName), nodeName, archiveName);

        writeFileOnStorage(writeCmd);

        FileReferenceWithoutOwnersDto fileRef = createFileReference(fileName,
                                                                    fileChecksum,
                                                                    fileSize,
                                                                    nodeName,
                                                                    archiveName,
                                                                    true);

        // When
        s3Glacier.runCheckPendingAction(progressManager, new HashSet<>(Collections.singletonList(fileRef)));

        // Then
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> progressManager.countAllReports() == 1);

        Assertions.assertEquals(1,
                                progressManager.getStoragePendingActionSucceed().size(),
                                "There should have been one success");

    }

    @Test
    public void test_local_absent_remote_absent() {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);
        TestPeriodicActionProgressManager progressManager = new TestPeriodicActionProgressManager();

        String fileName = "smallFile1.txt";
        String fileChecksum = "83e93a40da8ad9e6ed0ab9ef852e7e39";
        long fileSize = 446L;
        String nodeName = "deep/dir/testNode";
        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

        FileReferenceWithoutOwnersDto fileRef = createFileReference(fileName,
                                                                    fileChecksum,
                                                                    fileSize,
                                                                    nodeName,
                                                                    archiveName,
                                                                    true);

        // When
        s3Glacier.runCheckPendingAction(progressManager, new HashSet<>(Collections.singletonList(fileRef)));

        // Then
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> progressManager.countAllReports() == 1);

        Assertions.assertEquals(1,
                                progressManager.getStoragePendingActionError().size(),
                                "There should have been one error");
    }
}
