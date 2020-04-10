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
package fr.cnes.regards.modules.fem.plugins.service.factory;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.feature.domain.request.FeatureReferenceRequest;
import fr.cnes.regards.modules.feature.dto.Feature;

/**
 * Create a {@link Feature} from a {@link FeatureReferenceRequest}
 * We will use the file name to extract {@link Feature} metadata
 * @author Kevin Marchois
 */
public abstract class AbstractFeatureFactory {

    public static final String MODEL = "GEODE001";

    ////////////////////////// Properties list/////////////////////
    public static final String INGESTION_DATE = "ingestion_date";

    public static final String PRODUCTION_DATE = "production_date";

    public static final String UTC_START_DATE = "utc_start_date";

    public static final String UTC_END_DATE = "utc_end_date";

    public static final String TAI_START_DATE = "tai_start_date";

    public static final String TAI_END_DATE = "tai_end_date";

    public static final String DAY_DATE = "day_date";

    public static final String CYCLE_NUMBER = "cycle_number";

    public static final String PASS_NUMBER = "pass_number";

    public static final String TILE_NUMBER = "tile_number";

    public static final String TILE_SIDE = "tile_side";

    public static final String GRANULE_TYPE = "granule_type";

    public static final String CONTINENT_ID = "continent_id";

    public static final String BASSIN_ID = "bassin_id";

    public static final String CRID = "CRID";

    public static final String PRODUCT_COUNTER = "product_counter";

    public static final String STATION = "station";

    public static final String APID = "APID";

    public static final String TYPE = "type";
    /////////////////////////// End of properties ///////////////////////

    public static final String GROUND_STATION_PACKAGE_UNIT = "Ground station package unit";

    public static final String FULL_SWATH_HALF_ORBIT = "Full-swath half orbit";

    public static final String GLOBAL = "Global";

    public static final String FULL_SWATT_TILE = "Full-swath tile";

    public static final String TILE = "Tile";

    public static final String CONTINENT = "Continent-pass";

    public static final String BASSIN = "Basin-cycle";

    public static final String FULL_SWATH_SCENE = "Full-swath scene";

    protected Matcher matcher;

    public Feature createFeature(String fileName, String regex, String model) {
        Pattern pattern = Pattern.compile(regex);
        matcher = pattern.matcher(fileName);
        matcher.matches();
        Feature toCreate = Feature.build(fileName, null, null, EntityType.DATA, model);
        return toCreate;
    }

    public OffsetDateTime parseDate(String date) {
        return OffsetDateTime.of(LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd'T'hhmmss")).atStartOfDay(),
                                 ZoneOffset.UTC);
    }
}
