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
import fr.cnes.regards.modules.catalog.stac.domain.api.Fields;
import fr.cnes.regards.modules.catalog.stac.domain.api.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.SearchBody;
import fr.cnes.regards.modules.catalog.stac.plugin.it.AbstractStacIT;
import fr.cnes.regards.modules.catalog.stac.rest.utils.StacApiConstants;
import fr.cnes.regards.modules.search.domain.plugin.SearchEngineConfiguration;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.List;

/**
 * Cross layer integration test : from REST API to Elasticsearch index
 *
 * @author Marc SORDI
 */
@TestPropertySource(locations = { "classpath:test.properties" },
                    properties = { "regards.tenant=fields",
                                   "spring.jpa.properties.hibernate" + ".default_schema=fields" })
public class FieldExtensionIT extends AbstractStacIT {

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

    @Test
    public void searchItemsAsPost() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        ItemSearchBody body = ItemSearchBody.builder().limit(1).build();
        performDefaultPost(StacApiConstants.STAC_SEARCH_PATH, body, customizer, "Cannot search STAC items");
    }

    /**
     * Basic ITEM search with include fields
     */
    @Test
    public void searchItemsAsPostWithIncludeFields() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectToHaveSize("features", 1);
        customizer.expectDoesNotExist("$.features[0].type");
        customizer.expectIsNotEmpty("$.features[0].id");
        customizer.expectValue("$.features[0].properties.hydro:data_type", "L2_HR_RASTER_100m");

        search(customizer, new Fields(List.of("id", "properties.hydro:data_type", "properties.dcs:data_format"), null));
    }

    /**
     * Basic ITEM search with exclude fields
     */
    @Test
    public void searchItemsAsPostWithExcludeFields() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectToHaveSize("features", 1);
        customizer.expectIsNotEmpty("$.features[0].id");
        customizer.expectDoesNotExist("$.features[0].properties.hydro:data_type");
        customizer.expectDoesNotExist("$.features[0].assets");
        customizer.expectDoesNotExist("$.features[0].links");

        // Exclude data type, assets and links
        search(customizer, new Fields(null, List.of("properties.hydro:data_type", "assets", "links")));
    }

    @Test
    public void searchItemsAsPostWithIncludeFieldsAndExcludeFields() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectStatusOk();
        customizer.expectToHaveSize("features", 1);
        customizer.expectDoesNotExist("$.features[0].properties.hydro:variables");

        // Exclude data type, assets and links
        search(customizer, new Fields(List.of("properties"), List.of("properties.hydro:variables")));
    }

    /**
     * Search first item of data type L2_HR_RASTER_100m
     *
     * @param customizer customizer to set assertions
     * @param fields     fields to include or exclude
     */
    private void search(RequestBuilderCustomizer customizer, Fields fields) {
        Map<String, SearchBody.QueryObject> iq = HashMap.of("hydro:data_type",
                                                            SearchBody.StringQueryObject.builder()
                                                                                        .eq("L2_HR_RASTER_100m")
                                                                                        .build());
        ItemSearchBody body = ItemSearchBody.builder().query(iq).limit(1).fields(fields).build();
        performDefaultPost(StacApiConstants.STAC_SEARCH_PATH, body, customizer, "Cannot search STAC items");
    }
}
