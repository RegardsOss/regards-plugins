/*
 * Copyright 2017-2025 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.stac.plugin.it.swot;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.test.integration.RequestBuilderCustomizer;
import fr.cnes.regards.modules.catalog.stac.plugin.it.AbstractStacIT;
import fr.cnes.regards.modules.catalog.stac.rest.utils.StacApiConstants;
import fr.cnes.regards.modules.search.domain.plugin.SearchEngineConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;

/**
 * Cross layer integration test : from REST API to Elasticsearch index
 *
 * @author Marc SORDI
 */
@TestPropertySource(locations = { "classpath:test.properties" },
                    properties = { "regards.tenant=ogc_feature",
                                   "spring.jpa.properties.hibernate" + ".default_schema=ogc_feature" })
public class OGCFeatureControllerIT extends AbstractStacIT {

    @Before
    @Override
    public void prepareData() throws ModuleException, InterruptedException, IOException {
        cleanDatabase();
        super.prepareData();
    }

    @Override
    protected String getDataFolderName() {
        return "swot_v2";
    }

    @Override
    protected String getDataModel() {
        return "001_data_model_hygor_V2.xml";
    }

    @Override
    protected String getDatasetModel() {
        return "002_dataset_model_hygor_V2.xml";
    }

    @Override
    protected void initPlugins() throws ModuleException {
        SearchEngineConfiguration conf = loadFromJson(getConfigFolder().resolve("STAC-api-configuration-v2.json"),
                                                      SearchEngineConfiguration.class);
        searchEngineService.createConf(conf);

        SearchEngineConfiguration collectionConf = loadFromJson(getConfigFolder().resolve(
            "STAC-collection-configuration-v2.json"), SearchEngineConfiguration.class);
        searchEngineService.createConf(collectionConf);
    }

    /**
     * Get all collections
     */
    @Test
    public void getCollectionsTest() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        performDefaultGet(StacApiConstants.STAC_COLLECTIONS_PATH, customizer, "Cannot get STAC collections");
    }

    @Test
    public void getCollectionItems() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        performDefaultGet(StacApiConstants.STAC_COLLECTIONS_PATH + "/SWOT_L2_HR_Raster_100m/items",
                          customizer,
                          "Cannot get STAC collection items");
    }
}
