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

package fr.cnes.regards.modules.catalog.stac.service.configuration;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.collection.Provider;
import io.vavr.collection.List;
import org.locationtech.spatial4j.io.GeoJSONReader;

/**
 * Provides access to the values present in the STAC plugin configuration.
 */
public interface ConfigurationAccessor {

    /**
     * Get the list of {@link StacProperty} defined in the current project
     * StacSearchEngine configuration
     */
    List<StacProperty> getStacProperties();

    StacProperty getDatetimeStacProperty();

    StacProperty getLinksStacProperty();

    StacProperty getAssetsStacProperty();

    List<Provider> getProviders(String datasetUrn);

    List<String> getKeywords(String datasetUrn);

    String getLicense(String datasetUrn);

    GeoJSONReader getGeoJSONReader();

    String getTitle();

    String getDescription();

    String getRootDynamicCollectionName();

    String getRootStaticCollectionName();

    String getEODAGPortalName();

    String getEODAGProvider();

    String getEODAGApiKey();

    String getHistogramProperyPath();

    boolean isHumanReadableIdsEnabled();

    boolean useCollectionConfiguration();

    boolean isDisableauthParam();
}
