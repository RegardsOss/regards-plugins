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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import fr.cnes.regards.framework.jpa.multitenant.transactional.MultitenantTransactional;
import fr.cnes.regards.framework.test.integration.RequestBuilderCustomizer;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.StacApiConstants;
import fr.cnes.regards.modules.catalog.stac.service.collection.dyncoll.DynamicCollectionService;

/**
 * Cross layer integration test : from RESTful API to Elasticsearch index
 *
 * @author Marc SORDI
 *
 */
@TestPropertySource(locations = { "classpath:test.properties" },
        properties = { "regards.tenant=swot", "spring.jpa.properties.hibernate.default_schema=swot" })
@MultitenantTransactional
public class SwotEngineControllerIT extends AbstractSwotIT {

    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(SwotEngineControllerIT.class);

    @Autowired
    private DynamicCollectionService dynCollService;

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
                          "Cannot reach STAC conformance page", "static");
    }

    @Test
    public void getDynamicCollections() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // TODO get JSON result and make assertion on expected collection links
        performDefaultGet(StacApiConstants.STAC_COLLECTIONS_PATH + StacApiConstants.STAC_COLLECTION_PATH_SUFFIX, customizer,
                          "Cannot reach STAC conformance page", "dynamic");
    }

    @Test
    public void getDynamicCollectionFirstLevelItems() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // L2_HR_RASTER URN
        String urn = "URN:DYNCOLL:eyJscyI6W3sicCI6Imh5ZHJvOmRhdGFfdHlwZSIsInYiOiJMMl9IUl9SQVNURVIifV19";
        performDefaultGet(StacApiConstants.STAC_COLLECTIONS_PATH + StacApiConstants.STAC_ITEMS_PATH_SUFFIX, customizer,
                          "Cannot reach STAC conformance page", urn);
    }
}
