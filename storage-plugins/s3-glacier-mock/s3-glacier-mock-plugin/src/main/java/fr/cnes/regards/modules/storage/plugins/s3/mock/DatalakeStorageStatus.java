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

import java.util.Arrays;

/**
 * Determine the tier of the given file depending on his name suffix
 *
 * @author tguillou
 */
public enum DatalakeStorageStatus {
    /**
     * T2 file is available and downloadable
     */
    T2("_on_t2"),
    /**
     * T3 file is not available and not downloadable
     */
    T3("_on_t3"),
    /**
     * T3_RESTORED file is not available, but is downloadable (in workflow, user needs to restore it before)
     */
    T3_RESTORED("_on_t3_restored"),
    /**
     * NONE file for files that pattern is wrong, they are not available and not downloadable
     */
    NONE("");

    private final String suffix;

    DatalakeStorageStatus(String suffix) {
        this.suffix = suffix;
    }

    public static DatalakeStorageStatus getStatusOf(FileReferenceWithoutOwnersDto fileReference) {
        String fileName = fileReference.getMetaInfo().getFileName();
        return getDatalakeStorageStatus(fileName);
    }

    private static DatalakeStorageStatus getDatalakeStorageStatus(String fileName) {
        // remove extension
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf("."));
        }
        String finalFileName = fileName.toLowerCase();
        return Arrays.stream(DatalakeStorageStatus.values())
                     .filter(storage -> finalFileName.endsWith(storage.suffix))
                     .findFirst()
                     .orElse(NONE);
    }

    public String getSuffix() {
        return suffix;
    }
}