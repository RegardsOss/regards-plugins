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

import fr.cnes.regards.framework.hateoas.IResourceService;
import fr.cnes.regards.modules.accessrights.client.IProjectUsersClient;
import fr.cnes.regards.modules.dam.client.dataaccess.IAccessGroupClient;
import fr.cnes.regards.modules.dam.client.entities.IAttachmentClient;
import fr.cnes.regards.modules.dam.client.entities.IDatasetClient;
import fr.cnes.regards.modules.model.client.IAttributeModelClient;
import fr.cnes.regards.modules.model.client.IModelAttrAssocClient;
import fr.cnes.regards.modules.project.client.rest.IProjectsClient;
import fr.cnes.regards.modules.project.domain.Project;
import fr.cnes.regards.modules.storage.client.IStorageRestClient;
import fr.cnes.regards.modules.toponyms.client.IToponymsClient;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;

/**
 * @author sbinda
 */
@Configuration
public class TestConfiguration {

    @Bean
    public IProjectsClient projectsClient() {
        IProjectsClient projectsClientMock = Mockito.mock(IProjectsClient.class);
        // Manage project
        Project project = new Project("Solar system project", "http://plop/icon.png", true, "SolarSystem");
        project.setHost("http://regards/solarsystem");
        ResponseEntity<EntityModel<Project>> response = ResponseEntity.ok(EntityModel.of(project));
        Mockito.when(projectsClientMock.retrieveProject(Mockito.anyString())).thenReturn(response);
        return projectsClientMock;
    }

    @Bean
    public IAttributeModelClient attClient() {
        return Mockito.mock(IAttributeModelClient.class);
    }

    @Bean
    public IModelAttrAssocClient attAssocClient() {
        return Mockito.mock(IModelAttrAssocClient.class);
    }

    @Bean
    public IDatasetClient dataSetClient() {
        return Mockito.mock(IDatasetClient.class);
    }

    @Bean
    public IAccessGroupClient accessGroupClient() {
        return Mockito.mock(IAccessGroupClient.class);
    }

    @Bean
    public IProjectUsersClient projectUsersClient() {
        return Mockito.mock(IProjectUsersClient.class);
    }

    @Bean
    public IResourceService resourceServiceClient() {
        return Mockito.mock(IResourceService.class);
    }

    @Bean
    @Primary
    public IStorageRestClient storageRestClient() {
        return Mockito.mock(IStorageRestClient.class);
    }

    @Bean
    public IToponymsClient toponymsClient() {
        return Mockito.mock(IToponymsClient.class);
    }

    @Bean
    public IAttachmentClient attachmentClient() {
        return Mockito.mock(IAttachmentClient.class);
    }

}
