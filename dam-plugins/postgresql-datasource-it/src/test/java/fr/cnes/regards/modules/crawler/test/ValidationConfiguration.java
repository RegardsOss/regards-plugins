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
package fr.cnes.regards.modules.crawler.test;

import fr.cnes.regards.framework.amqp.*;
import fr.cnes.regards.framework.hateoas.IResourceService;
import fr.cnes.regards.framework.security.autoconfigure.MethodAuthorizationServiceAutoConfiguration;
import fr.cnes.regards.framework.security.autoconfigure.MethodSecurityAutoConfiguration;
import fr.cnes.regards.framework.security.autoconfigure.SecurityVoterAutoConfiguration;
import fr.cnes.regards.framework.security.autoconfigure.WebSecurityAutoConfiguration;
import fr.cnes.regards.modules.storage.client.IStorageRestClient;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.*;

/**
 * @author oroussel
 */
@Profile("ValidationTest")
@Configuration
@ComponentScan(basePackages = { "fr.cnes.regards.modules.crawler.service",
                                "fr.cnes.regards.modules.indexer",
                                "fr.cnes.regards.modules.dam",
                                "fr.cnes.regards.modules.dam.plugins.datasources",
                                "fr.cnes.regards.modules.search",
                                "fr.cnes.regards.framework.modules.plugins.service" })
@EnableAutoConfiguration(exclude = { MethodAuthorizationServiceAutoConfiguration.class,
                                     MethodSecurityAutoConfiguration.class,
                                     SecurityVoterAutoConfiguration.class,
                                     WebSecurityAutoConfiguration.class })
@PropertySource(value = { "classpath:validation.properties", "classpath:validation_${user.name}.properties" },
                ignoreResourceNotFound = true)
public class ValidationConfiguration {

    @Bean
    public IResourceService getResourceService() {
        return Mockito.mock(IResourceService.class);
    }

    @Bean
    public IPoller getPoller() {
        return Mockito.mock(IPoller.class);
    }

    @Bean
    public IPublisher getPublisher() {
        return Mockito.mock(IPublisher.class);
    }

    @Bean
    public ISubscriber getSubscriber() {
        return Mockito.mock(ISubscriber.class);
    }

    @Bean
    public IInstanceSubscriber getInstanceSubscriber() {
        return Mockito.mock(IInstanceSubscriber.class);
    }

    @Bean
    public IInstancePublisher getInstancePublisher() {
        return Mockito.mock(IInstancePublisher.class);
    }

    @Bean
    public IStorageRestClient storageRestClient() {
        return Mockito.mock(IStorageRestClient.class);
    }

}
