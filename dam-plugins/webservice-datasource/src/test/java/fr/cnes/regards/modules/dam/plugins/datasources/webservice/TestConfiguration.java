package fr.cnes.regards.modules.dam.plugins.datasources.webservice;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import fr.cnes.regards.modules.accessrights.client.IProjectUsersClient;
import fr.cnes.regards.modules.model.client.IAttributeModelClient;
import fr.cnes.regards.modules.project.client.rest.IProjectsClient;

@Configuration
public class TestConfiguration {

    @Bean
    public IProjectUsersClient userClientMock() {
        return Mockito.mock(IProjectUsersClient.class);
    }

    @Bean
    public IAttributeModelClient modelClient() {
        return Mockito.mock(IAttributeModelClient.class);
    }

    @Bean
    public IProjectsClient projectsClientMock() {
        return Mockito.mock(IProjectsClient.class);
    }
}
