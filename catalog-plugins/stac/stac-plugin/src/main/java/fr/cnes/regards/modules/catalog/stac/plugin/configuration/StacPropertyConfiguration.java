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
    
    public static final String STAC_DYNAMIC_COLLECTION_LEVEL = "stac.dynamic.collection.level";
    public static final String STAC_DYNAMIC_COLLECTION_LEVEL_MD = "stacDynamicCollectionLevel.md";

    public static final String STAC_DYNAMIC_COLLECTION_FORMAT = "stac.dynamic.collection.format";
    public static final String STAC_DYNAMIC_COLLECTION_FORMAT_MD = "stacDynamicCollectionFormat.md";

    public static final String STAC_FORMAT = "target.stac.property.format";
    public static final String STAC_FORMAT_MD = "stacFormat.md";

    public static final String REGARDS_FORMAT = "source.property.format";
    public static final String REGARDS_FORMAT_MD = "regardsFormat.md";

    @PluginParameter(name = "source.property.path", label = "Source model property path",
            description = "This parameter defines the path to the model attribute and its corresponding "
                    + "source property in a product")
    private String sourcePropertyPath;

    @PluginParameter(name = "source.json.property.path", label = "JSON property path (for a JSON type attribute only)",
            description = "If the source model attribute is of type JSON, "
                    + " this parameter defines the path in the JSON structure where to read the value.",
            optional = true)
    private String sourceJsonPropertyPath;
    
    @PluginParameter(name = REGARDS_FORMAT, label = "Format for the source property value", markdown = REGARDS_FORMAT_MD,
            optional = true)
    private String  sourcePropertyFormat;

    @PluginParameter(
            name = "stac.property.name", label = "STAC property name (Expected format : {extension:}name)",
            description = "This parameter determines the name of"
                    + " the STAC property corresponding to the model attribute name.",
            optional = true)
    private String stacPropertyName;

    @PluginParameter(name = "stac.property.extension", label = "Name or URL of the STAC extension",
            description = "If this STAC property is not defined in the standard, give the name or URL of its extension.",
            optional = true)
    private String stacPropertyExtension;

    @PluginParameter(name = "stac.property.type", label = "STAC property type",
            description = "Should take a value among: " + "'DATETIME', " + "'URL', " + "'STRING', " + "'ANGLE', "
                    + "'LENGTH', " + "'PERCENTAGE', " + "'NUMBER', " + "'BOOLEAN', " + "'JSON_OBJECT'.",
            defaultValue = "STRING", optional = true)
    private String stacPropertyType;

    @PluginParameter(name = STAC_FORMAT, label = "Format for the STAC value", markdown = STAC_FORMAT_MD, optional = true)
    private String stacPropertyFormat;

    @PluginParameter(
            name = "stac.property.compute.summary",
            label = "Compute summary",
            description = "Whether a summary should be computed for this property in the collections." +
                    " Only applicable for STAC type value among 'ANGLE', 'LENGTH', 'PERCENTAGE' and 'NUMBER'."
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
}
