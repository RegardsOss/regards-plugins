/*
 * Copyright 2017-2025 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.stac.domain;

/**
 * STAC properties.
 *
 * @author Marc SORDI
 */
public final class StacProperties {

    // Common

    public static final String STAC_PROVIDERS_PROPERTY_NAME = "providers";

    public static final String STAC_LINKS_PROPERTY_NAME = "links";

    public static final String STAC_ASSETS_PROPERTY_NAME = "assets";

    public static final String STAC_LOWER_TEMPORAL_EXTENT_PROPERTY_NAME = "lower_temporal";

    public static final String STAC_UPPER_TEMPORAL_EXTENT_NAME = "upper_temporal";

    // Items

    public static final String DATETIME_PROPERTY_NAME = "datetime";

    // Common metadata
    public static final String START_DATETIME_PROPERTY_NAME = "start_datetime";

    // Common metadata
    public static final String END_DATETIME_PROPERTY_NAME = "end_datetime";

    public static final String TAGS_PROPERTY_NAME = "tags";

    public static final String ID_PROPERTY_NAME = "ipId";

    // Collection

    public static final String COLLECTION_TITLE_PROPERTY_NAME = "title";

    public static final String COLLECTION_DESCRIPTION_PROPERTY_NAME = "description";

    public static final String COLLECTION_KEYWORDS_PROPERTY_NAME = "keywords";

    public static final String COLLECTION_LICENSE_PROPERTY_NAME = "license";

    private StacProperties() {
        // Utility class
    }
}
