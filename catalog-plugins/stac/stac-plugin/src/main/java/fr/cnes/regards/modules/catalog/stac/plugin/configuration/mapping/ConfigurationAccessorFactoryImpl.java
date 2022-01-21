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

package fr.cnes.regards.modules.catalog.stac.plugin.configuration.mapping;

import fr.cnes.regards.framework.modules.plugins.service.IPluginService;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.AbstractPropertyConverter;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.PropertyConverterFactory;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Provider;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Provider.ProviderRole;
import fr.cnes.regards.modules.catalog.stac.plugin.StacSearchEngine;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.CollectionConfiguration;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.ProviderConfiguration;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.StacPropertyConfiguration;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.StacSourcePropertyConfiguration;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.indexer.dao.spatial.ProjectGeoSettings;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.locationtech.spatial4j.context.jts.JtsSpatialContext;
import org.locationtech.spatial4j.context.jts.JtsSpatialContextFactory;
import org.locationtech.spatial4j.io.GeoJSONReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ArrayList;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.debug;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.warn;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;

/**
 * Allows to transform property configuration to domain properties, and access
 * configuration in its "domain" form.
 */
@Component
public class ConfigurationAccessorFactoryImpl extends AbstractConfigurationAccessor
        implements ConfigurationAccessorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationAccessorFactoryImpl.class);

    private final PropertyConverterFactory propertyConverterFactory;

    private final IRuntimeTenantResolver runtimeTenantResolver;

    private final ProjectGeoSettings geoSettings;

    @Autowired
    public ConfigurationAccessorFactoryImpl(PropertyConverterFactory propertyConverterFactory,
            IPluginService pluginService, RegardsPropertyAccessorFactory regardsPropertyAccessorFactory,
            IRuntimeTenantResolver runtimeTenantResolver, ProjectGeoSettings geoSettings) {
        super(pluginService, regardsPropertyAccessorFactory);
        this.propertyConverterFactory = propertyConverterFactory;
        this.runtimeTenantResolver = runtimeTenantResolver;
        this.geoSettings = geoSettings;
    }

    @Override
    public ConfigurationAccessor makeConfigurationAccessor() {
        final Try<StacSearchEngine> plugin = getPlugin(StacSearchEngine.PLUGIN_ID);
        return new ConfigurationAccessor() {

            @Override
            public String getTitle() {
                return plugin.map(StacSearchEngine::getStacTitle).getOrElse(() -> {
                    String tenant = runtimeTenantResolver.getTenant();
                    return "STAC Catalog " + tenant;
                });
            }

            @Override
            public String getDescription() {
                return plugin.map(StacSearchEngine::getStacDescription).getOrElse(() -> {
                    String tenant = runtimeTenantResolver.getTenant();
                    return "STAC Catalog " + tenant;
                });
            }

            @Override
            public boolean hasStacConfiguration() {
                return plugin.isSuccess();
            }

            @Override
            public List<StacProperty> getStacProperties() {
                return plugin.map(ConfigurationAccessorFactoryImpl.this::getConfiguredProperties)
                        .getOrElse(List.empty());
            }

            @Override
            public StacProperty getDatetimeStacProperty() {
                return plugin.map(p -> p.getStacDatetimeProperty().toStacPropertyConfiguration())
                        .map(spc -> getConfiguredProperties(List.of(spc)).head()).getOrNull();
            }

            @Override
            public StacProperty getLinksStacProperty() {
                String stacPropertyName = StacSpecConstants.PropertyName.STAC_LINKS_PROPERTY_NAME;
                return plugin.map(p -> makeStacProperty(p.getStacLinksProperty(), stacPropertyName,
                                                        StacPropertyType.JSON_OBJECT)).getOrElse(
                        makeDefaultStacProperty(StacSpecConstants.SourcePropertyName.STAC_LINKS_SOURCE_PROPERTY_NAME,
                                                stacPropertyName, StacPropertyType.JSON_OBJECT));
            }

            @Override
            public StacProperty getAssetsStacProperty() {
                String stacPropertyName = StacSpecConstants.PropertyName.STAC_ASSETS_PROPERTY_NAME;
                return plugin.map(p -> makeStacProperty(p.getStacAssetsProperty(), stacPropertyName,
                                                        StacPropertyType.JSON_OBJECT)).getOrElse(
                        makeDefaultStacProperty(StacSpecConstants.SourcePropertyName.STAC_ASSETS_SOURCE_PROPERTY_NAME,
                                                stacPropertyName, StacPropertyType.JSON_OBJECT));
            }

            @Override
            public List<Provider> getProviders(String datasetUrn) {
                return getCollectionConfigs(datasetUrn).flatMap(CollectionConfiguration::getProviders)
                        .map(pc -> getProvider(pc));
            }

            @Override
            public List<String> getKeywords(String datasetUrn) {
                return getCollectionConfigs(datasetUrn).flatMap(cc -> List.ofAll(cc.getKeywords()));
            }

            @Override
            public String getLicense(String datasetUrn) {
                return getCollectionConfigs(datasetUrn).headOption().map(CollectionConfiguration::getLicense)
                        .getOrNull();
            }

            private List<CollectionConfiguration> getCollectionConfigs(String datasetUrn) {
                return plugin.map(StacSearchEngine::getStacCollectionDatasetProperties).map(List::ofAll)
                        .getOrElse(List.empty()).filter(cc -> cc.getDatasetUrns().contains(datasetUrn));
            }

            @Override
            public GeoJSONReader getGeoJSONReader() {
                JtsSpatialContextFactory factory = new JtsSpatialContextFactory();
                factory.geo = geoSettings.getShouldManagePolesOnGeometries();
                return new GeoJSONReader(new JtsSpatialContext(factory), factory);
            }

            @Override
            public String getRootDynamicCollectionName() {
                return plugin.map(StacSearchEngine::getRootDynamicCollectionTitle).getOrElse("dynamic");
            }

            @Override
            public String getRootStaticCollectionName() {
                return plugin.map(StacSearchEngine::getRootStaticCollectionTitle).getOrElse("static");
            }
        };
    }

    private Provider getProvider(ProviderConfiguration pc) {
        List<ProviderRole> roles = List.ofAll(pc.getProviderRoles()).flatMap(
                role -> trying(() -> ProviderRole.valueOf(role))
                        .onFailure(t -> warn(LOGGER, "Failed to parse provider role {}", role)));
        URL providerUrl = trying(() -> new URL(pc.getProviderUrl()))
                .onFailure(t -> warn(LOGGER, "Failed to parse provider URL in {}", pc)).getOrNull();
        return new Provider(pc.getProviderName(), pc.getProviderDescription(), providerUrl, roles);
    }

    private List<StacProperty> getConfiguredProperties(List<StacPropertyConfiguration> paramConfigurations) {
        return paramConfigurations.map(s -> {
            debug(LOGGER, "Converting stac prop config: {}", s);
            StacPropertyType stacType = StacPropertyType.parse(s.getStacPropertyType());
            @SuppressWarnings("rawtypes") AbstractPropertyConverter converter = propertyConverterFactory
                    .getConverter(stacType, s.getStacPropertyFormat(), s.getSourcePropertyFormat());
            return new StacProperty(extractPropertyAccessor(s, stacType), s.getStacPropertyNamespace(),
                                    s.getStacPropertyName(), s.getStacPropertyExtension(),
                                    s.getStacComputeSummary() && canComputeSummary(stacType),
                                    s.getStacDynamicCollectionLevel(), s.getStacDynamicCollectionFormat(), stacType,
                                    converter, Boolean.FALSE);
        }).toList();
    }

    private boolean canComputeSummary(StacPropertyType type) {
        return type.canBeSummarized();
    }

    private List<StacProperty> getConfiguredProperties(StacSearchEngine plugin) {
        java.util.List<StacPropertyConfiguration> propConfigs = Option.of(plugin.getStacExtraProperties())
                .getOrElse(new ArrayList<>());
        StacPropertyConfiguration datetimeProp = plugin.getStacDatetimeProperty().toStacPropertyConfiguration();
        return addVirtualStacProperties(getConfiguredProperties(List.ofAll(propConfigs).prepend(datetimeProp)));
    }

    private StacProperty makeStacProperty(StacSourcePropertyConfiguration sourcePropertyConfiguration,
            String stacPropertyName, StacPropertyType stacType) {
        return new StacProperty(extractPropertyAccessor(sourcePropertyConfiguration, stacType), null, stacPropertyName,
                                null, false, null, null, stacType, null, Boolean.FALSE);
    }

    private StacProperty makeDefaultStacProperty(String sourcePropertyPath, String stacPropertyName,
            StacPropertyType stacType) {
        StacSourcePropertyConfiguration sourcePropertyConfiguration = new StacSourcePropertyConfiguration(
                sourcePropertyPath, null, null);
        return new StacProperty(extractPropertyAccessor(sourcePropertyConfiguration, stacType), null, stacPropertyName,
                                null, false, null, null, stacType, null, Boolean.FALSE);
    }
}
