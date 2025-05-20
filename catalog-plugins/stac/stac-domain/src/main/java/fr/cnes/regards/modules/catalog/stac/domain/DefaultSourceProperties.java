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

import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;

import java.util.List;

/**
 * Default source properties.
 *
 * @author Marc SORDI
 */
public final class DefaultSourceProperties {

    public static final List<String> INTERNAL_PROPERTIES = List.of("creationDate", "lastUpdate", "ipId", "type");

    public static final String PROPERTY_NAMESPACE = StaticProperties.FEATURE_PROPERTIES + ".";

    public static final String COLLECTION_TITLE_SOURCE_PROPERTY_NAME = PROPERTY_NAMESPACE + "title";

    public static final String COLLECTION_DESCRIPTION_SOURCE_PROPERTY_NAME = PROPERTY_NAMESPACE + "description";

    public static final String COLLECTION_KEYWORDS_SOURCE_PROPERTY_NAME = PROPERTY_NAMESPACE + "keywords";

    public static final String COLLECTION_LICENSE_SOURCE_PROPERTY_NAME = PROPERTY_NAMESPACE + "license";

    public static final String STAC_PROVIDERS_SOURCE_PROPERTY_NAME = PROPERTY_NAMESPACE + "providers";

    public static final String STAC_LINKS_SOURCE_PROPERTY_NAME = PROPERTY_NAMESPACE + "links";

    public static final String STAC_ASSETS_SOURCE_PROPERTY_NAME = PROPERTY_NAMESPACE + "assets";

    private DefaultSourceProperties() {
        // Utility class
    }
}
