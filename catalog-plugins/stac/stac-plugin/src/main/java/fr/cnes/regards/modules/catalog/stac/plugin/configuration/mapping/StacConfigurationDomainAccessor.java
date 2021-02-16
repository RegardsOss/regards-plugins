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
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.catalog.stac.domain.properties.PropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
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
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.Optional;

import static fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants.PropertyName.DATETIME_PROPERTY_NAME;

/**
 * Allows to transform property configuration to domain properties, and access
 * configuration in its "domain" form.
 */
@Component
public class StacConfigurationDomainAccessor implements ConfigurationAccessorFactory {

    private final PropertyConverterFactory propertyConverterFactory;

    private final IPluginService pluginService;

    @Autowired
    public StacConfigurationDomainAccessor(
            PropertyConverterFactory propertyConverterFactory,
            IPluginService pluginService
    ) {
        this.propertyConverterFactory = propertyConverterFactory;
        this.pluginService = pluginService;
    }

    @Override
    public ConfigurationAccessor makeConfigurationAccessor() {
        final Option<StacSearchEngine> plugin = getPlugin();
        return new ConfigurationAccessor() {
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
                        .flatMap(cc -> cc.getProviders())
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
                    .map(cc -> cc.getLicense())
                    .getOrNull();
            }

            private List<CollectionConfiguration> getCollectionConfigs(String datasetUrn) {
                return plugin
                        .map(StacSearchEngine::getStacCollectionDatasetProperties)
                        .map(List::ofAll)
                        .getOrElse(List.empty())
                        .filter(cc -> cc.getDatasetUrns().contains(datasetUrn));
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
                    PropertyType type = PropertyType.parse(s.getStacType());
                    AbstractPropertyConverter converter = propertyConverterFactory.getConverter(
                            type,
                            s.getStacFormat(),
                            s.getRegardsFormat()
                    );
                    return new StacProperty(
                            s.getModelAttributeName(),
                            s.getStacPropertyName(),
                            s.getStacExtension(),
                            s.getStacComputeExtent(),
                            s.getStacDynamicCollectionLevel(),
                            type,
                            converter
                    );
                })
                .toList();
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
        return getConfiguredProperties(List.ofAll(plugin.getStacExtraProperties())
                .prepend(plugin.getStacDatetimeProperty().withStacPropertyName(DATETIME_PROPERTY_NAME)));
    }

}
