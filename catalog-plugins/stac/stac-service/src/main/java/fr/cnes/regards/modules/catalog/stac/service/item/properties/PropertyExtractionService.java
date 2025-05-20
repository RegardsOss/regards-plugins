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
package fr.cnes.regards.modules.catalog.stac.service.item.properties;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacCollectionProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Asset;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Link;
import fr.cnes.regards.modules.catalog.stac.service.item.extensions.FieldExtension;
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
                                              List<StacProperty> stacProperties,
                                              FieldExtension fieldExtension);

    default Map<String, Object> extractStacProperties(AbstractEntity<? extends EntityFeature> feature,
                                                      List<StacProperty> stacProperties) {
        return this.extractStacProperties(feature, stacProperties, FieldExtension.disable());
    }

    Map<String, Asset> extractAssets(AbstractEntity<? extends EntityFeature> feature,
                                     Map<String, Asset> staticFeatureAssets,
                                     FieldExtension fieldExtension);

    default Map<String, Asset> extractAssets(AbstractEntity<? extends EntityFeature> feature,
                                             Map<String, Asset> staticFeatureAssets) {
        return this.extractAssets(feature, staticFeatureAssets, FieldExtension.disable());
    }

    /**
     * Static assets if directly available in the feature with the proper structure
     */
    Map<String, Asset> extractStaticAssets(AbstractEntity<? extends EntityFeature> feature,
                                           StacProperty stacAssetsProperty,
                                           FieldExtension fieldExtension);

    default Map<String, Asset> extractStaticAssets(AbstractEntity<? extends EntityFeature> feature,
                                                   StacProperty stacAssetsProperty) {
        return this.extractStaticAssets(feature, stacAssetsProperty, FieldExtension.disable());
    }

    /**
     * Static links if directly available in the feature with the proper structure
     */
    List<Link> extractStaticLinks(AbstractEntity<? extends EntityFeature> feature,
                                  StacProperty stacLinksProperty,
                                  FieldExtension fieldExtension);

    default List<Link> extractStaticLinks(AbstractEntity<? extends EntityFeature> feature,
                                          StacProperty stacLinksProperty) {
        return this.extractStaticLinks(feature, stacLinksProperty, FieldExtension.disable());
    }

    Set<String> extractExtensionsFromConfiguration(List<StacProperty> stacProperties,
                                                   Set<String> internalExtensions,
                                                   FieldExtension fieldExtension);

    default Set<String> extractExtensionsFromConfiguration(List<StacProperty> stacProperties,
                                                           Set<String> internalExtensions) {
        return this.extractExtensionsFromConfiguration(stacProperties, internalExtensions, FieldExtension.disable());
    }
}
