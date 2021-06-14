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
package fr.cnes.regards.modules.catalog.stac.plugin;

import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Only used for STAC collection search configuration
 *
 * @author Marc SORDI
 */
@Plugin(id = StacSearchCollectionEngine.PLUGIN_ID, version = "1.0.0",
        description = "Extend th STAC API for collection search", author = "REGARDS Team",
        contact = "regards@csgroup.eu.fr", license = "GPLv3", owner = "CS GROUP FRANCE",
        url = "https://github.com/RegardsOss", markdown = "StacSearchCollectionEngine.md")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StacSearchCollectionEngine {

    public static final String PLUGIN_ID = "stac-collection-search";

    @PluginParameter(name = "stac-api-root-dynamic-collection-title", label = "STAC root dynamic collection title",
            description = "Displayed label for the dynamic collections root.", defaultValue = "dynamic",
            optional = true)
    private String rootDynamicCollectionTitle;
}
