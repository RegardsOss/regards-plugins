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
package fr.cnes.regards.db.datasources.plugins.common;

import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IDataSourcePlugin;

/**
 * @author oroussel
 */
public abstract class AbstractDataSourcePlugin implements IDataSourcePlugin {

    @PluginParameter(name = DataSourcePluginConstants.MODEL_NAME_PARAM,
                     label = "model name",
                     description = "Associated data source model name")
    protected String modelName;

    @PluginParameter(name = DataSourcePluginConstants.COLUMN_ID,
                     label = "column id",
                     optional = true,
                     description = "the column name which contains ids")
    protected String columnId;

    @PluginParameter(name = DataSourcePluginConstants.UNIQUE_ID,
                     label = "unique id",
                     defaultValue = "false",
                     optional = true,
                     description = "indicate if ids of column id are unique or not")
    protected Boolean uniqueId;

    @Override
    public String getModelName() {
        return modelName;
    }

}
