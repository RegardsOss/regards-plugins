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
package fr.cnes.regards.modules.catalog.femdriver.rest;

import com.google.common.collect.Sets;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.test.integration.RequestBuilderCustomizer;
import fr.cnes.regards.modules.catalog.femdriver.dto.FeatureUpdateRequest;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import fr.cnes.regards.modules.search.domain.SearchRequest;
import fr.cnes.regards.modules.search.domain.plugin.SearchEngineMappings;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.util.LinkedMultiValueMap;

import java.util.List;
import java.util.Set;

/**
 * @author sbinda
 */
@TestPropertySource(locations = { "classpath:test.properties" },
                    properties = { "spring.jpa.properties.hibernate.default_schema=femdriver_rest_it" })
public class FemDriverControllerIT extends AbstractFemTest {

    @Before
    public void init() throws ModuleException {
        this.doInit();
    }

    @Test
    public void update() {
        List<AttributeModel> atts = attributeModelService.getAttributes(null, null, null);
        gsonAttributeFactory.refresh(getDefaultTenant(), atts);
        Set<IProperty<?>> properties = Sets.newHashSet();
        properties.add(IProperty.buildString("name", "plop"));
        SearchRequest searchRequest = new SearchRequest(SearchEngineMappings.LEGACY_PLUGIN_ID,
                                                        null,
                                                        new LinkedMultiValueMap<String, String>(),
                                                        null,
                                                        null,
                                                        null);
        FeatureUpdateRequest req = FeatureUpdateRequest.build(searchRequest, properties);

        RequestBuilderCustomizer customizer = customizer();
        customizer.expect(MockMvcResultMatchers.status().isNoContent());
        performDefaultPost(FemDriverController.FEM_DRIVER_PATH + FemDriverController.FEM_DRIVER_UPDATE_PATH,
                           req,
                           customizer,
                           "Failed to create a new dataset");

    }

}
