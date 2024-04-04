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

package fr.cnes.regards.modules.catalog.stac.service.item;

import fr.cnes.regards.framework.geojson.GeoJsonType;
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Item;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Asset;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.Centroid;
import fr.cnes.regards.modules.catalog.stac.domain.utils.StacGeoHelper;
import fr.cnes.regards.modules.catalog.stac.service.collection.IdMappingService;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.item.properties.PropertyExtractionService;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.StacLinkCreator;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.feature.EntityFeature;
import io.vavr.Tuple;
import io.vavr.Tuple3;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.locationtech.spatial4j.io.GeoJSONReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.ENTITY_TO_ITEM_CONVERSION;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.debug;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;
import static java.lang.String.format;

/**
 * Default implementation for {@link RegardsFeatureToStacItemConverter} interface.
 */
@Component
public class RegardsFeatureToStacItemConverterImpl implements RegardsFeatureToStacItemConverter, StacLinkCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegardsFeatureToStacItemConverterImpl.class);

    private final StacGeoHelper geoHelper;

    private final ConfigurationAccessorFactory configurationAccessorFactory;

    private final PropertyExtractionService propertyExtractionService;

    private IdMappingService idMappingService;

    public RegardsFeatureToStacItemConverterImpl(StacGeoHelper geoHelper,
                                                 ConfigurationAccessorFactory configurationAccessorFactory,
                                                 PropertyExtractionService propertyExtractionService,
                                                 IdMappingService idMappingService) {
        this.geoHelper = geoHelper;
        this.configurationAccessorFactory = configurationAccessorFactory;
        this.propertyExtractionService = propertyExtractionService;
        this.idMappingService = idMappingService;
    }

    @Override
    public Try<Item> convertFeatureToItem(List<StacProperty> properties,
                                          OGCFeatLinkCreator linkCreator,
                                          AbstractEntity<? extends EntityFeature> feature) {
        debug(LOGGER, "Converting to item: Feature={}\n\twith Properties={}", feature, properties);
        return trying(() -> {
            ConfigurationAccessor configurationAccessor = configurationAccessorFactory.makeConfigurationAccessor();
            Map<String, Object> featureStacProperties = propertyExtractionService.extractStacProperties(feature,
                                                                                                        properties);
            List<Link> staticFeatureLinks = propertyExtractionService.extractStaticLinks(feature,
                                                                                         configurationAccessor.getLinksStacProperty());
            Map<String, Asset> staticFeatureAssets = propertyExtractionService.extractStaticAssets(feature,
                                                                                                   configurationAccessor.getAssetsStacProperty());
            Set<String> extensions = propertyExtractionService.extractExtensionsFromConfiguration(properties);
            Tuple3<IGeometry, BBox, Centroid> geo = extractGeo(feature, configurationAccessor.getGeoJSONReader());
            String collection = extractCollection(feature).getOrNull();
            String itemId = feature.getIpId().toString();

            Item result = new Item(extensions,
                                   itemId,
                                   geo._2,
                                   geo._1,
                                   geo._3,
                                   collection,
                                   featureStacProperties,
                                   extractLinks(itemId, collection, linkCreator).appendAll(staticFeatureLinks),
                                   propertyExtractionService.extractAssets(feature).merge(staticFeatureAssets));
            debug(LOGGER, "Result Item={}", result);
            return result;
        }).mapFailure(ENTITY_TO_ITEM_CONVERSION,
                      () -> format("Failed to convert data object %s to item", feature.getIpId()));
    }

    private List<Link> extractLinks(String itemId, String collection, OGCFeatLinkCreator linkCreator) {
        return List.of(linkCreator.createRootLink(),
                       linkCreator.createCollectionLink(collection, "Item collection"),
                       linkCreator.createItemLink(collection, itemId)).flatMap(tl -> tl);
    }

    private Option<String> extractCollection(AbstractEntity<? extends EntityFeature> feature) {
        Option<String> urn = HashSet.ofAll(feature.getTags())
                                    .filter(tag -> tag.startsWith("URN:"))
                                    .filter(tag -> tag.contains(":DATASET:"))
                                    .headOption();

        return Option.of(idMappingService.getStacIdByUrn(urn.get()));
    }

    private Tuple3<IGeometry, BBox, Centroid> extractGeo(AbstractEntity<? extends EntityFeature> feature,
                                                         GeoJSONReader geoJSONReader) {
        Option<IGeometry> geometry = Option.of(feature.getFeature().getGeometry());
        if (geometry.isDefined() && !GeoJsonType.UNLOCATED.equals(geometry.get().getType())) {
            Option<BBox> bbox = Option.ofOptional(feature.getFeature().getBbox()).flatMap(this::extractBBox);
            return geometry.flatMap(g -> bbox.map(b -> Tuple.of(g, b, b.centroid()))
                                             .orElse(geoHelper.computeBBoxCentroid(g, geoJSONReader)))
                           .orElse(bbox.map(bb -> Tuple.of(null, bb, bb.centroid())))
                           .getOrElse(() -> Tuple.of(null, null, null));
        } else {
            return new Tuple3<>(IGeometry.unlocated(), null, null);
        }
    }

    private Option<BBox> extractBBox(Double[] doubles) {
        if (doubles.length == 4) {
            return Option.of(new BBox(doubles[0], doubles[1], doubles[2], doubles[3]));
        } else {
            return Option.none();
        }
    }
}
