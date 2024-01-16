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
 * along with REGARDS. If not, see `<http://www.gnu.org/licenses/>`.
 */
package fr.cnes.regards.modules.storage.plugins.s3.mock;

import fr.cnes.regards.modules.fileaccess.plugin.domain.NearlineDownloadException;
import fr.cnes.regards.modules.fileaccess.plugin.domain.NearlineFileNotAvailableException;
import fr.cnes.regards.modules.filecatalog.dto.FileLocationDto;
import fr.cnes.regards.modules.filecatalog.dto.FileReferenceMetaInfoDto;
import fr.cnes.regards.modules.filecatalog.dto.FileReferenceWithoutOwnersDto;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.time.OffsetDateTime;

/**
 * @author tguillou
 */
public class S3GlacierMockTest {

    private static final String FILE_1 = "FILE_1";

    private static final String FILE_2 = "FILE_2";

    private static final String FILE_3 = "FILE_3";

    @Test
    public void testAvailabilityMock() {
        // GIVEN
        S3GlacierPluginMock s3GlacierPluginMock = new S3GlacierPluginMock();
        FileReferenceWithoutOwnersDto fileT2 = createFileReference(FILE_1, DatalakeStorageStatus.T2);
        FileReferenceWithoutOwnersDto fileT3 = createFileReference(FILE_2, DatalakeStorageStatus.T3);
        FileReferenceWithoutOwnersDto fileT3Restored = createFileReference(FILE_3, DatalakeStorageStatus.T3_RESTORED);
        // WHEN checking availability THEN
        Assertions.assertTrue(s3GlacierPluginMock.checkAvailability(fileT2).isAvailable());
        Assertions.assertFalse(s3GlacierPluginMock.checkAvailability(fileT3).isAvailable());
        Assertions.assertFalse(s3GlacierPluginMock.checkAvailability(fileT3Restored).isAvailable());
    }

    @Test
    public void testDownloadMock() throws NearlineDownloadException, NearlineFileNotAvailableException {
        // GIVEN
        S3GlacierPluginMock s3GlacierPluginMock = new S3GlacierPluginMock();
        FileReferenceWithoutOwnersDto fileT2 = createFileReference(FILE_1, DatalakeStorageStatus.T2);
        FileReferenceWithoutOwnersDto fileT3 = createFileReference(FILE_2, DatalakeStorageStatus.T3);
        FileReferenceWithoutOwnersDto fileT3Restored = createFileReference(FILE_3, DatalakeStorageStatus.T3_RESTORED);
        try {
            // WHEN
            s3GlacierPluginMock.download(fileT2);
            Assertions.fail("S3Glacier.download fail because no plugin param is indicated");
        } catch (NullPointerException e) {
            // THEN fake assertions -> S3Glacier.download is called and we are happy
            Assertions.assertEquals("Cannot invoke \"String.indexOf(String)\" because \"s\" is null", e.getMessage());
        }
        try {
            // WHEN
            s3GlacierPluginMock.download(fileT3);
            // THEN S3Glacier.download must not be called
            Assertions.fail("NotAvailableException must be throw here");
        } catch (NearlineFileNotAvailableException e) {
        }
        try {
            // WHEN
            s3GlacierPluginMock.download(fileT3Restored);
            Assertions.fail("S3Glacier.download fail because no plugin param is indicated");
        } catch (NullPointerException e) {
            // THEN fake assertions -> S3Glacier.download is called and we are happy
            Assertions.assertEquals("Cannot invoke \"String.indexOf(String)\" because \"s\" is null", e.getMessage());
        }
    }

    private FileReferenceWithoutOwnersDto createFileReference(String fileReferenceName,
                                                              DatalakeStorageStatus storageStatus) {
        String computedFileName = fileReferenceName + storageStatus.getSuffix();

        FileReferenceMetaInfoDto metaInfo = new FileReferenceMetaInfoDto(computedFileName,
                                                                         "md5",
                                                                         computedFileName,
                                                                         5L,
                                                                         null,
                                                                         null,
                                                                         "application/json",
                                                                         "type");
        boolean nearlineConfirmed = switch (storageStatus) {
            case T2, T3_RESTORED -> false;
            case T3, NONE -> true;
        };
        FileLocationDto location = new FileLocationDto("storageName", "url", false);
        return new FileReferenceWithoutOwnersDto(1l,
                                                 OffsetDateTime.now(),
                                                 metaInfo,
                                                 location,
                                                 false,
                                                 nearlineConfirmed);
    }
}
