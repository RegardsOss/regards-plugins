/*
 * Copyright 2017-2021 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
 * Lists a bunch of constant values defined by the STAC specification.
 */
public class StacSpecConstants {

    public interface Version {

        String STAC_SPEC_VERSION = "1.0.0-beta.2";

        String STAC_API_VERSION = "1.0.0-beta.1";
    }

    public interface PropertyName {

        String DATETIME_PROPERTY_NAME = "datetime";

        String TAGS_PROPERTY_NAME = "tags";

        String ID_PROPERTY_NAME = "ipId";
    }

    public static final DateTimeFormatter ISO_DATE_TIME_UTC = new DateTimeFormatterBuilder().parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME).optionalStart().appendOffset("+HH:MM", "Z").toFormatter();
}
