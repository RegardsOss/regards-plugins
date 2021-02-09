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

import fr.cnes.regards.modules.catalog.stac.domain.properties.PropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.AbstractPropertyConverter;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.PropertyConverterFactory;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.StacPropertyConfiguration;
import io.vavr.collection.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Allows to transform property configuration to domain properties
 */
@Component
public class StacPropertyConfigurationToDomainPropertyMapper {

    @Autowired
    private final PropertyConverterFactory propertyConverterFactory;

    public StacPropertyConfigurationToDomainPropertyMapper(PropertyConverterFactory propertyConverterFactory) {
        this.propertyConverterFactory = propertyConverterFactory;
    }

    public List<StacProperty> getConfiguredProperties(List<StacPropertyConfiguration> paramConfigurations) {
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

}
