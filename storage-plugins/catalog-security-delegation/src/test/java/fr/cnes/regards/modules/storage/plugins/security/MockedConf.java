package fr.cnes.regards.modules.storage.plugins.security;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import fr.cnes.regards.framework.hateoas.IResourceService;
import fr.cnes.regards.modules.accessrights.client.IProjectUsersClient;
import fr.cnes.regards.modules.entities.domain.attribute.IAttribute;
import fr.cnes.regards.modules.models.client.IAttributeModelClient;
import fr.cnes.regards.modules.notification.client.INotificationClient;
import fr.cnes.regards.modules.search.client.ISearchClient;

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
    public ISearchClient searchClient() {
        return Mockito.mock(ISearchClient.class);
    }

    @Bean
    public IProjectUsersClient projectUsersClient() {
        return Mockito.mock(IProjectUsersClient.class);
    }

    @Bean
    public IResourceService mockedResourceService() {
        return Mockito.mock(IResourceService.class);
    }

    @Bean
    public INotificationClient notificationClient() {
        return Mockito.mock(INotificationClient.class);
    }
}
