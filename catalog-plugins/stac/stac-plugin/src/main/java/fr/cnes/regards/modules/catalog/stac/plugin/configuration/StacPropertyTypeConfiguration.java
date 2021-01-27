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
 * This class describes which type this property can have.
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class StacPropertyTypeConfiguration {

    // @formatter:off
    @PluginParameter(
        name = "type",
        label = "Property type",
        description = "Should take a value among: " +
            "'DURATION', " +
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
            "'OBJECT'."
    )
    private String type;

    @PluginParameter(
            name = "stacFormat",
            label = "Format for the STAC value",
            description = "This parameter describes the format for values in STAC." +
                    " Currently only used when type is 'DATETIME' or 'PERCENTAGE'. "
    )
    private String stacFormat;

    @PluginParameter(
            name = "regardsFormat",
            label = "Format for the REGARDS value",
            description = "This parameter describes the format for values in REGARDS." +
                    " Currently only used when type is 'DATETIME' or 'PERCENTAGE'. "
    )
    private String regardsFormat;

    // @formatter:on
}
