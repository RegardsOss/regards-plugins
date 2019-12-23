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
package fr.cnes.regards.modules.crawler.test;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;

import fr.cnes.regards.framework.hateoas.IResourceService;
import fr.cnes.regards.modules.accessrights.client.IProjectUsersClient;
import fr.cnes.regards.modules.dam.service.dataaccess.IAccessRightService;
import fr.cnes.regards.modules.model.client.IAttributeModelClient;
import fr.cnes.regards.modules.model.service.IModelAttrAssocService;
import fr.cnes.regards.modules.opensearch.service.IOpenSearchService;
import fr.cnes.regards.modules.project.client.rest.IProjectsClient;
import fr.cnes.regards.modules.project.domain.Project;

@Profile("CrawlerTest")
@Configuration
@PropertySource(value = { "classpath:test.properties", "classpath:test_${user.name}.properties" },
        ignoreResourceNotFound = true)
public class CrawlerConfiguration {

    @Bean
    public IAttributeModelClient attributeModelClient() {
        return Mockito.mock(IAttributeModelClient.class);
    }

    @Bean(name = "osService")
    @Primary
    public IOpenSearchService openSearchService() {
        return Mockito.mock(IOpenSearchService.class);
    }

    @Bean
    public IAccessRightService getAccessRightsService() {
        return Mockito.mock(IAccessRightService.class);
    }

    @Bean
    public IProjectsClient projectsClient() {
        IProjectsClient projectClient = Mockito.mock(IProjectsClient.class);
        Mockito.when(projectClient.retrieveProject(Mockito.anyString())).thenAnswer(invocation -> {
            String tenant = invocation.getArgument(0);
            Project project = new Project();
            project.setName(tenant);
            project.setCrs("WGS_84");
            project.setPoleToBeManaged(Boolean.FALSE);
            return ResponseEntity.ok(new EntityModel<Project>(project));
        });
        return projectClient;
    }

    @Bean
    public IProjectUsersClient projectUsersClient() {
        return Mockito.mock(IProjectUsersClient.class);
    }

    @Bean(name = "rService")
    public IResourceService getResourceService() {
        return Mockito.mock(IResourceService.class);
    }

    @Bean
    @Primary
    public IModelAttrAssocService modelAttrAssocService() {
        return Mockito.mock(IModelAttrAssocService.class);
    }

}
