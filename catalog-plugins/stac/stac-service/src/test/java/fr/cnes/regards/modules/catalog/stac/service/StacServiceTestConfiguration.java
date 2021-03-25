package fr.cnes.regards.modules.catalog.stac.service;

import fr.cnes.regards.framework.authentication.IAuthenticationResolver;
import fr.cnes.regards.framework.hateoas.IResourceService;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.security.utils.jwt.JWTAuthentication;
import fr.cnes.regards.modules.catalog.stac.domain.properties.RegardsPropertyAccessor;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.AbstractPropertyConverter;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Provider;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import io.vavr.collection.List;
import org.locationtech.spatial4j.io.GeoJSONReader;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.MalformedURLException;
import java.net.URL;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Configuration
public class StacServiceTestConfiguration {

    @Bean
    public IResourceService getResourceService() {
        return Mockito.mock(IResourceService.class);
    }

    @Bean
    public ConfigurationAccessorFactory getConfigurationAccessorFactory() throws MalformedURLException {
        ConfigurationAccessorFactory mockConfigurationAccessorFactory = Mockito.mock(ConfigurationAccessorFactory.class);
        ConfigurationAccessor mockConfigurationAccessor = Mockito.mock(ConfigurationAccessor.class);

        when(mockConfigurationAccessor.getLicense(anyString())).thenReturn("licence");
        when(mockConfigurationAccessor.getDescription()).thenReturn("description");
        when(mockConfigurationAccessor.getProviders(anyString())).thenReturn(List.of(
                new Provider("provider","providerDes",new URL("http","stac",80,"file"),List.of(Provider.ProviderRole.HOST))));
        when(mockConfigurationAccessor.getKeywords(anyString())).thenReturn(List.of("licence"));
        when(mockConfigurationAccessor.getStacProperties()).thenReturn(List.of(
                new StacProperty(
                        new RegardsPropertyAccessor("attrName",new AttributeModel(), null, null),
                        "propName","ext",false,0, "format",
                        StacPropertyType.NUMBER, Mockito.mock(AbstractPropertyConverter.class))));
        when(mockConfigurationAccessor.getGeoJSONReader()).thenReturn(mock(GeoJSONReader.class));

        when(mockConfigurationAccessorFactory.makeConfigurationAccessor()).thenReturn(mockConfigurationAccessor);
        return mockConfigurationAccessorFactory;
    }

    @Bean
    public IAuthenticationResolver stacServiceAuthenticationResolver(){
        return new StacServiceAuthenticationResolver();
    }

    private static class StacServiceAuthenticationResolver implements IAuthenticationResolver {

        @Override
        public String getUser() {
            return "regards-admin@c-s.fr";
        }

        @Override
        public String getRole() {
                return "ADMIN";
        }

        @Override
        public String getToken() {
            return null;
        }
    }
}
