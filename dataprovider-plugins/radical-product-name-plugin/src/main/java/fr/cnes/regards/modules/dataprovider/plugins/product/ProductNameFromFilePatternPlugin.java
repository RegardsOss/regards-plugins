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
package fr.cnes.regards.modules.dataprovider.plugins.product;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.acquisition.plugins.IProductPlugin;

import java.nio.file.Path;

/**
 * Plugin to calculate product name from file by removing some parts of the file name.
 *
 * @author Binda Sébastien
 */
@Plugin(id = "ProductNameFromFilePatternPlugin",
        version = "1.8.0-SNAPSHOT",
        description = "Compute product name from filename pattern by removing some groups from given regex",
        author = "REGARDS Team",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CSSI",
        url = "https://github.com/RegardsOss",
        markdown = "ProductNameFromFilePatternPlugin.md")
public class ProductNameFromFilePatternPlugin implements IProductPlugin {

    public static final String FIELD_PATTERN = "fileNamePattern";

    public static final String FIELD_GROUPS = "groups";

    @PluginParameter(name = FIELD_PATTERN,
                     label = "File name pattern",
                     description = "File name pattern used to remove selected groups to calculate product name.")
    private String pattern;

    @PluginParameter(name = FIELD_GROUPS,
                     label = "Patter groups to remove",
                     description =
                         "Number of groups to remove from file pattern to file name to generate product name. Use ','"
                         + " as separation character between group numbers. Exemple : 1,2,7")
    private String groups;

    @Override
    public String getProductName(Path filePath) throws ModuleException {
        return RegexpHelper.removeGroups(filePath, pattern, groups);
    }
}
