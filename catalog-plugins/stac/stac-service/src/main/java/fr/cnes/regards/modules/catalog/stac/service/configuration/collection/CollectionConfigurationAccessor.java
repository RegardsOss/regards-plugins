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
package fr.cnes.regards.modules.catalog.stac.service.configuration.collection;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacCollectionProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import io.vavr.collection.List;

/**
 * Collection configuration to fulfil response of collection search.
 * This is the representation of the STAC collection plugin configuration.
 */
public interface CollectionConfigurationAccessor {

    StacCollectionProperty getTitleProperty();

    StacCollectionProperty getDescriptionProperty();

    StacCollectionProperty getKeywordsProperty();

    StacCollectionProperty getLicenseProperty();

    StacCollectionProperty getProvidersProperty();

    StacCollectionProperty getLinksProperty();

    StacCollectionProperty getAssetsProperty();

    StacCollectionProperty getLowerTemporalExtentProperty();

    StacCollectionProperty getUpperTemporalExtentProperty();

    List<StacProperty> getSummariesProperties();

    /**
     * @return aggregation of all properties as {@link StacProperty} for reverse mapping
     */
    List<StacProperty> getStacProperties();
}
