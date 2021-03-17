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

package fr.cnes.regards.modules.catalog.stac.plugin.configuration;

import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

/**
 * Definition of the configuration for a STAC property, defining which model attribute
 * it corresponds to and how to convert from the one to the other.
 */
@Data @With @AllArgsConstructor @NoArgsConstructor
public class StacPropertyConfiguration {

    @PluginParameter(
            name = "modelPropertyName",
            label = "Regards property name",
            description = "This parameter determines which attribute model parameter" +
                    " to map to a STAC property."
    )
    private String modelPropertyName;

    @PluginParameter(
            name = "modelPropertyJSONPath",
            label = "Model property JSON path",
            description = "If the target REGARDS property is of type JSON, " +
                    " this parameter determines the path in the JSON structure where to read the value.",
            optional = true
    )
    private String modelPropertyJSONPath;

    @PluginParameter(
            name = "stacPropertyName",
            label = "STAC property name",
            description = "This parameter determines the name of" +
                    " the STAC property corresponding to the model attribute name.",
            optional = true
    )
    private String stacPropertyName;

    @PluginParameter(
            name = "stacExtension",
            label = "Name or URL of the STAC extension",
            description = "If this STAC property is not native, give the name of its extension," +
                    " or its URL if the extension is not part defined in the standard.",
            optional = true
    )
    private String stacExtension;

    @PluginParameter(
            name = "stacComputeSummary",
            label = "compute summary",
            description = "Whether a summary should be computed for this property in the collections." +
                    " Only applicable for stacType value among 'DATETIME', 'ANGLE', 'LENGTH', 'PERCENTAGE' and 'NUMBER'."
    )
    private Boolean stacComputeSummary;

    @PluginParameter(
            name = "stacDynamicCollectionLevel",
            label = "STAC dynamic collection level",
            description = "For dynamic collections, use this parameter as the given level." +
                    " Levels start at 1, and there must be only one STAC property defined for each level.",
            optional = true
    )
    private Integer stacDynamicCollectionLevel;

    @PluginParameter(
            name = "stacDynamicCollectionFormat",
            label = "STAC dynamic collection format",
            description = "For dynamic collections, use this parameter to define the format of the dynamic collection.",
            optional = true
    )
    private String stacDynamicCollectionFormat;

    @PluginParameter(
            name = "stacType",
            label = "Property type",
            description = "Should take a value among: " +
                    "'DATETIME', " +
                    "'URL', " +
                    "'STRING', " +
                    "'ANGLE', " +
                    "'LENGTH', " +
                    "'PERCENTAGE', " +
                    "'NUMBER', " +
                    "'GEOMETRY', " +
                    "'BBOX', " +
                    "'BOOLEAN', " +
                    "'OBJECT'. Default is 'STRING', unless for the mandatory datetime property in which case it is 'DATETIME'.",
            optional = true
    )
    private String stacType;

    @PluginParameter(
            name = "stacFormat",
            label = "Format for the STAC value",
            description = "This parameter describes the format for values in STAC." +
                    " Currently only used when type is 'DATETIME' or 'PERCENTAGE'. ",
            optional = true
    )
    private String stacFormat;

    @PluginParameter(
            name = "regardsFormat",
            label = "Format for the REGARDS value",
            description = "This parameter describes the format for values in REGARDS." +
                    " Currently only used when type is 'DATETIME' or 'PERCENTAGE'. ",
            optional = true
    )
    private String regardsFormat;

}
