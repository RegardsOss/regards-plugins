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

/**
 * STAC source property configuration
 *
 * @author Marc SORDI
 */
@Data
@With
@AllArgsConstructor
@NoArgsConstructor
public class StacSourcePropertyConfiguration {

    public static final String REGARDS_FORMAT = "sourcePropertyFormat";

    public static final String REGARDS_FORMAT_MD = "regardsFormat.md";

    @PluginParameter(name = "sourcePropertyPath",
                     label = "Source model property path",
                     description = "This parameter defines the path to the model attribute and its corresponding "
                                   + "source property in a product")
    protected String sourcePropertyPath;

    @PluginParameter(name = "sourceJsonPropertyPath",
                     label = "JSON property path (for a JSON type attribute only)",
                     description = "If the source model attribute is of type JSON, "
                                   + " this parameter defines the path in the JSON structure where to read the value.",
                     optional = true)
    protected String sourceJsonPropertyPath;

    @PluginParameter(name = REGARDS_FORMAT,
                     label = "Format for the source property value",
                     markdown = REGARDS_FORMAT_MD,
                     optional = true)
    protected String sourcePropertyFormat;
}
