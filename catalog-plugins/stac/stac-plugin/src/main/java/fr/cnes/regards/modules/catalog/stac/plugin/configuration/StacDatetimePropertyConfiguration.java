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
import fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import io.vavr.control.Option;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

import static fr.cnes.regards.modules.catalog.stac.plugin.configuration.StacPropertyConfiguration.*;

/**
 * Definition of the configuration for a STAC property, defining which model attribute
 * it corresponds to and how to convert from the one to the other.
 */
@Data
@With
@AllArgsConstructor
@NoArgsConstructor
public class StacDatetimePropertyConfiguration {

    @PluginParameter(name = "sourcePropertyPath", label = "Source model property path", description =
        "This parameter defines the path to the model attribute and its corresponding "
        + "source property in a product (default value is mapped to an internal property)", optional = true,
        defaultValue = "creationDate")
    private String sourcePropertyPath;

    @PluginParameter(name = "sourceJsonPropertyPath", label = "JSON property path (for a JSON type attribute only)",
        description = "If the source model attribute is of type JSON, "
                      + " this parameter defines the path in the JSON structure where to read the value.",
        optional = true)
    private String sourceJsonPropertyPath;

    @PluginParameter(name = STAC_DYNAMIC_COLLECTION_LEVEL, label = "STAC dynamic collection level",
        markdown = STAC_DYNAMIC_COLLECTION_LEVEL_MD, defaultValue = "-1", optional = true)
    private Integer stacDynamicCollectionLevel;

    @PluginParameter(name = STAC_DYNAMIC_COLLECTION_FORMAT, label = "STAC dynamic collection format",
        markdown = STAC_DYNAMIC_COLLECTION_FORMAT_MD, optional = true)
    private String stacDynamicCollectionFormat;

    public StacPropertyConfiguration toStacPropertyConfiguration() {
        return new StacPropertyConfiguration(Option.of(sourcePropertyPath).getOrElse("creationDate"),
                                             sourceJsonPropertyPath,
                                             null,
                                             null,
                                             StacSpecConstants.PropertyName.DATETIME_PROPERTY_NAME,
                                             "",
                                             StacPropertyType.DATETIME.name(),
                                             null,
                                             false,
                                             stacDynamicCollectionLevel,
                                             stacDynamicCollectionFormat);
    }

}
