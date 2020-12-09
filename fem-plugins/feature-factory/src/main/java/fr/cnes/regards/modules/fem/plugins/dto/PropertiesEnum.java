/*
 * Copyright 2017-2019 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

import fr.cnes.regards.modules.fem.plugins.dto.transformer.ITransformer;
import fr.cnes.regards.modules.fem.plugins.dto.transformer.TileSideTransformer;

/**
 * Enumeration of known properties to READ from {@link DataTypeDescriptor}s<br/>
 *
 * A property is defined by :<ul>
 * <li><b>name : </b>Name of the property in yml description file</li>
 * <li><b>propertyPath : </b>Path of the property in GEODE Feature</li>
 * <li><b>pattern : </b>Regex of the  attribute in file names</li>
 * <li><b>type : </b>Type of the attribute</li>
 * <li><b>format : </b> [Optional] Format to parse dates</li>
 * <li><b>transformer : </b> [Optional] function to transform property value</li>
 * </ul>
 *
 *
 * @author Sébastien Binda
 *
 */
public enum PropertiesEnum {

    /** DATA Fragment */

    RANGE_START_DATE("RangeBeginningDateTime", Constants.DATA_START_DATE, Constants.PATTERN_8_T_6, PropertyType.DATE_TIME,
        Constants.FORMAT_8_T_6),

    RANGE_END_DATE("RangeEndingDateTime", Constants.DATA_END_DATE, Constants.PATTERN_8_T_6, PropertyType.DATE_TIME,
        Constants.FORMAT_8_T_6),

    DATE_TIME_BEGIN_TM("DateTimeBeginTM", Constants.DATA_START_DATE, Constants.PATTERN_8_T_6, PropertyType.DATE_TIME,
        Constants.FORMAT_8_T_6),

    DATE_TIME_END_TM("DateTimeEndTM", Constants.DATA_END_DATE, Constants.PATTERN_8_T_6, PropertyType.DATE_TIME,
        Constants.FORMAT_8_T_6),

    CREATION_DATE_TIME("CreationDateTime", Constants.DATA_PRODUCTION_DATE, Constants.PATTERN_8___6, PropertyType.DATE_TIME,
        Constants.FORMAT_8___6),

    START_DATE_TIME("StartDateTime", Constants.DATA_START_DATE, Constants.PATTERN_8___6, PropertyType.DATE_TIME,
        Constants.FORMAT_8___6),

    END_DATE_TIME("EndDateTime", Constants.DATA_END_DATE, Constants.PATTERN_8___6, PropertyType.DATE_TIME, Constants.FORMAT_8___6),

    PRODUCTION_DATE_TIME("ProductionDateTime", Constants.DATA_PRODUCTION_DATE, Constants.PATTERN_8_T_6, PropertyType.DATE_TIME,
        Constants.FORMAT_8_T_6),

    DATE_CREATION_FILE("DateCreationFile", Constants.DATA_PRODUCTION_DATE, "[0-9]{8}", PropertyType.DATE, "yyyyMMdd"),

    DATETIME_CREATION_FILE("DateTimeCreationFile", Constants.DATA_PRODUCTION_DATE, Constants.PATTERN_8_T_6, PropertyType.DATE_TIME,
        Constants.FORMAT_8_T_6),

    TYPE("TYPE", "type", ".*", PropertyType.STRING),

    /** SWOT Fragment */

    CRID("CRID", "swot.crid", "[a-zA-Z0-9]*", PropertyType.STRING),

    PRODUCT_COUNTER("ProductCounter", "swot.product_counter", "[0-9]+", PropertyType.INTEGER),

    PRODUCT_VERSION_RINEX("ProductVersionRinex", "swot.product_version", "[a-z]{1}", PropertyType.STRING),

    PRODUCT_VERSION("ProductVersion", "swot.product_version", "[a-z]{1}", PropertyType.STRING),

    STATION_NAME("StationName", "swot.station", "[A-Z]{3}", PropertyType.STRING),

    SOURCE_TYPE("SourceType", null, "O|T|S", PropertyType.STRING),

    SENSING_DATE_TIME("SensingDateDay", Constants.SWOT_DAY_DATE, "[0-9]{8}", PropertyType.DATE, "yyyyMMdd"),

    CYCLE_ID("CycleID", "swot.cycle_number", "[0-9]{3}", PropertyType.INTEGER),

    PASS_ID("PassID", "swot.pass_number", "[0-9]{3}", PropertyType.INTEGER),

    TILE_ID("TileID", "swot.tile_number", "[0-9]{3}", PropertyType.INTEGER),

    TILE_SIDE("TileSide", "swot.tile_side", ".*", PropertyType.STRING, new TileSideTransformer()),

    FILE_IDENTIFIER("FileIdentifier", "swot.file_identifier", ".*", PropertyType.STRING),

    APID_NUMBER("APIDnumber", "swot.apid", "[0-9]{1,4}", PropertyType.INTEGER),

    GRANULE_TYPE("GranuleType", "granule_type", ".*", PropertyType.STRING),

    CONTINENT_ID("ContinentID", "swot.continent_id", "[A-Z]{2}", PropertyType.STRING),

    SCENE_ID("SceneID", "swot.scene_id", "[0-9]{3}", PropertyType.INTEGER),

    BASIN_ID("BassinID", "swot.bassin_id", "[0-9]{3}", PropertyType.STRING);

    private String name;

    private String propertyPath;

    private String pattern;

    private PropertyType type;

    private String format;

    private ITransformer transformer;

    PropertiesEnum(String name, String featureName, String pattern, PropertyType type) {
        this.name = name;
        this.propertyPath = featureName;
        this.pattern = pattern;
        this.type = type;
        this.format = null;
        this.transformer = null;
    }

    PropertiesEnum(String name, String featureName, String pattern, PropertyType type, ITransformer transformer) {
        this.name = name;
        this.propertyPath = featureName;
        this.pattern = pattern;
        this.type = type;
        this.format = null;
        this.transformer = transformer;
    }

    PropertiesEnum(String name, String featureName, String pattern, PropertyType type, String format) {
        this.name = name;
        this.propertyPath = featureName;
        this.pattern = pattern;
        this.type = type;
        this.format = format;
    }

    PropertiesEnum(String name, String[] possibleNames, String pattern, PropertyType type, String format) {
        this.name = name;
        this.pattern = pattern;
        this.type = type;
        this.format = format;
    }

    public String getName() {
        return name;
    }

    public String getPattern() {
        return pattern;
    }

    public static PropertiesEnum get(String property) {
        PropertiesEnum prop = null;
        for (PropertiesEnum pp : PropertiesEnum.values()) {
            if (pp.getName().equals(property)) {
                prop = pp;
                break;
            }
        }
        return prop;
    }

    public static String getPattern(String property) {
        String pattern = "(.*)";
        for (PropertiesEnum pp : PropertiesEnum.values()) {
            if (pp.getName().equals(property)) {
                pattern = String.format("(%s)", pp.getPattern());
                break;
            }
        }
        return pattern;
    }

    public static String getFormat(String property) {
        String format = null;
        for (PropertiesEnum pp : PropertiesEnum.values()) {
            if (pp.getName().equals(property)) {
                format = pp.getFormat();
                break;
            }
        }
        return format;
    }

    public static PropertyType getType(String property) {
        PropertyType type = PropertyType.STRING;
        for (PropertiesEnum pp : PropertiesEnum.values()) {
            if (pp.getName().equals(property)) {
                type = pp.getType();
                break;
            }
        }
        return type;
    }

    public static String asConfPattern(String property) {
        return String.format("\\{\\{ %s \\}\\}", property);
    }

    public PropertyType getType() {
        return type;
    }

    public String getFormat() {
        return format;
    }

    public String getPropertyPath() {
        return propertyPath;
    }

    public ITransformer getTransformer() {
        return transformer;
    }

    public static class Constants {
        public static final String PATTERN_8_T_6 = "[0-9]{8}T[0-9]{6}";
        public static final String PATTERN_8___6 = "[0-9]{8}_[0-9]{6}";
        public static final String FORMAT_8_T_6 = "yyyyMMdd'T'HHmmss";
        public static final String FORMAT_8___6 = "yyyyMMdd'_'HHmmss";
        public static final String START_DATE = "start_date";
        public static final String DATA_START_DATE = "data." + START_DATE;
        public static final String END_DATE = "end_date";
        public static final String DATA_END_DATE = "data." + END_DATE;
        public static final String DATA_PRODUCTION_DATE = "data.production_date";
        public static final String SWOT_DAY_DATE = "swot.day_date";
    }
}
