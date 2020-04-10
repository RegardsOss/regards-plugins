/*
 * Copyright 2017-2018 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.fem.plugins.service;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import fr.cnes.regards.modules.model.client.IAttributeModelClient;
import fr.cnes.regards.modules.model.client.IModelAttrAssocClient;
import fr.cnes.regards.modules.model.gson.IAttributeHelper;
import fr.cnes.regards.modules.model.service.xml.IComputationPluginService;

/**
 * @author Kevin Marchois
 *
 */
@Configuration
@PropertySource({ "classpath:geode-test.properties" })
public class FeatureTestConfiguration {

    @Bean
    public IAttributeModelClient attributeModelClient() {
        return Mockito.mock(IAttributeModelClient.class);
    }

    @Bean
    public IModelAttrAssocClient modelAttrAssocClient() {
        return Mockito.mock(IModelAttrAssocClient.class);
    }

    @Bean
    public IAttributeHelper attributeHelper() {
        return Mockito.mock(IAttributeHelper.class);
    }

    @Bean // Used in model service
    public IComputationPluginService computationPluginService() {
        return Mockito.mock(IComputationPluginService.class);
    }

}
