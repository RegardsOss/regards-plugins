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

package fr.cnes.regards.modules.catalog.stac.service.configuration;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Provider;
import io.vavr.collection.List;

/**
 * Provides access to values present in the STAC plugin configuration.
 */
public interface ConfigurationAccessor {

    /**
     * Tells if the STAC search engine is configured for the current project
     * @return true if a PluginCOnfiguration is found for StacSearchEngine
     */
    boolean hasStacConfiguration();

    /**
     * Get the list of {@link StacProperty} defined in the current project
     * StacSearchEngine configuration
     * @return
     */
    List<StacProperty> getStacProperties();

    List<Provider> getProviders(String datasetUrn);

    List<String> getKeywords(String datasetUrn);

    String getLicense(String datasetUrn);

}
