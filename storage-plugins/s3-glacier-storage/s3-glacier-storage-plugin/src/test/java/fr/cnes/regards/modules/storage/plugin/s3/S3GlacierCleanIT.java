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
import fr.cnes.regards.modules.storage.domain.plugin.IPeriodicActionProgressManager;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Test class for clean actions from {@link  S3Glacier#runPeriodicAction(IPeriodicActionProgressManager)}
 *
 * @author Thibaud Michaudel
 **/
@SpringBootTest
public class S3GlacierCleanIT extends AbstractS3GlacierIT {

    @Test
    @Purpose("Test that a file is correctly deleted when it is too old")
    public void test_periodic_clean_file() throws URISyntaxException, IOException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);
        FileTime tooOld = FileTime.from(OffsetDateTime.now().minusHours(CACHE_DURATION_IN_HOURS + 1).toInstant());

        Path cachePath = Path.of(workspace.getRoot().toString(), S3Glacier.TMP_DIR);
        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

        String fileName = "smallFile1.txt";
        String fileName2 = "smallFile2.txt";
        String nodeName = "deep/dir/testNode";
        Path fileResourcesPath = Path.of(S3GlacierRestoreIT.class.getResource("/files/" + fileName).toURI());
        Path fileResourcesPath2 = Path.of(S3GlacierRestoreIT.class.getResource("/files/" + fileName2).toURI());

        Path fileCachePath = cachePath.resolve(Path.of(ROOT_PATH,
                                                       nodeName,
                                                       S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName,
                                                       fileName));
        Path fileCachePath2 = cachePath.resolve(Path.of(ROOT_PATH,
                                                        nodeName,
                                                        S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName,
                                                        fileName2));
        Files.createDirectories(fileCachePath.getParent());
        Files.copy(fileResourcesPath, fileCachePath);
        Files.copy(fileResourcesPath2, fileCachePath2);

        Files.setLastModifiedTime(fileCachePath, tooOld);

        // When
        s3Glacier.runPeriodicAction(new AbstractS3GlacierIT.TestPeriodicActionProgressManager());

        // Then
        Assertions.assertFalse(Files.exists(fileCachePath),
                               "The file smallFile1 should have been deleted as it is too old");
        Assertions.assertTrue(Files.exists(fileCachePath2),
                              "The file smallFile2 should not have been deleted as it isn't too old");
    }

    @Test
    @Purpose("Test that a file is correctly deleted when it is too old and its directory is also deleted if empty")
    public void test_periodic_clean_dir() throws URISyntaxException, IOException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);
        FileTime tooOld = FileTime.from(OffsetDateTime.now().minusHours(CACHE_DURATION_IN_HOURS + 1).toInstant());

        Path cachePath = Path.of(workspace.getRoot().toString(), S3Glacier.TMP_DIR);
        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

        String fileName = "smallFile1.txt";
        String fileName2 = "smallFile2.txt";
        String nodeName = "deep/dir/testNode";
        Path fileResourcesPath = Path.of(S3GlacierRestoreIT.class.getResource("/files/" + fileName).toURI());
        Path fileResourcesPath2 = Path.of(S3GlacierRestoreIT.class.getResource("/files/" + fileName2).toURI());

        Path fileCachePath = cachePath.resolve(Path.of(ROOT_PATH,
                                                       nodeName,
                                                       S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName,
                                                       fileName));
        Path fileCachePath2 = cachePath.resolve(Path.of(ROOT_PATH,
                                                        nodeName,
                                                        S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName,
                                                        fileName2));
        Files.createDirectories(fileCachePath.getParent());
        Files.copy(fileResourcesPath, fileCachePath);
        Files.copy(fileResourcesPath2, fileCachePath2);

        Files.setLastModifiedTime(fileCachePath, tooOld);
        Files.setLastModifiedTime(fileCachePath2, tooOld);

        // When
        s3Glacier.runPeriodicAction(new AbstractS3GlacierIT.TestPeriodicActionProgressManager());

        // Then
        Assertions.assertFalse(Files.exists(fileCachePath),
                               "The file smallFile1 should have been deleted as it is too old");
        Assertions.assertFalse(Files.exists(fileCachePath2),
                               "The file smallFile2 should have been deleted as it is too old");
        Assertions.assertFalse(Files.exists(fileCachePath.getParent()));
    }

    @Test
    @Purpose("Test that a directory is not deleted even when it is too old if there is a symlink to it")
    public void test_periodic_keep_symlink()
        throws URISyntaxException, IOException, NoSuchMethodException, InvocationTargetException,
        IllegalAccessException {
        // Given
        loadPlugin(endPoint, region, key, secret, BUCKET_OUTPUT, ROOT_PATH);
        FileTime tooOld = FileTime.from(OffsetDateTime.now().minusHours(CACHE_DURATION_IN_HOURS + 1).toInstant());

        Path cachePath = Path.of(workspace.getRoot().toString(), S3Glacier.TMP_DIR);
        String archiveName = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(S3Glacier.ARCHIVE_DATE_FORMAT));

        String fileName = "smallFile1.txt";
        String fileName2 = "smallFile2.txt";
        String nodeName = "deep/dir/testNode";
        Path fileResourcesPath = Path.of(S3GlacierRestoreIT.class.getResource("/files/" + fileName).toURI());
        Path fileResourcesPath2 = Path.of(S3GlacierRestoreIT.class.getResource("/files/" + fileName2).toURI());

        Path fileCachePath = cachePath.resolve(Path.of(ROOT_PATH,
                                                       nodeName,
                                                       S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName,
                                                       fileName));
        Path fileCachePath2 = cachePath.resolve(Path.of(ROOT_PATH,
                                                        nodeName,
                                                        S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName,
                                                        fileName2));
        Path dirBuildingWorkspacePath = Path.of(workspace.getRoot().toString(),
                                                S3Glacier.ZIP_DIR,
                                                ROOT_PATH,
                                                nodeName,
                                                S3Glacier.BUILDING_DIRECTORY_PREFIX + archiveName);
        Files.createDirectories(fileCachePath.getParent());
        Files.copy(fileResourcesPath, fileCachePath);
        Files.copy(fileResourcesPath2, fileCachePath2);

        Files.setLastModifiedTime(fileCachePath, tooOld);
        Files.setLastModifiedTime(fileCachePath2, tooOld);

        Files.createDirectories(dirBuildingWorkspacePath.getParent());
        Files.createSymbolicLink(dirBuildingWorkspacePath, fileCachePath.getParent());

        Method cleanMethod = s3Glacier.getClass().getDeclaredMethod("cleanArchiveCache");
        cleanMethod.setAccessible(true);

        // When
        cleanMethod.invoke(s3Glacier, null);

        // Then
        Assertions.assertTrue(Files.exists(fileCachePath),
                              "The file smallFile1 should have been kept even it is too old because there is a "
                              + "symbolic link to the directory");
        Assertions.assertTrue(Files.exists(fileCachePath2),
                              "The file smallFile2 should have been kept even it is too old because there is a "
                              + "symbolic link to the directory");
        Assertions.assertTrue(Files.exists(fileCachePath.getParent()));
    }
}
