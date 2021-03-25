/*
 * Copyright 2017-2021 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.service.IPluginService;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.catalog.stac.domain.properties.RegardsPropertyAccessor;
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
import java.util.Optional;

/**
 * Allows to transform property configuration to domain properties, and access
 * configuration in its "domain" form.
 */
@Component
public class StacConfigurationDomainAccessor implements ConfigurationAccessorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(StacConfigurationDomainAccessor.class);

    private final PropertyConverterFactory propertyConverterFactory;

    private final IPluginService pluginService;

    private final RegardsPropertyAccessorFactory regardsPropertyAccessorFactory;

    private final IRuntimeTenantResolver runtimeTenantResolver;

    private final ProjectGeoSettings geoSettings;

    @Autowired
    public StacConfigurationDomainAccessor(
            PropertyConverterFactory propertyConverterFactory,
            IPluginService pluginService,
            RegardsPropertyAccessorFactory regardsPropertyAccessorFactory,
            IRuntimeTenantResolver runtimeTenantResolver,
            ProjectGeoSettings geoSettings) {
        this.propertyConverterFactory = propertyConverterFactory;
        this.pluginService = pluginService;
        this.regardsPropertyAccessorFactory = regardsPropertyAccessorFactory;
        this.runtimeTenantResolver = runtimeTenantResolver;
        this.geoSettings = geoSettings;
    }

    @Override
    public ConfigurationAccessor makeConfigurationAccessor() {
        final Option<StacSearchEngine> plugin = getPlugin();
        return new ConfigurationAccessor() {
            @Override
            public String getTitle() {
                return plugin.map(StacSearchEngine::getStacTitle).getOrElse(() ->{
                    String tenant = runtimeTenantResolver.getTenant();
                    return "STAC Catalog " + tenant;
                });
            }

            @Override
            public String getDescription() {
                return plugin.map(StacSearchEngine::getStacDescription).getOrElse(() ->{
                    String tenant = runtimeTenantResolver.getTenant();
                    return "STAC Catalog " + tenant;
                });
            }

            @Override
            public boolean hasStacConfiguration() {
                return plugin.isDefined();
            }

            @Override
            public List<StacProperty> getStacProperties() {
                return plugin
                        .map(StacConfigurationDomainAccessor.this::getConfiguredProperties)
                        .getOrElse(List.empty());
            }

            @Override
            public List<Provider> getProviders(String datasetUrn) {
                return getCollectionConfigs(datasetUrn)
                        .flatMap(CollectionConfiguration::getProviders)
                        .map(pc -> getProvider(pc));
            }

            @Override
            public List<String> getKeywords(String datasetUrn) {
                return getCollectionConfigs(datasetUrn)
                    .flatMap(cc -> List.ofAll(cc.getKeywords()));
            }

            @Override
            public String getLicense(String datasetUrn) {
                return getCollectionConfigs(datasetUrn)
                    .headOption()
                    .map(CollectionConfiguration::getLicense)
                    .getOrNull();
            }

            private List<CollectionConfiguration> getCollectionConfigs(String datasetUrn) {
                return plugin
                    .map(StacSearchEngine::getStacCollectionDatasetProperties)
                    .map(List::ofAll)
                    .getOrElse(List.empty())
                    .filter(cc -> cc.getDatasetUrns().contains(datasetUrn));
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
        List<ProviderRole> roles = List.ofAll(pc.getProviderRoles())
                .flatMap(role -> Try.of(() -> ProviderRole.valueOf(role)));
        URL providerUrl = Try.of(() -> new URL(pc.getProviderUrl())).getOrNull();
        return new Provider(pc.getProviderName(), pc.getProviderDescription(), providerUrl, roles);
    }

    private List<StacProperty> getConfiguredProperties(List<StacPropertyConfiguration> paramConfigurations) {
        return paramConfigurations
                .map(s -> {
                    LOGGER.debug("Convertng stac prop config: {}", s);
                    StacPropertyType stacType = StacPropertyType.parse(s.getStacType());
                    AbstractPropertyConverter converter = propertyConverterFactory.getConverter(
                        stacType,
                        s.getStacFormat(),
                        s.getRegardsFormat()
                    );
                    return new StacProperty(
                        extractPropertyAccessor(s, stacType),
                        s.getStacPropertyName(),
                        s.getStacExtension(),
                        s.getStacComputeSummary() && canComputeSummary(stacType),
                        s.getStacDynamicCollectionLevel(),
                        s.getStacDynamicCollectionFormat(),
                        stacType,
                        converter
                    );
                })
                .toList();
    }

    private RegardsPropertyAccessor extractPropertyAccessor(
            StacPropertyConfiguration sPropConfig,
            StacPropertyType stacType
    ) {
        return this.regardsPropertyAccessorFactory.makeRegardsPropertyAccessor(sPropConfig, stacType);
    }

    private boolean canComputeSummary(StacPropertyType type) {
        return type.canBeSummarized();
    }

    private Option<StacSearchEngine> getPlugin() {
        return List.ofAll(pluginService.getActivePluginConfigurations(StacSearchEngine.PLUGIN_ID))
            .headOption()
            .flatMap(pc -> loadPluginFromConfiguration(pc));

    }

    private Option<StacSearchEngine> loadPluginFromConfiguration(PluginConfiguration pc) {
        return Try.of(() -> getOptionalPlugin(pc))
            .toOption()
            .flatMap(Option::ofOptional);
    }

    private Optional<StacSearchEngine> getOptionalPlugin(PluginConfiguration pc)
            throws NotAvailablePluginConfigurationException {
        return pluginService.getOptionalPlugin(pc.getBusinessId());
    }

    private List<StacProperty> getConfiguredProperties(StacSearchEngine plugin) {
        java.util.List<StacPropertyConfiguration> propConfigs = Option
            .of(plugin.getStacExtraProperties())
            .getOrElse(new ArrayList<>());
        StacPropertyConfiguration datetimeProp = plugin.getStacDatetimeProperty().toStacPropertyCOnfiguration();
        return getConfiguredProperties(List.ofAll(propConfigs)
            .prepend(datetimeProp));
    }

}
