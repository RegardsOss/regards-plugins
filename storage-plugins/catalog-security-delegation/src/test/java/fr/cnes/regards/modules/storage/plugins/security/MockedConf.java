package fr.cnes.regards.modules.storage.plugins.security;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import fr.cnes.regards.framework.hateoas.IResourceService;
import fr.cnes.regards.framework.notification.client.INotificationClient;
import fr.cnes.regards.modules.accessrights.client.IProjectUsersClient;
import fr.cnes.regards.modules.dam.client.models.IAttributeModelClient;
import fr.cnes.regards.modules.search.client.IAccessRights;

/**
 * @author Sylvain VISSIERE-GUERINET
 */
@Configuration
public class MockedConf {

    @Bean
    public IAttributeModelClient attributeModelClient() {
        return Mockito.mock(IAttributeModelClient.class);
    }

    @Bean
    public IAccessRights accessRights() {
        return Mockito.mock(IAccessRights.class);
    }

    @Bean
    public IProjectUsersClient projectUsersClient() {
        return Mockito.mock(IProjectUsersClient.class);
    }

    @Bean
    public IResourceService mockedResourceService() {
        return Mockito.mock(IResourceService.class);
    }

}
