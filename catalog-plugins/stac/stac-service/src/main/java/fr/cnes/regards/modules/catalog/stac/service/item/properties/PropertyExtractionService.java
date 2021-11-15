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
package fr.cnes.regards.modules.catalog.stac.service.item.properties;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacCollectionProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Asset;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.Dataset;
import fr.cnes.regards.modules.dam.domain.entities.feature.EntityFeature;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;

/**
 * Extraction tools for both collections and items
 *
 * @author Marc SORDI
 */
public interface PropertyExtractionService {

    Map<String, Object> extractStacProperties(AbstractEntity<? extends EntityFeature> feature,
            List<StacProperty> stacProperties);

    Map<String, Asset> extractAssets(AbstractEntity<? extends EntityFeature> feature);

    List<Link> extractLinks(AbstractEntity<? extends EntityFeature> feature, StacProperty sp);

    Set<String> extractExtensionsFromConfiguration(List<StacProperty> stacProperties);

    Extent.Temporal extractTemporalExtent(Dataset dataset, StacCollectionProperty lowerTemporalExtent,
            StacCollectionProperty upperTemporalExtent);
}
