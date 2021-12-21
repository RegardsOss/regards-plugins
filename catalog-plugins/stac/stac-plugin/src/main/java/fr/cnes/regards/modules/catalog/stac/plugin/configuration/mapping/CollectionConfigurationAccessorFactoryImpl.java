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

import fr.cnes.regards.framework.modules.plugins.service.IPluginService;
import fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacCollectionProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.AbstractPropertyConverter;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.PropertyConverterFactory;
import fr.cnes.regards.modules.catalog.stac.plugin.StacSearchCollectionEngine;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.StacSimplePropertyConfiguration;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.StacSourcePropertyConfiguration;
import fr.cnes.regards.modules.catalog.stac.service.configuration.collection.CollectionConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.collection.CollectionConfigurationAccessorFactory;
import io.vavr.CheckedFunction0;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.springframework.stereotype.Component;

/**
 * Initialize configuration for collection search management
 *
 * @author Marc SORDI
 */
@Component
public class CollectionConfigurationAccessorFactoryImpl extends AbstractConfigurationAccessor
        implements CollectionConfigurationAccessorFactory {

    private final PropertyConverterFactory propertyConverterFactory;

    public CollectionConfigurationAccessorFactoryImpl(IPluginService pluginService,
            RegardsPropertyAccessorFactory regardsPropertyAccessorFactory,
            PropertyConverterFactory propertyConverterFactory) {
        super(pluginService, regardsPropertyAccessorFactory);
        this.propertyConverterFactory = propertyConverterFactory;
    }

    @Override
    public CollectionConfigurationAccessor makeConfigurationAccessor() {

        final Try<StacSearchCollectionEngine> plugin = getPlugin(StacSearchCollectionEngine.PLUGIN_ID);
        return new CollectionConfigurationAccessor() {

            @Override
            public StacCollectionProperty getTitleProperty() {
                String stacPropertyName = StacSpecConstants.PropertyName.COLLECTION_TITLE_PROPERTY_NAME;
                return plugin.map(p -> makeStacCollectionProperty(p.getStacCollectionTitle(), stacPropertyName,
                                                                  StacPropertyType.STRING)).getOrElseTry(
                        makeDefaultStacCollectionProperty(
                                StacSpecConstants.SourcePropertyName.COLLECTION_TITLE_SOURCE_PROPERTY_NAME,
                                stacPropertyName, StacPropertyType.STRING));
            }

            @Override
            public StacCollectionProperty getDescriptionProperty() {
                String stacPropertyName = StacSpecConstants.PropertyName.COLLECTION_DESCRIPTION_PROPERTY_NAME;
                return plugin.map(p -> makeStacCollectionProperty(p.getStacCollectionDescription(), stacPropertyName,
                                                                  StacPropertyType.STRING)).getOrElseTry(
                        makeDefaultStacCollectionProperty(
                                StacSpecConstants.SourcePropertyName.COLLECTION_DESCRIPTION_SOURCE_PROPERTY_NAME,
                                stacPropertyName, StacPropertyType.STRING));
            }

            @Override
            public StacCollectionProperty getKeywordsProperty() {
                String stacPropertyName = StacSpecConstants.PropertyName.COLLECTION_KEYWORDS_PROPERTY_NAME;
                return plugin.map(p -> makeStacCollectionProperty(p.getStacCollectionKeywords(), stacPropertyName,
                                                                  StacPropertyType.STRING)).getOrElseTry(
                        makeDefaultStacCollectionProperty(
                                StacSpecConstants.SourcePropertyName.COLLECTION_KEYWORDS_SOURCE_PROPERTY_NAME,
                                stacPropertyName, StacPropertyType.STRING));
            }

            @Override
            public StacCollectionProperty getLicenseProperty() {
                String stacPropertyName = StacSpecConstants.PropertyName.COLLECTION_LICENSE_PROPERTY_NAME;
                return plugin.map(p -> makeStacCollectionProperty(p.getStacCollectionLicense(), stacPropertyName,
                                                                  StacPropertyType.STRING)).getOrElseTry(
                        makeDefaultStacCollectionProperty(
                                StacSpecConstants.SourcePropertyName.COLLECTION_LICENSE_SOURCE_PROPERTY_NAME,
                                stacPropertyName, StacPropertyType.STRING));
            }

            @Override
            public StacCollectionProperty getProvidersProperty() {
                String stacPropertyName = StacSpecConstants.PropertyName.STAC_PROVIDERS_PROPERTY_NAME;
                return plugin.map(p -> makeStacCollectionProperty(p.getStacCollectionProviders(), stacPropertyName,
                                                                  StacPropertyType.JSON_OBJECT)).getOrElseTry(
                        makeDefaultStacCollectionProperty(
                                StacSpecConstants.SourcePropertyName.STAC_PROVIDERS_SOURCE_PROPERTY_NAME,
                                stacPropertyName, StacPropertyType.JSON_OBJECT));
            }

            @Override
            public StacCollectionProperty getLinksProperty() {
                String stacPropertyName = StacSpecConstants.PropertyName.STAC_LINKS_PROPERTY_NAME;
                return plugin.map(p -> makeStacCollectionProperty(p.getStacCollectionLinks(), stacPropertyName,
                                                                  StacPropertyType.JSON_OBJECT)).getOrElseTry(
                        makeDefaultStacCollectionProperty(
                                StacSpecConstants.SourcePropertyName.STAC_LINKS_SOURCE_PROPERTY_NAME, stacPropertyName,
                                StacPropertyType.JSON_OBJECT));
            }

            @Override
            public StacCollectionProperty getAssetsProperty() {
                String stacPropertyName = StacSpecConstants.PropertyName.STAC_ASSETS_PROPERTY_NAME;
                return plugin.map(p -> makeStacCollectionProperty(p.getStacCollectionAssets(), stacPropertyName,
                                                                  StacPropertyType.JSON_OBJECT)).getOrElseTry(
                        makeDefaultStacCollectionProperty(
                                StacSpecConstants.SourcePropertyName.STAC_ASSETS_SOURCE_PROPERTY_NAME, stacPropertyName,
                                StacPropertyType.JSON_OBJECT));
            }

            @Override
            public StacCollectionProperty getLowerTemporalExtentProperty() {
                String stacPropertyName = StacSpecConstants.PropertyName.STAC_LOWER_TEMPORAL_EXTENT_PROPERTY_NAME;
                return plugin.map(p -> makeStacCollectionProperty(p.getTemporalExtent().getLower(), stacPropertyName,
                                                                  StacPropertyType.DATETIME)).getOrNull();
            }

            @Override
            public StacCollectionProperty getUpperTemporalExtentProperty() {
                String stacPropertyName = StacSpecConstants.PropertyName.STAC_UPPER_TEMPORAL_EXTENT_NAME;
                return plugin.map(p -> makeStacCollectionProperty(p.getTemporalExtent().getUpper(), stacPropertyName,
                                                                  StacPropertyType.DATETIME)).getOrNull();
            }

            @Override
            public List<StacProperty> getSummariesProperties() {
                return plugin.map(p -> makeStacProperties(List.ofAll(p.getStacCollectionSummaries())))
                        .getOrElse(List.empty());
            }

            @Override
            public List<StacProperty> getStacProperties() {
                return addVirtualStacProperties(
                        List.of(getTitleProperty().toStacProperty(), getDescriptionProperty().toStacProperty(),
                                getKeywordsProperty().toStacProperty(), getLicenseProperty().toStacProperty(),
                                getProvidersProperty().toStacProperty(), getLinksProperty().toStacProperty(),
                                getAssetsProperty().toStacProperty()).appendAll(getSummariesProperties()));
            }
        };
    }

    private StacCollectionProperty makeStacCollectionProperty(
            StacSourcePropertyConfiguration sourcePropertyConfiguration, String stacPropertyName,
            StacPropertyType stacType) {
        return new StacCollectionProperty(extractPropertyAccessor(sourcePropertyConfiguration, stacType), null,
                                          stacPropertyName, null);
    }

    private CheckedFunction0<StacCollectionProperty> makeDefaultStacCollectionProperty(String sourcePropertyPath,
            String stacPropertyName, StacPropertyType stacType) {
        return () -> {
            StacSourcePropertyConfiguration sourcePropertyConfiguration = new StacSourcePropertyConfiguration(
                    sourcePropertyPath, null, null);
            return new StacCollectionProperty(extractPropertyAccessor(sourcePropertyConfiguration, stacType), null,
                                              stacPropertyName, null);
        };
    }

    private List<StacProperty> makeStacProperties(List<StacSimplePropertyConfiguration> simplePropertyConfigurations) {
        return simplePropertyConfigurations.map(p -> makeStacProperty(p));
    }

    private StacProperty makeStacProperty(StacSimplePropertyConfiguration simplePropertyConfiguration) {
        StacPropertyType stacPropertyType = StacPropertyType.parse(simplePropertyConfiguration.getStacPropertyType());
        @SuppressWarnings("rawtypes") AbstractPropertyConverter converter = propertyConverterFactory.getConverter(
                stacPropertyType, simplePropertyConfiguration.getStacPropertyFormat(),
                simplePropertyConfiguration.getSourcePropertyFormat());
        return new StacProperty(extractPropertyAccessor(simplePropertyConfiguration, stacPropertyType),
                                simplePropertyConfiguration.getStacPropertyNamespace(),
                                simplePropertyConfiguration.getStacPropertyName(),
                                simplePropertyConfiguration.getStacPropertyExtension(), null, null, null,
                                stacPropertyType, converter, Boolean.FALSE);
    }
}
