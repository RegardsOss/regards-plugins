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
package fr.cnes.regards.modules.catalog.stac.plugin.it.swot;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestPropertySource;

import fr.cnes.regards.framework.jpa.multitenant.transactional.MultitenantTransactional;
import fr.cnes.regards.framework.test.integration.RequestBuilderCustomizer;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.StacApiConstants;
import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;
import fr.cnes.regards.modules.search.domain.plugin.SearchEngineMappings;

/**
 * Cross layer integration test : from RESTful API to Elasticsearch index
 *
 * @author Marc SORDI
 *
 */
@TestPropertySource(locations = { "classpath:test-local.properties" },
        properties = { "regards.tenant=swot", "spring.jpa.properties.hibernate.default_schema=swot" })
@MultitenantTransactional
public class SwotEngineControllerIT extends AbstractSwotIT {

    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(SwotEngineControllerIT.class);

    @Test
    public void getLandingPage() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        performDefaultGet(StacApiConstants.STAC_PATH, customizer, "Cannot reach STAC landing page");
    }

    @Test
    public void getConformancePage() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        performDefaultGet(StacApiConstants.STAC_CONFORMANCE_PATH, customizer, "Cannot reach STAC conformance page");
    }

    @Test
    public void getStaticCollections() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        performDefaultGet(StacApiConstants.STAC_COLLECTIONS_PATH + StacApiConstants.STAC_COLLECTION_PATH_SUFFIX, customizer,
                          "Cannot reach STAC static collections", "static");
    }

    @Test
    public void getDynamicCollections() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // TODO get JSON result and make assertion on expected collection links
        performDefaultGet(StacApiConstants.STAC_COLLECTIONS_PATH + StacApiConstants.STAC_COLLECTION_PATH_SUFFIX, customizer,
                          "Cannot reach STAC dynamic collections", "dynamic");
    }

    @Test
    public void getDynamicCollectionFirstLevelItems() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // L2_HR_RASTER URN
        String urn = "URN:DYNCOLL:eyJscyI6W3sicCI6Imh5ZHJvOmRhdGFfdHlwZSIsInYiOiJMMl9IUl9SQVNURVIifV19";
        performDefaultGet(StacApiConstants.STAC_COLLECTIONS_PATH + StacApiConstants.STAC_ITEMS_PATH_SUFFIX, customizer,
                          "Cannot reach STAC collection items", urn);
    }

    @Test
    public void searchItems() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.addParameter("datetime", "2022-01-01T00:00:00Z/2022-07-01T00:00:00Z");
        performDefaultGet(StacApiConstants.STAC_SEARCH_PATH, customizer, "Cannot search STAC items");
    }

    @Test
    public void searchDataobjectsReturningDatasets() {

        // Search dataset
        String engine_type = "legacy";
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        addSearchTermQuery(customizer, "datetime", "[2020-01-01T00:00:00 TO 2020-06-01T00:00:00]");
        performDefaultGet(SearchEngineMappings.TYPE_MAPPING + SearchEngineMappings.SEARCH_DATAOBJECTS_DATASETS_MAPPING,
                          customizer, "Search all error", engine_type);

        //        addCommontMatchers(customizer);
        //        customizer.expect(MockMvcResultMatchers.jsonPath("$.content.length()", Matchers.equalTo(1)));
        //        addSearchTermQuery(customizer, STAR_SYSTEM, protect(SOLAR_SYSTEM));
        //        ResultActions result = performDefaultGet(SearchEngineMappings.TYPE_MAPPING
        //                + SearchEngineMappings.SEARCH_DATASETS_MAPPING, customizer, "Search all error", ENGINE_TYPE);
        //
        //        String datasetUrn = JsonPath.read(payload(result), "$.content[0].content.id");
        //
        //        customizer = customizer().expectStatusOk();
        //        addCommontMatchers(customizer);
        //        customizer.expect(MockMvcResultMatchers.jsonPath("$.links.length()", Matchers.equalTo(1)));
        //        customizer.expect(MockMvcResultMatchers.jsonPath("$.content[0].links.length()", Matchers.equalTo(1)));
        //        performDefaultGet(SearchEngineMappings.TYPE_MAPPING + SearchEngineMappings.SEARCH_DATASET_DATAOBJECTS_MAPPING,
        //                          customizer, "Search all error", ENGINE_TYPE, datasetUrn);
    }

    /**
     * Add query to current request
     * @param customizer current {@link RequestBuilderCustomizer}
     * @param relativePropertyName name without properties prefix
     * @param value the property value
     */
    private void addSearchTermQuery(RequestBuilderCustomizer customizer, String relativePropertyName, String value) {
        customizer.addParameter("q", StaticProperties.FEATURE_PROPERTIES + "." + relativePropertyName + ":" + value);
    }
}
