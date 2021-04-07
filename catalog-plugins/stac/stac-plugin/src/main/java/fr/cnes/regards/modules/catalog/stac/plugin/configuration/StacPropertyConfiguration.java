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

    public static final String STAC_DYNAMIC_COLLECTION_LEVEL = "stacDynamicCollectionLevel";
    public static final String STAC_DYNAMIC_COLLECTION_LEVEL_MD = STAC_DYNAMIC_COLLECTION_LEVEL + ".md";

    public static final String STAC_DYNAMIC_COLLECTION_FORMAT = "stacDynamicCollectionFormat";
    public static final String STAC_DYNAMIC_COLLECTION_FORMAT_MD = STAC_DYNAMIC_COLLECTION_FORMAT + ".md";

    public static final String STAC_FORMAT = "stacFormat";
    public static final String STAC_FORMAT_MD = STAC_FORMAT + ".md";

    public static final String REGARDS_FORMAT = "regardsFormat";
    public static final String REGARDS_FORMAT_MD = REGARDS_FORMAT + ".md";

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
                    " Only applicable for stacType value among 'ANGLE', 'LENGTH', 'PERCENTAGE' and 'NUMBER'."
    )
    private Boolean stacComputeSummary;

    @PluginParameter(
            name = STAC_DYNAMIC_COLLECTION_LEVEL,
            label = "STAC dynamic collection level",
            markdown = STAC_DYNAMIC_COLLECTION_LEVEL_MD,
            defaultValue = "-1",
            optional = true
    )
    private Integer stacDynamicCollectionLevel;

    @PluginParameter(
            name = STAC_DYNAMIC_COLLECTION_FORMAT,
            label = "STAC dynamic collection format",
            markdown = STAC_DYNAMIC_COLLECTION_FORMAT_MD,
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
                    "'BOOLEAN', " +
                    "'JSON_OBJECT'.",
            defaultValue = "STRING",
            optional = true
    )
    private String stacType;

    @PluginParameter(
            name = STAC_FORMAT,
            label = "Format for the STAC value",
            markdown = STAC_FORMAT_MD,
            optional = true
    )
    private String stacFormat;

    @PluginParameter(
            name = REGARDS_FORMAT,
            label = "Format for the REGARDS value",
            markdown = REGARDS_FORMAT_MD,
            optional = true
    )
    private String regardsFormat;

}
