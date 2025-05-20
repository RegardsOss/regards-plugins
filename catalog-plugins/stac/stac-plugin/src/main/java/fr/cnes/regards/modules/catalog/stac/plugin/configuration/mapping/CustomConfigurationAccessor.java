/*
 * Copyright 2017-2024 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.modules.catalog.stac.domain.DefaultSourceProperties;
import fr.cnes.regards.modules.catalog.stac.domain.StacProperties;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.spec.collection.Provider;
import fr.cnes.regards.modules.catalog.stac.plugin.StacSearchEngine;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.CollectionConfiguration;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.EODAGConfiguration;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.locationtech.spatial4j.context.jts.JtsSpatialContext;
import org.locationtech.spatial4j.context.jts.JtsSpatialContextFactory;
import org.locationtech.spatial4j.io.GeoJSONReader;

/**
 * Provides access to the values present in the STAC plugin configuration.
 *
 * @author Sébastien Binda
 **/
public class CustomConfigurationAccessor implements ConfigurationAccessor {

    private final IRuntimeTenantResolver runtimeTenantResolver;

    private final Try<StacSearchEngine> plugin;

    private final ConfigurationAccessorFactoryImpl configurationAccessorFactoryImpl;

    public CustomConfigurationAccessor(IRuntimeTenantResolver runtimeTenantResolver,
                                       Try<StacSearchEngine> plugin,
                                       ConfigurationAccessorFactoryImpl configurationAccessorFactoryImpl) {
        this.runtimeTenantResolver = runtimeTenantResolver;
        this.plugin = plugin;
        this.configurationAccessorFactoryImpl = configurationAccessorFactoryImpl;
    }

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
    public List<StacProperty> getStacProperties() {
        return plugin.map(configurationAccessorFactoryImpl::getConfiguredProperties).getOrElse(List.empty());
    }

    @Override
    public StacProperty getDatetimeStacProperty() {
        return plugin.map(p -> p.getStacDatetimeProperty().toStacPropertyConfiguration())
                     .map(spc -> configurationAccessorFactoryImpl.getConfiguredProperties(List.of(spc)).head())
                     .getOrNull();
    }

    @Override
    public StacProperty getLinksStacProperty() {
        String stacPropertyName = StacProperties.STAC_LINKS_PROPERTY_NAME;
        return plugin.map(p -> configurationAccessorFactoryImpl.makeStacProperty(p.getStacLinksProperty(),
                                                                                 stacPropertyName,
                                                                                 StacPropertyType.JSON_OBJECT))
                     .getOrElse(configurationAccessorFactoryImpl.makeDefaultStacProperty(DefaultSourceProperties.STAC_LINKS_SOURCE_PROPERTY_NAME,
                                                                                         stacPropertyName,
                                                                                         StacPropertyType.JSON_OBJECT));
    }

    @Override
    public StacProperty getAssetsStacProperty() {
        String stacPropertyName = StacProperties.STAC_ASSETS_PROPERTY_NAME;
        return plugin.map(p -> configurationAccessorFactoryImpl.makeStacProperty(p.getStacAssetsProperty(),
                                                                                 stacPropertyName,
                                                                                 StacPropertyType.JSON_OBJECT))
                     .getOrElse(configurationAccessorFactoryImpl.makeDefaultStacProperty(DefaultSourceProperties.STAC_ASSETS_SOURCE_PROPERTY_NAME,
                                                                                         stacPropertyName,
                                                                                         StacPropertyType.JSON_OBJECT));
    }

    @Override
    public List<Provider> getProviders(String datasetUrn) {
        return getCollectionConfigs(datasetUrn).flatMap(CollectionConfiguration::getProviders)
                                               .map(configurationAccessorFactoryImpl::getProvider);
    }

    @Override
    public List<String> getKeywords(String datasetUrn) {
        return getCollectionConfigs(datasetUrn).flatMap(cc -> List.ofAll(cc.getKeywords()));
    }

    @Override
    public String getLicense(String datasetUrn) {
        return getCollectionConfigs(datasetUrn).headOption().map(CollectionConfiguration::getLicense).getOrNull();
    }

    private List<CollectionConfiguration> getCollectionConfigs(String datasetUrn) {
        return plugin.map(StacSearchEngine::getStacCollectionDatasetProperties)
                     .map(List::ofAll)
                     .getOrElse(List.empty())
                     .filter(cc -> cc.getDatasetUrns().contains(datasetUrn));
    }

    @Override
    public GeoJSONReader getGeoJSONReader() {
        JtsSpatialContextFactory factory = new JtsSpatialContextFactory();
        factory.geo = configurationAccessorFactoryImpl.getGeoSettings().getShouldManagePolesOnGeometries();
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

    @Override
    public String getEODAGPortalName() {
        return plugin.map(StacSearchEngine::getEodagConfiguration)
                     .map(EODAGConfiguration::getPortalName)
                     .getOrElse(this::getTitle);
    }

    @Override
    public String getEODAGProvider() {
        return plugin.map(StacSearchEngine::getEodagConfiguration)
                     .map(EODAGConfiguration::getProvider)
                     .getOrElse("provider");
    }

    @Override
    public String getEODAGApiKey() {
        return plugin.map(StacSearchEngine::getEodagConfiguration)
                     .map(EODAGConfiguration::getApiKey)
                     .getOrElse("apiKey");
    }

    @Override
    public String getHistogramProperyPath() {
        return plugin.map(StacSearchEngine::getHistogramPropertyPath).getOrNull();
    }

    @Override
    public boolean isHumanReadableIdsEnabled() {
        return plugin.map(StacSearchEngine::isEnableHumanReadableIds).getOrElse(false);
    }

    @Override
    public boolean useCollectionConfiguration() {
        return plugin.map(StacSearchEngine::isUseCollectionConfiguration).getOrElse(false);
    }

    @Override
    public boolean isDisableauthParam() {
        return plugin.map(StacSearchEngine::isDisableAuthParam).getOrElse(false);
    }
}
