package fr.cnes.regards.modules.catalog.stac.service;

import fr.cnes.regards.framework.hateoas.IResourceService;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StacServiceTestConfiguration {

    @Bean
    public IResourceService getResourceService() {
        return Mockito.mock(IResourceService.class);
    }
}
