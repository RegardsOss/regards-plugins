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
import fr.cnes.regards.modules.catalog.stac.domain.properties.RegardsPropertyAccessor;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.StacPropertyConfiguration;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.StacSourcePropertyConfiguration;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.PLUGIN_CONFIGURATION_ACCESS;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;
import static java.lang.String.format;

/**
 * @author Marc SORDI
 */
public abstract class AbstractConfigurationAccessor {

    private final IPluginService pluginService;

    private final RegardsPropertyAccessorFactory regardsPropertyAccessorFactory;

    public AbstractConfigurationAccessor(IPluginService pluginService,
            RegardsPropertyAccessorFactory regardsPropertyAccessorFactory) {
        this.pluginService = pluginService;
        this.regardsPropertyAccessorFactory = regardsPropertyAccessorFactory;
    }

    protected <E> Try<E> getPlugin(String pluginId) {
        return List.ofAll(pluginService.getActivePluginConfigurations(pluginId)).headOption().toTry()
                .flatMap(this::loadPluginFromConfiguration);

    }

    protected <E> Try<E> loadPluginFromConfiguration(PluginConfiguration pc) {
        return (Try<E>) trying(() -> getOptionalPlugin(pc).get())
                .mapFailure(PLUGIN_CONFIGURATION_ACCESS, () -> format("Failed to load plugin configuration in %s", pc));
    }

    protected <E> Option<E> getOptionalPlugin(PluginConfiguration pc) throws NotAvailablePluginConfigurationException {
        return Option.ofOptional(pluginService.getOptionalPlugin(pc.getBusinessId()));
    }

    protected RegardsPropertyAccessor extractPropertyAccessor(StacSourcePropertyConfiguration sPropConfig,
            StacPropertyType stacType) {
        return regardsPropertyAccessorFactory.makeRegardsPropertyAccessor(sPropConfig, stacType);
    }
}
