package fr.cnes.regards.modules.catalog.stac.service;

import fr.cnes.regards.framework.authentication.IAuthenticationResolver;
import fr.cnes.regards.framework.hateoas.IResourceService;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacCollectionProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Provider;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.configuration.collection.CollectionConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.collection.CollectionConfigurationAccessorFactory;
import io.vavr.collection.List;
import org.locationtech.spatial4j.io.GeoJSONReader;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.OffsetDateTime;

import static fr.cnes.regards.modules.catalog.stac.domain.properties.RegardsPropertyAccessor.accessor;
import static fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType.DATETIME;
import static fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType.NUMBER;
import static fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.AbstractPropertyConverter.idConverter;
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
        when(mockConfigurationAccessor.getProviders(anyString())).thenReturn(List.of(new Provider("provider",
                                                                                                  "providerDes",
                                                                                                  new URL("http",
                                                                                                          "stac",
                                                                                                          80,
                                                                                                          "file"),
                                                                                                  List.of(Provider.ProviderRole.HOST))));
        when(mockConfigurationAccessor.getKeywords(anyString())).thenReturn(List.of("licence"));

        StacProperty datetimeProp = new StacProperty(accessor("creationDate", DATETIME, OffsetDateTime.now(), true),
                                                     null,
                                                     "datetime",
                                                     "",
                                                     false,
                                                     -1,
                                                     "",
                                                     DATETIME,
                                                     idConverter(DATETIME),
                                                     Boolean.FALSE);

        when(mockConfigurationAccessor.getDatetimeStacProperty()).thenReturn(datetimeProp);
        when(mockConfigurationAccessor.getStacProperties()).thenReturn(List.of(datetimeProp,
                                                                               new StacProperty(accessor("attrName",
                                                                                                         NUMBER,
                                                                                                         42),
                                                                                                null,
                                                                                                "propName",
                                                                                                "ext",
                                                                                                false,
                                                                                                0,
                                                                                                "format",
                                                                                                NUMBER,
                                                                                                idConverter(NUMBER),
                                                                                                Boolean.FALSE)));
        when(mockConfigurationAccessor.getRootStaticCollectionName()).thenReturn("staticCollectionRoot");

        when(mockConfigurationAccessor.getGeoJSONReader()).thenReturn(mock(GeoJSONReader.class));

        when(mockConfigurationAccessorFactory.makeConfigurationAccessor()).thenReturn(mockConfigurationAccessor);
        return mockConfigurationAccessorFactory;
    }

    @Bean
    CollectionConfigurationAccessorFactory collectionConfigurationAccessorFactory() {
        return new CollectionConfigurationAccessorFactory() {

            @Override
            public CollectionConfigurationAccessor makeConfigurationAccessor() {
                return new CollectionConfigurationAccessor() {

                    @Override
                    public StacCollectionProperty getTitleProperty() {
                        return null;
                    }

                    @Override
                    public StacCollectionProperty getDescriptionProperty() {
                        return null;
                    }

                    @Override
                    public StacCollectionProperty getKeywordsProperty() {
                        return null;
                    }

                    @Override
                    public StacCollectionProperty getLicenseProperty() {
                        return null;
                    }

                    @Override
                    public StacCollectionProperty getProvidersProperty() {
                        return null;
                    }

                    @Override
                    public StacCollectionProperty getLinksProperty() {
                        return null;
                    }

                    @Override
                    public StacCollectionProperty getAssetsProperty() {
                        return null;
                    }

                    @Override
                    public StacCollectionProperty getLowerTemporalExtentProperty() {
                        return null;
                    }

                    @Override
                    public StacCollectionProperty getUpperTemporalExtentProperty() {
                        return null;
                    }

                    @Override
                    public List<StacProperty> getSummariesProperties() {
                        return null;
                    }

                    @Override
                    public List<StacProperty> getStacProperties() {
                        return null;
                    }
                };
            }
        };
    }

    @Bean
    public IAuthenticationResolver stacServiceAuthenticationResolver() {
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
