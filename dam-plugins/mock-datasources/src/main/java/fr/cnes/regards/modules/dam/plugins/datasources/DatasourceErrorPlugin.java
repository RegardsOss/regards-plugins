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
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IInternalDataSourcePlugin;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.model.domain.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * This plugin is used for testing purposes. It mocks a connection to an external datasource<br/>
 * According to its configuration, a {@link DataSourceException} can be thrown from the method {@link #findAll(String, CrawlingCursor, OffsetDateTime)}
 * called during the crawling of data.
 * See fr.cnes.regards.modules.crawler.service.CrawlingService from regards-oss-backend to understand the usage of this plugin.
 */
@Plugin(id = "datasource-error-plugin",
        version = "1.0-SNAPSHOT",
        description = "Mock a datasource and triggers errors",
        author = "REGARDS Team",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CSSI",
        url = "https://github.com/RegardsOss")
public class DatasourceErrorPlugin implements IInternalDataSourcePlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatasourceErrorPlugin.class);

    @PluginParameter(name = "throwDatasourceException",
                     label = "throw DataSourceException",
                     description = "If DataSourceException should be thrown.")
    protected boolean throwDatasourceException;

    @PluginParameter(name = "dataObjectsNum",
                     label = "The number of data objects to generate.",
                     description = "Should be greater than 0.")
    protected int dataObjectsNum;

    @PluginParameter(name = "cursorErrorPos",
                     label = "The position from which a DataSourceException will be thrown.",
                     description =
                         "Should be greater than 0 and inferior to dataObjectsNum/CrawlerPropertiesConfiguration.maxBulkSize. "
                         + "Only active if throwDataSourceException is true.")
    protected int cursorErrorPos;

    @PluginParameter(name = DataSourcePluginConstants.MODEL_NAME_PARAM,
                     label = "Model name",
                     description = "Associated data source model name.")
    protected String modelName;

    @PluginParameter(name = DataSourcePluginConstants.REFRESH_RATE,
                     defaultValue = "10000",
                     optional = true,
                     label = "refresh rate",
                     description = "Ingestion refresh rate in seconds (minimum delay between two consecutive ingestions)")
    private int refreshRate;

    @PluginParameter(name = "entityLastUpdate",
                     label = "The date from which the data objects will be generated.",
                     description = "ISO8631 date. Example: 2022-06-17T14:00:00.248127109Z")
    private String entityLastUpdate;

    @Autowired
    private IRuntimeTenantResolver runtimeTenantResolver;

    private List<DataObject> dataObjects;

    @PluginInit
    public void init() {
        dataObjects = initDataObjects(runtimeTenantResolver.getTenant());
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
    public List<DataObjectFeature> findAll(String tenant, CrawlingCursor cursor, OffsetDateTime from)
        throws DataSourceException {
        LOGGER.info("---------------------------------------------------------");
        LOGGER.info("Init of DatasourceErrorPlugin");
        LOGGER.info("Cursor position number {}", cursor.getPosition());
        LOGGER.info("Initial crawling cursor {}", cursor);

        if (throwDatasourceException && cursor.getPosition() == cursorErrorPos) {
            throw new DataSourceException(String.format(
                "Expected exception thrown with the following cursor configuration %s",
                cursor));
        }

        // 2) Get page requested
        List<DataObject> subListDataObjects = getPage(cursor, dataObjects);

        // 3) Update cursor for next iteration
        // set lastUpdate date
        int lastIndex = subListDataObjects.size() - 1;
        cursor.setCurrentLastEntityDate(subListDataObjects.get(lastIndex).getLastUpdate());

        // compute the total number of pages to process and see if there are still requests to be performed
        int totalNumOfPages = dataObjectsNum == 0 ?
            1 :
            (int) Math.ceil((double) dataObjectsNum / (double) cursor.getSize());
        cursor.setHasNext(cursor.getPosition() + 1 < totalNumOfPages);

        LOGGER.info("Number of elements returned : {}", subListDataObjects.size());
        DataObject lastDataObj = subListDataObjects.get(lastIndex);
        LOGGER.info("Id of last element returned {} with last update date : {}",
                    lastDataObj.getProviderId(),
                    lastDataObj.getLastUpdate());
        LOGGER.info("Modified crawling cursor {}", cursor);

        LOGGER.info("End of DatasourceErrorPlugin");
        LOGGER.info("---------------------------------------------------------");
        return subListDataObjects.stream().map(AbstractEntity::getFeature).toList();
    }

    /**
     * Simulate the retrieval of data during the crawling by creating {@link DataObject}s with a lastUpdateDate = {@link #entityLastUpdate}
     *
     * @param tenant project tenant
     */
    private List<DataObject> initDataObjects(String tenant) {
        OffsetDateTime lastUpdateDate = OffsetDateTime.parse(this.entityLastUpdate);
        List<DataObject> objects = new ArrayList<>();
        for (int i = 0; i < dataObjectsNum; i++) {
            // Create dataObjectFeature
            String id = String.format("datasource-error-item-%d", i);
            String label = String.format("Item %d datasource error", i);

            UniformResourceName urn = UniformResourceName.build(id,
                                                                EntityType.DATA,
                                                                tenant,
                                                                UUID.randomUUID(),
                                                                1,
                                                                null,
                                                                null);
            DataObjectFeature objectFeature = new DataObjectFeature(urn, id, label);
            objectFeature.setModel(modelName);
            objectFeature.setFiles(HashMultimap.create());
            objectFeature.setSessionOwner("sessionOwner-" + i);
            objectFeature.setSession("session-" + i);
            objectFeature.setProperties(Set.of());
            objectFeature.setEntityType(EntityType.DATA);

            // Create dataObject
            DataObject object = new DataObject(Model.build(modelName, "For testing purposes", EntityType.DATA),
                                               objectFeature);
            object.setCreationDate(lastUpdateDate);
            object.setLastUpdate(lastUpdateDate);
            objects.add(object);
        }
        return objects;
    }

    /**
     * Simulate pages of the data object list created in {@link #initDataObjects(String)}
     *
     * @param cursor      the position of page to retrieve
     * @param dataObjects objects to retrieve by page
     * @return a sublist of data objects
     */
    private List<DataObject> getPage(CrawlingCursor cursor, List<DataObject> dataObjects) {
        // in order to get the page requested, simulate the indexes of sublist extracted from the list which contains all data
        int offset = cursor.getPosition() * cursor.getSize();
        int maxSublist = offset + cursor.getSize();

        if (cursor.getLastEntityDate() != null) {
            OffsetDateTime previousEntityDate = cursor.getLastEntityDate();
            LOGGER.info("Searching dataObjects from lastUpdateDate {} and crawling position {}",
                        previousEntityDate,
                        cursor.getPosition());
            dataObjects = dataObjects.stream()
                                     .filter(o -> o.getLastUpdate().isAfter(previousEntityDate) || o.getLastUpdate()
                                                                                                    .isEqual(
                                                                                                        previousEntityDate))
                                     .toList();
        }
        return dataObjects.subList(offset, Math.min(maxSublist, dataObjects.size()));
    }
}
