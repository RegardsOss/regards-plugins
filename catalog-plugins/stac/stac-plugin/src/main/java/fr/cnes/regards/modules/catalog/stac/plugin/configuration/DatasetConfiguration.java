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
 * Allows to configure the main catalog collection configuration.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DatasetConfiguration {

    @PluginParameter(label = "STAC collection title", optional = true)
    private StacSourcePropertyConfiguration stacCollectionTitle;

    @PluginParameter(label = "STAC collection description")
    private StacSourcePropertyConfiguration stacCollectionDescription;

    @PluginParameter(label = "STAC collection keywords", optional = true)
    private StacSourcePropertyConfiguration stacCollectionKeywords;

    @PluginParameter(label = "STAC collection license")
    private StacSourcePropertyConfiguration stacCollectionLicense;

    @PluginParameter(label = "STAC collection providers", optional = true)
    private StacSourcePropertyConfiguration stacCollectionProviders;

    // TODO extent ... mapping or computed properties : mapping plus rapide! mais inexact en recherche!
    // TODO summaries ... mapping only?

}
