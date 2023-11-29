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
package fr.cnes.regards.modules.storage.plugin.local.dto;

import fr.cnes.regards.modules.filecatalog.dto.AbstractStoragePluginConfigurationDto;

/**
 * Configuration DTO for Local Storage Plugin
 *
 * @author Thibaud Michaudel
 **/
public class LocalStorageLocationConfigurationDto extends AbstractStoragePluginConfigurationDto {

    /**
     * Root directory where to store new files on this location
     */
    private String baseStorageLocationAsString;

    /**
     * If deletion is allowed, files are physically deleted else files are only removed from references
     */
    private Boolean allowPhysicalDeletion;

    /**
     * When storing a new file in this location, if the file size is less than this value, so the file is stored with other "small files" in a zip archive. The size is in octets.
     */
    private Long maxFileSizeForZip;

    /**
     * When storing a new file in this location, "small" files are stored in a zip archive which maximum size is configurable thanks this property. The size is in octets.
     */
    private Long maxZipSize;

    public LocalStorageLocationConfigurationDto(Boolean allowPhysicalDeletion,
                                                String baseStorageLocationAsString,
                                                Long maxFileSizeForZip,
                                                Long maxZipSize) {
        super(allowPhysicalDeletion);
        this.baseStorageLocationAsString = baseStorageLocationAsString;
        this.maxFileSizeForZip = maxFileSizeForZip;
        this.maxZipSize = maxZipSize;
    }

    public String getBaseStorageLocationAsString() {
        return baseStorageLocationAsString;
    }

    public Boolean getAllowPhysicalDeletion() {
        return allowPhysicalDeletion;
    }

    public Long getMaxFileSizeForZip() {
        return maxFileSizeForZip;
    }

    public Long getMaxZipSize() {
        return maxZipSize;
    }
}
