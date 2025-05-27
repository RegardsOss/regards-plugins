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
package fr.cnes.regards.modules.storage.s3.common;

import fr.cnes.regards.modules.fileaccess.dto.FileLocationDto;
import fr.cnes.regards.modules.fileaccess.dto.FileReferenceMetaInfoDto;
import fr.cnes.regards.modules.fileaccess.dto.FileReferenceWithoutOwnersDto;
import fr.cnes.regards.modules.fileaccess.plugin.domain.FileDeletionWorkingSubset;
import fr.cnes.regards.modules.fileaccess.plugin.domain.FileStorageWorkingSubset;
import fr.cnes.regards.modules.fileaccess.plugin.domain.IDeletionProgressManager;
import fr.cnes.regards.modules.fileaccess.plugin.domain.IStorageProgressManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.time.OffsetDateTime;

/**
 * Test for {@link AbstractS3Storage#getEntryKey(String)}
 *
 * @author Thibaud Michaudel
 **/
public class GetEntryKeyTest {

    private AbstractS3Storage storage;

    private String bucket = "myAwesomeBucket";

    @BeforeEach
    public void init() {
        storage = new TestS3Storage("https://myOldServer.cs.fr:9200/", bucket);
    }

    @Test
    public void test_get_entry_key_for_file_reference() throws MalformedURLException {
        // Given
        String resource = "this/is/the/path/to/my/file.txt";
        String url = "http://insaneS3server.cnes.fr:9000/" + bucket + "/" + resource;

        FileReferenceWithoutOwnersDto fileReference = new FileReferenceWithoutOwnersDto(OffsetDateTime.now(),
                                                                                        new FileReferenceMetaInfoDto(),
                                                                                        new FileLocationDto("storage",
                                                                                                            url));
        // When, then
        Assertions.assertEquals(resource, storage.getEntryKey(fileReference.getLocation().getUrl()));
    }

    @Test
    public void test_get_entry_key_malformed() {
        // Given
        String resource = "this/is/the/path/to/my/file.txt";
        String url = "insaneS3server.cnes.fr:9000/" + bucket + "/" + resource;

        FileReferenceWithoutOwnersDto fileReference = new FileReferenceWithoutOwnersDto(OffsetDateTime.now(),
                                                                                        new FileReferenceMetaInfoDto(),
                                                                                        new FileLocationDto("storage",
                                                                                                            url));
        // When, then
        Assertions.assertThrows(MalformedURLException.class, () -> {
            storage.getEntryKey(fileReference.getLocation().getUrl());
        });
    }

    @Test
    public void test_get_entry_key_for_small_file_reference() throws MalformedURLException {
        // Given
        String resource = "this/is/the/path/to/my/archive.zip?fileName=file.txt";
        String url = "http://insaneS3server.cnes.fr:9000/" + bucket + "/" + resource;

        FileReferenceWithoutOwnersDto fileReference = new FileReferenceWithoutOwnersDto(OffsetDateTime.now(),
                                                                                        new FileReferenceMetaInfoDto(),
                                                                                        new FileLocationDto("storage",
                                                                                                            url));
        // When, then
        Assertions.assertEquals(resource, storage.getEntryKey(fileReference.getLocation().getUrl()));
    }

    private static class TestS3Storage extends AbstractS3Storage {

        public TestS3Storage(String endpoint, String bucket) {
            this.bucket = bucket;
            this.endpoint = endpoint;
        }

        @Override
        public void delete(FileDeletionWorkingSubset workingSet, IDeletionProgressManager progressManager) {
        }

        @Override
        public void store(FileStorageWorkingSubset workingSet, IStorageProgressManager progressManager) {
        }
    }

}



