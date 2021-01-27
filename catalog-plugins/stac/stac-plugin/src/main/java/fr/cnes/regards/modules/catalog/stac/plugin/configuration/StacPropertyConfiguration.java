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

/**
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StacPropertyConfiguration {

    @PluginParameter(
            name = "modelAttributeName",
            label = "Model attribute name",
            description = "This parameter determines which attribute model parameter" +
                    " to map to a STAC property."
    )
    private String modelAttributeName;

    @PluginParameter(
            name = "stacPropertyName",
            label = "STAC property name",
            description = "This parameter determines the name of" +
                    " the STAC property corresponding to the model attribute name."
    )
    private String stacPropertyName;

    @PluginParameter(
            name = "stacExtension",
            label = "Name or URL of the STAC extension",
            description = "If this STAC property is not native, give the name of its extension," +
                    " or its URL if the extension is not part defined in the standard."
    )
    private String stacExtension;

    @PluginParameter(
            name = "stacType",
            label = "STAC property type",
            description = "Defines the type and eventual format conversion details for this property."
    )
    private StacPropertyTypeConfiguration stacType;

    @PluginParameter(
            name = "stacComputeExtent",
            label = "compute extent",
            description = "Whether an extent should be computed for this property in the collections"
    )
    private Boolean stacComputeExtent;

    @PluginParameter(
            name = "stacDynamicCollectionLevel",
            label = "",
            description = ""
    )
    private Integer stacDynamicCollectionLevel;

    @PluginParameter(
            name = "spatial4jConfiguration",
            label = "Configuration for spatial4j",
            description = "This property configures the spatial4j library, allowing to compute bounding boxes from geometries."
    )
    private Spatial4jConfiguration spatial4jConfiguration;


}
