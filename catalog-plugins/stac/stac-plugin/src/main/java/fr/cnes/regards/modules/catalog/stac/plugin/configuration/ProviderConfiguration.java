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

import java.util.List;

/**
 * Configuration for the STAC collection providers, associated to datasets in
 * the {@link CollectionConfiguration}.
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class ProviderConfiguration {

    @PluginParameter(name = "providerName",
            label = "Provider name",
            description = "Name of the institution which provides this collection.",
            optional = true)
    String providerName;

    @PluginParameter(name = "providerDescription",
            label = "Provider description",
            description = "Description of the institution which provides this collection.",
            optional = true)
    String providerDescription;

    @PluginParameter(name = "providerUrl",
            label = "Provider URL",
            description = "URL to the institution which provides this collection.",
            optional = true)
    String providerUrl;

    @PluginParameter(name = "providerRoles",
            label = "Provider roles",
            description = "Roles of the institution which provides this collection. " +
                    " Must be only among the following values:" +
                    " 'LICENSOR', 'PRODUCER', 'PROCESSOR', 'HOST'.",
            optional = true)
    List<String> providerRoles;
}
