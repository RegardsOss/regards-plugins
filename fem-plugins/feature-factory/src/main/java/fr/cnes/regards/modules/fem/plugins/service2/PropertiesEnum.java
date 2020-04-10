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
package fr.cnes.regards.modules.fem.plugins.service2;

/**
 * Enumeration of known properties to READ from {@link DataTypeDescriptor}s
 *
 * @author Sébastien Binda
 *
 */
public enum PropertiesEnum {

    APID_NUMBER("APIDnumber", "[0-9]{4}", PropertyType.INTEGER),

    FILE_IDENTIFIER("FileIdentifier", ".*", PropertyType.STRING),

    GRANULE_TYPE("GranuleType", ".*", PropertyType.STRING),

    CYCLE_ID("CycleID", "[0-9]{3}", PropertyType.INTEGER),

    PASS_ID("PassID", "[0-9]{3}", PropertyType.INTEGER),

    SCENE_ID("SceneID", "[0-9]{3}", PropertyType.INTEGER),

    RANGE_START_DATE("RangeBeginningDateTime", "[0-9]{8}T[0-9]{6}", PropertyType.DATE),

    RANGE_END_DATE("RangeEndingDateTime", "[0-9]{8}T[0-9]{6}", PropertyType.DATE),

    PRODUCT_COUNTER("ProductCounter", "[0-9]+", PropertyType.INTEGER),

    CRID("CRID", "[a-zA-Z0-9]*", PropertyType.STRING);

    private String name;

    private String pattern;

    private PropertyType type;

    PropertiesEnum(String name, String pattern, PropertyType type) {
        this.name = name;
        this.pattern = pattern;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getPattern() {
        return pattern;
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

}
