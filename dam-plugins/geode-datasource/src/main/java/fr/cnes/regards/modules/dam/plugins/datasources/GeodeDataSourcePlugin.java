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
import java.util.Collection;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;

import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IDataSourcePlugin;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.feature.client.IFeatureEntityClient;
import fr.cnes.regards.modules.feature.dto.FeatureEntityDto;

/**
 * Plugin to get data from feature manager
 *
 * @author Kevin Marchois
 */
@Plugin(id = "geode-datasource", version = "1.0-SNAPSHOT", description = "Plugin to get data from feature manager",
        author = "REGARDS Team", contact = "regards@c-s.fr", license = "GPLv3", owner = "CSSI",
        url = "https://github.com/RegardsOss")
public class GeodeDataSourcePlugin implements IDataSourcePlugin {

    @Value("${geode.plugin.refreshRate:1000}")
    private int refreshRate;

    @PluginParameter(name = DataSourcePluginConstants.MODEL_NAME_PARAM, label = "model name",
            description = "Associated data source model name")
    protected String modelName;

    @Autowired
    private IFeatureEntityClient dataObjectClient;

    @Override
    public int getRefreshRate() {
        return refreshRate;
    }

    @Override
    public Page<DataObjectFeature> findAll(String model, Pageable pageable, OffsetDateTime date)
            throws DataSourceException {
        PagedModel<EntityModel<FeatureEntityDto>> page = dataObjectClient
                .findAll(model, date, pageable.getPageNumber(), pageable.getPageSize()).getBody();
        Collection<EntityModel<FeatureEntityDto>> dtos = page.getContent();
        return new PageImpl<DataObjectFeature>(
                dtos.stream().map(entity -> initDataObjectFeature(entity)).collect(Collectors.toList()),
                PageRequest.of(new Long(page.getMetadata().getNumber()).intValue(),
                               new Long(page.getMetadata().getSize()).intValue()),
                page.getMetadata().getTotalElements());
    }

    private DataObjectFeature initDataObjectFeature(EntityModel<FeatureEntityDto> entity) {
        return new DataObjectFeature(entity.getContent().getFeature().getUrn(),
                entity.getContent().getFeature().getId(), "NO LABEL", entity.getContent().getSessionOwner(),
                entity.getContent().getSession(), entity.getContent().getFeature().getModel());
    }

    @Override
    public String getModelName() {
        return modelName;
    }

}
