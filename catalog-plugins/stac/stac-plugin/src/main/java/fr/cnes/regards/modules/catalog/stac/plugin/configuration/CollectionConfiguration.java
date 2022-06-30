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

import java.util.List;

/**
 * Allows to configure the main catalog collection configuration.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CollectionConfiguration {

    @PluginParameter(name = "datasetUrns", label = "Dataset URNs",
        description = "URN of the datasets concerned by this collection configuration.", optional = true)
    List<String> datasetUrns;

    @PluginParameter(name = "license", label = "License",
        description = "Which license this collection is released under.", optional = true)
    String license;

    @PluginParameter(name = "keywords", label = "Keywords",
        description = "Which keywords this collection corresponds to.", optional = true)
    List<String> keywords;

    @PluginParameter(name = "providers", label = "Providers", description = "Define providers for dataset URN.",
        optional = true)
    List<ProviderConfiguration> providers;

}
