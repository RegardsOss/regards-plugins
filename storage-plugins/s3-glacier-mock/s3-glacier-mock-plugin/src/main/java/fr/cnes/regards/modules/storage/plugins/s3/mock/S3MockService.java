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

import fr.cnes.regards.modules.fileaccess.dto.FileReferenceWithoutOwnersDto;
import fr.cnes.regards.modules.fileaccess.dto.availability.NearlineFileStatusDto;
import fr.cnes.regards.modules.fileaccess.plugin.domain.NearlineFileNotAvailableException;
import fr.cnes.regards.modules.storage.dao.FileReferenceSpecification;
import fr.cnes.regards.modules.storage.dao.ICacheFileRepository;
import fr.cnes.regards.modules.storage.domain.database.CacheFile;
import fr.cnes.regards.modules.storage.domain.database.FileReference;
import fr.cnes.regards.modules.storage.service.cache.CacheService;
import fr.cnes.regards.modules.storage.service.file.FileReferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author tguillou
 */
@Service
public class S3MockService {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3MockService.class);

    private final FileReferenceService fileRefService;

    private final CacheService cacheService;

    private final ICacheFileRepository cacheFileRepository;

    private final Set<String> T3FileChecksums = new HashSet<>();

    private final Set<String> restoredFileChecksums = new HashSet<>();

    public S3MockService(FileReferenceService fileRefService,
                         CacheService cacheService,
                         ICacheFileRepository cacheFileRepository) {
        this.fileRefService = fileRefService;
        this.cacheService = cacheService;
        this.cacheFileRepository = cacheFileRepository;
    }

    public void throwIfCannotDownload(FileReferenceWithoutOwnersDto fileReference)
        throws NearlineFileNotAvailableException {
        String checksum = fileReference.getMetaInfo().getChecksum();
        if (T3FileChecksums.contains(checksum)) {
            if (!restoredFileChecksums.contains(checksum)) {
                LOGGER.info("[S3-MOCK] Try to download {} which is stored in T3 and is not restored",
                            fileReference.getMetaInfo().getFileName());
                throw new NearlineFileNotAvailableException("T3 file not downloadable");
            } else {
                LOGGER.info("[S3-MOCK] Try to download {} which is stored in T3, but has been restored and downloadable",
                            fileReference.getMetaInfo().getFileName());
            }
        } else {
            LOGGER.info("[S3-MOCK] Try to download {} which is stored in T2",
                        fileReference.getMetaInfo().getFileName());
        }
    }

    public Optional<NearlineFileStatusDto> checkAvailability(FileReferenceWithoutOwnersDto fileReference) {
        String checksum = fileReference.getMetaInfo().getChecksum();
        if (T3FileChecksums.contains(checksum)) {
            if (!restoredFileChecksums.contains(checksum)) {
                // file on T3 not restored
                return Optional.of(new NearlineFileStatusDto(false,
                                                             null,
                                                             "This file is on T3 and needs to be restored"));
            } else {
                // file on T3 restored, which will return available status to true
                return Optional.empty();
            }
        } else {
            // file on T2, call real plugin, which will return available status to true
            return Optional.empty();
        }
    }

    public void T2toT3forFileWithName(String fileNamePattern) {
        LOGGER.info("[S3-MOCK] pass from T2 to T3 files with name pattern {}", fileNamePattern);
        Set<String> checksums = getChecksumOfFilesWithName(fileNamePattern);
        T3FileChecksums.addAll(checksums);
    }

    public void restoreWithFileName(String fileNamePattern) {
        LOGGER.info("[S3-MOCK] pass from T3 to T2 files with name pattern {}", fileNamePattern);
        Set<String> checksums = getChecksumOfFilesWithName(fileNamePattern);
        restoredFileChecksums.addAll(checksums);
    }

    public void setExpiredDateToFileWithName(String fileNamePattern) {
        LOGGER.info("[S3-MOCK] set expired date to files with name pattern {}", fileNamePattern);
        Set<String> checksums = getChecksumOfFilesWithName(fileNamePattern);
        restoredFileChecksums.removeAll(checksums);
        Set<CacheFile> cacheFiles = cacheService.getCacheFiles(checksums);
        for (CacheFile cacheFile : cacheFiles) {
            cacheFile.setExpirationDate(OffsetDateTime.now().minusHours(1));
        }
        cacheFileRepository.saveAll(cacheFiles);
    }

    private Set<String> getChecksumOfFilesWithName(String fileNamePattern) {
        Specification<FileReference> spec = FileReferenceSpecification.search(fileNamePattern,
                                                                              null,
                                                                              null,
                                                                              null,
                                                                              null,
                                                                              null,
                                                                              null,
                                                                              Pageable.ofSize(2000));
        Page<FileReference> fileReferencePage = fileRefService.search(spec, Pageable.ofSize(2000));
        return fileReferencePage.stream()
                                .map(fileRef -> fileRef.getMetaInfo().getChecksum())
                                .collect(Collectors.toSet());
    }

}
