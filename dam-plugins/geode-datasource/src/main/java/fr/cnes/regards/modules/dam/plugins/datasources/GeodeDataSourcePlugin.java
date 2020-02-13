/*
 * Copyright 2017-2019 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.dam.plugins.datasources;

import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IGeodeDataSourcePlugin;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.feature.service.IDataObjectFeatureService;

/**
 * Plugin to get data from feature manager
 *
 * @author Kevin Marchois
 */
@Plugin(id = "gedoe-datasource", version = "1.0-SNAPSHOT", description = "Plugin to get data from feature manager",
        author = "REGARDS Team", contact = "regards@c-s.fr", license = "GPLv3", owner = "CSSI",
        url = "https://github.com/RegardsOss")
public class GeodeDataSourcePlugin implements IGeodeDataSourcePlugin {

    @Value("${geode.plugin.refreshRate:1000}")
    private int refreshRate;

    @PluginParameter(name = DataSourcePluginConstants.MODEL_NAME_PARAM, label = "model name",
            description = "Associated data source model name")
    protected String modelName;

    @Autowired
    private IDataObjectFeatureService dataObjectFactory;

    @Override
    public int getRefreshRate() {
        return refreshRate;
    }

    @Override
    public Page<DataObjectFeature> findAll(String model, Pageable pageable, OffsetDateTime date)
            throws DataSourceException {
        return dataObjectFactory.findAll(model, pageable, date);
    }

    @Override
    public String getModelName() {
        return modelName;
    }

}
