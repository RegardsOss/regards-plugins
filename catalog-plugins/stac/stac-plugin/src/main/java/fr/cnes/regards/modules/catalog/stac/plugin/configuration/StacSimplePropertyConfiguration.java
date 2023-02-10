/*
 * Copyright 2017-2022 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

@Data
@With
@AllArgsConstructor
@NoArgsConstructor
public class StacSimplePropertyConfiguration extends StacSourcePropertyConfiguration {

    public static final String STAC_FORMAT = "stacPropertyFormat";

    public static final String STAC_FORMAT_MD = "stacFormat.md";

    public StacSimplePropertyConfiguration(String sourcePropertyPath,
                                           String sourceJsonPropertyPath,
                                           String sourcePropertyFormat,
                                           String stacPropertyNamespace,
                                           String stacPropertyName,
                                           String stacPropertyExtension,
                                           String stacPropertyType,
                                           String stacPropertyFormat) {
        super(sourcePropertyPath, sourceJsonPropertyPath, sourcePropertyFormat);
        this.stacPropertyNamespace = stacPropertyNamespace;
        this.stacPropertyName = stacPropertyName;
        this.stacPropertyExtension = stacPropertyExtension;
        this.stacPropertyType = stacPropertyType;
        this.stacPropertyFormat = stacPropertyFormat;
    }

    @PluginParameter(name = "stacPropertyNamespace",
                     label = "Enclosing object name (i.e. namespace) for current property",
                     description = "This parameter determines an optional enclosing object for current property",
                     optional = true)
    protected String stacPropertyNamespace;

    @PluginParameter(name = "stacPropertyName",
                     label = "STAC property name (Expected format : {extension:}name)",
                     description = "This parameter determines the name of"
                                   + " the STAC property corresponding to the model attribute name.")
    protected String stacPropertyName;

    // FIXME optional?
    @PluginParameter(name = "stacPropertyExtension",
                     label = "Name or URL of the STAC extension",
                     description = "If this STAC property is not defined in the standard, give the name or URL of its extension.",
                     optional = true)
    protected String stacPropertyExtension;

    @PluginParameter(name = "stacPropertyType",
                     label = "STAC property type",
                     description = "Should take a value among: "
                                   + "'DATETIME', "
                                   + "'URL', "
                                   + "'STRING', "
                                   + "'ANGLE', "
                                   + "'LENGTH', "
                                   + "'PERCENTAGE', "
                                   + "'NUMBER', "
                                   + "'BOOLEAN', "
                                   + "'JSON_OBJECT'.",
                     defaultValue = "STRING",
                     optional = true)
    protected String stacPropertyType;

    @PluginParameter(name = STAC_FORMAT,
                     label = "Format for the STAC value",
                     markdown = STAC_FORMAT_MD,
                     optional = true)
    protected String stacPropertyFormat;
}
