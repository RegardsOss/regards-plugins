/*
 * Copyright 2017-2020 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.fem.plugins.dto;

/**
 * Enumeration of fixed properties to each SWOT Feature to add in system fragment.
 *
 * @author Sébastien Binda
 *
 */
public enum SystemPropertiyEnum {

    INGEST_DATE("ingestion_date"),
    CHANGE_DATE("change_date"),
    GPFS_URL("gpfs_url"),
    FILE_NAME("file_name"),
    FILE_SIZE("filesize"),
    EXTENSION("extension");

    private String propertyPath;

    SystemPropertiyEnum(String propertyPath) {
        this.propertyPath = propertyPath;
    }

    public String getPropertyPath() {
        return propertyPath;
    }

}
