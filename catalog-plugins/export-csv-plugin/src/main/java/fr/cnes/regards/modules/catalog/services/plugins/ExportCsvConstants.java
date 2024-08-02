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
package fr.cnes.regards.modules.catalog.services.plugins;

import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;

import java.util.List;

/**
 * @author Iliana Ghazali
 **/
public final class ExportCsvConstants {

    // Service configuration

    public static final int DEFAULT_DATA_OBJECTS_PAGE_SIZE = 1000;

    public static final String CSV_FILENAME_PATTERN = "csv_export_%s.csv";

    // Plugin parameters

    public static final String DYNAMIC_CSV_FILENAME = "dynamicCsvFilename";

    public static final String MAX_DATA_OBJECTS_TO_EXPORT = "maxDataObjectsToExport";

    public static final String DYNAMIC_PROPERTIES = "dynamicPropertiesToRetrieve";

    public static final String BASIC_PROPERTIES = "basicPropertiesToRetrieve";

    public static final List<String> BASIC_HEADER = List.of(StaticProperties.FEATURE_ID,
                                                            StaticProperties.FEATURE_PROVIDER_ID,
                                                            StaticProperties.FEATURE_LABEL,
                                                            StaticProperties.MODEL_TYPE,
                                                            StaticProperties.FEATURE_FILES,
                                                            StaticProperties.FEATURE_TAGS);

    private ExportCsvConstants() {
        // constant class
    }

}
