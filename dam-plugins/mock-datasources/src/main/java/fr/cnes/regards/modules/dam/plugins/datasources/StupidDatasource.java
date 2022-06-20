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
package fr.cnes.regards.modules.dam.plugins.datasources;

import com.google.common.collect.HashMultimap;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginInit;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.dam.domain.datasources.CrawlingCursor;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IInternalDataSourcePlugin;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.*;

@Plugin(id = "sutpid-datasource-plugin", version = "1.0-SNAPSHOT",
    description = "Mock a datasource and always return 3 results at a time for a total of 20 results",
    author = "REGARDS Team", contact = "regards@c-s.fr", license = "GPLv3", owner = "CSSI",
    url = "https://github.com/RegardsOss")
public class StupidDatasource implements IInternalDataSourcePlugin {

    private static final int NUMBER_ELEMENT = 20;

    private static final int STEP = 3;

    @PluginParameter(name = DataSourcePluginConstants.MODEL_NAME_PARAM, label = "Model name",
        description = "Associated data source model name.")
    protected String modelName;

    @PluginParameter(name = DataSourcePluginConstants.REFRESH_RATE, defaultValue = "10000", optional = true,
        label = "refresh rate",
        description = "Ingestion refresh rate in seconds (minimum delay between two consecutive ingestions)")
    private int refreshRate;

    @Autowired
    private IRuntimeTenantResolver runtimeTenantResolver;

    private List<DataObjectFeature> objects;

    @PluginInit
    public void initData() {
        objects = initDataObjects();
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public int getRefreshRate() {
        return refreshRate;
    }

    @Override
    public List<DataObjectFeature> findAll(String tenant, CrawlingCursor cursor, OffsetDateTime from) {
        int position = cursor.getPosition();
        if (position < NUMBER_ELEMENT / STEP) {
            cursor.setHasNext(true);
            return objects.subList(position * STEP, ++position * STEP);
        } else if (cursor.getPosition() == NUMBER_ELEMENT / STEP) {
            cursor.setHasNext(false);
            return objects.subList(position * STEP, objects.size());
        } else {
            cursor.setHasNext(false);
            return Collections.emptyList();
        }
    }

    private List<DataObjectFeature> initDataObjects() {
        List<DataObjectFeature> dataObjects = new ArrayList<>();
        for (int i = 0; i < NUMBER_ELEMENT; i++) {
            // Create dataObjectFeature
            String id = String.format("stupid-datasource-item-%d", i);
            String label = String.format("Item %d from stupid datasource", i);

            UniformResourceName urn = UniformResourceName.build(id,
                                                                EntityType.DATA,
                                                                runtimeTenantResolver.getTenant(),
                                                                UUID.randomUUID(),
                                                                1,
                                                                null,
                                                                null);
            DataObjectFeature dataObjectFeature = new DataObjectFeature(urn, id, label);
            dataObjectFeature.setModel(modelName);
            dataObjectFeature.setFiles(HashMultimap.create());
            dataObjectFeature.setSessionOwner("sessionOwner-stupid");
            dataObjectFeature.setSession("session-stupid-1");
            dataObjectFeature.setProperties(new HashSet<>());
            dataObjectFeature.setEntityType(EntityType.DATA);

            dataObjects.add(dataObjectFeature);
        }
        return dataObjects;
    }
}
