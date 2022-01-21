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
 * Definition of the configuration for a STAC property, defining which model attribute
 * it corresponds to and how to convert from the one to the other.
 */
@Data
@With
@NoArgsConstructor
@AllArgsConstructor
public class StacPropertyConfiguration extends StacSimplePropertyConfiguration {

    public static final String STAC_DYNAMIC_COLLECTION_LEVEL = "stacDynamicCollectionLevel";

    public static final String STAC_DYNAMIC_COLLECTION_LEVEL_MD = "stacDynamicCollectionLevel.md";

    public static final String STAC_DYNAMIC_COLLECTION_FORMAT = "stacDynamicCollectionFormat";

    public static final String STAC_DYNAMIC_COLLECTION_FORMAT_MD = "stacDynamicCollectionFormat.md";

    public StacPropertyConfiguration(String sourcePropertyPath, String sourceJsonPropertyPath,
            String sourcePropertyFormat, String stacPropertyNamespace, String stacPropertyName,
            String stacPropertyExtension, String stacPropertyType, String stacPropertyFormat,
            Boolean stacComputeSummary, Integer stacDynamicCollectionLevel, String stacDynamicCollectionFormat) {
        super(sourcePropertyPath, sourceJsonPropertyPath, sourcePropertyFormat, stacPropertyNamespace, stacPropertyName,
              stacPropertyExtension, stacPropertyType, stacPropertyFormat);
        this.stacComputeSummary = stacComputeSummary;
        this.stacDynamicCollectionLevel = stacDynamicCollectionLevel;
        this.stacDynamicCollectionFormat = stacDynamicCollectionFormat;
    }

    @PluginParameter(name = "stacComputeSummary", label = "Compute summary",
            description = "Whether a summary should be computed for this property in the collections."
                    + " Only applicable for STAC type value among 'ANGLE', 'LENGTH', 'PERCENTAGE' and 'NUMBER'.")
    private Boolean stacComputeSummary;

    @PluginParameter(name = STAC_DYNAMIC_COLLECTION_LEVEL, label = "STAC dynamic collection level",
            markdown = STAC_DYNAMIC_COLLECTION_LEVEL_MD, defaultValue = "-1", optional = true)
    private Integer stacDynamicCollectionLevel;

    @PluginParameter(name = STAC_DYNAMIC_COLLECTION_FORMAT, label = "STAC dynamic collection format",
            markdown = STAC_DYNAMIC_COLLECTION_FORMAT_MD, optional = true)
    private String stacDynamicCollectionFormat;
}
