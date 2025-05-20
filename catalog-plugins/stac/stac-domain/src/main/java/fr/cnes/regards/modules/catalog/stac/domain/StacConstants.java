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

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * STAC constants.
 *
 * @author Marc SORDI
 */
public final class StacConstants {

    // Current stable version of STAC core since 2024/09/10
    public static final String STAC_SPEC_VERSION = "1.1.0";

    // Current stable version of STAC API since 2023/04/24
    public static final String STAC_API_VERSION = "1.0.0";

    public static final DateTimeFormatter ISO_DATE_TIME_UTC = new DateTimeFormatterBuilder().parseCaseInsensitive()
                                                                                            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                                                                            .optionalStart()
                                                                                            .appendOffset("+HH:MM", "Z")
                                                                                            .toFormatter();

    /**
     * Default media type for STAC features
     */
    public static final String APPLICATION_GEO_JSON_MEDIA_TYPE = "application/geo+json";

    public static final String APPLICATION_JSON_MEDIA_TYPE = "application/json";

    public static final String DISABLE_AUTH_PARAMS = "disable-auth-params";

    public static final String AUTHORIZATION_PARAMS = "Authorization";

    private StacConstants() {
        // Utility class
    }

}
