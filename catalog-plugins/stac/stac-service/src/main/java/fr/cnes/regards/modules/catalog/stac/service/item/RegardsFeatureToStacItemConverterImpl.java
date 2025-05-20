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
import fr.cnes.regards.modules.catalog.stac.domain.StacConstants;
import fr.cnes.regards.modules.catalog.stac.domain.api.Fields;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.Item;
import fr.cnes.regards.modules.catalog.stac.domain.spec.STACType;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Asset;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Link;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Relation;
import fr.cnes.regards.modules.catalog.stac.domain.spec.extensions.FileInfoExtension;
import fr.cnes.regards.modules.catalog.stac.domain.spec.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.domain.utils.StacGeoHelper;
import fr.cnes.regards.modules.catalog.stac.service.collection.IdMappingService;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.item.extensions.FieldExtension;
import fr.cnes.regards.modules.catalog.stac.service.item.properties.PropertyExtractionService;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.StacLinkCreator;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.feature.EntityFeature;
import io.vavr.Tuple;
import io.vavr.Tuple2;
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

    private final IdMappingService idMappingService;

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
                                          Fields fields,
                                          OGCFeatLinkCreator linkCreator,
                                          AbstractEntity<? extends EntityFeature> feature) {

        debug(LOGGER, "Converting to item: Feature={}\n\twith Properties={}", feature, properties);
        return trying(() -> {
            // Initialize field extensions
            FieldExtension fieldExtension = FieldExtension.build(fields, properties.toJavaList());
            ConfigurationAccessor configurationAccessor = configurationAccessorFactory.makeConfigurationAccessor();
            Map<String, Object> featureStacProperties = propertyExtractionService.extractStacProperties(feature,
                                                                                                        properties,
                                                                                                        fieldExtension);

            // Links from feature properties
            List<Link> staticFeatureLinks = propertyExtractionService.extractStaticLinks(feature,
                                                                                         configurationAccessor.getLinksStacProperty(),
                                                                                         fieldExtension);
            Map<String, Asset> staticFeatureAssets = propertyExtractionService.extractStaticAssets(feature,
                                                                                                   configurationAccessor.getAssetsStacProperty(),
                                                                                                   fieldExtension);
            Set<String> extensions = propertyExtractionService.extractExtensionsFromConfiguration(properties,
                                                                                                  HashSet.of(
                                                                                                      FileInfoExtension.EXTENSION_ID),
                                                                                                  fieldExtension);
            Tuple2<IGeometry, BBox> geo = extractGeo(feature, configurationAccessor.getGeoJSONReader(), fieldExtension);
            String collection = extractCollection(feature, fieldExtension).getOrNull();
            String itemId = extractItemId(feature, configurationAccessor, fieldExtension);

            Item result = new Item(fieldExtension.isTypeIncluded() ? STACType.FEATURE : null,
                                   fieldExtension.isStacVersionIncluded() ? StacConstants.STAC_SPEC_VERSION : null,
                                   extensions,
                                   itemId,
                                   geo._1,
                                   geo._2,
                                   featureStacProperties,
                                   extractItemLinks(itemId,
                                                    collection,
                                                    linkCreator,
                                                    staticFeatureLinks,
                                                    fieldExtension),
                                   propertyExtractionService.extractAssets(feature,
                                                                           staticFeatureAssets,
                                                                           fieldExtension),
                                   collection);
            debug(LOGGER, "Result Item={}", result);
            return result;
        }).mapFailure(ENTITY_TO_ITEM_CONVERSION,
                      () -> format("Failed to convert data object %s to item", feature.getIpId()));
    }

    private String extractItemId(AbstractEntity<? extends EntityFeature> feature,
                                 ConfigurationAccessor configurationAccessor,
                                 FieldExtension fieldExtension) {

        // Skip id extraction according to field extension
        if (!fieldExtension.isIdIncluded()) {
            return null;
        }

        return idMappingService.getItemId(feature.getIpId(),
                                          feature.getProviderId(),
                                          configurationAccessor.isHumanReadableIdsEnabled());
    }

    private List<Link> extractItemLinks(String itemId,
                                        String collection,
                                        OGCFeatLinkCreator linkCreator,
                                        List<Link> staticFeatureLinks,
                                        FieldExtension fieldExtension) {

        // Skip links extraction according to field extension
        if (!fieldExtension.isLinksIncluded()) {
            return null;
        }

        return List.of(linkCreator.createLandingPageLink(Relation.ROOT),
                       linkCreator.createCollectionLink(Relation.COLLECTION, collection, "Item collection"),
                       linkCreator.createCollectionLink(Relation.PARENT, collection, "Parent collection"),
                       linkCreator.createItemLink(Relation.SELF, collection, itemId))
                   .flatMap(tl -> tl)
                   .appendAll(staticFeatureLinks);
    }

    private Option<String> extractCollection(AbstractEntity<? extends EntityFeature> feature,
                                             FieldExtension fieldExtension) {
        // Skip collection extraction according to field extension
        if (!fieldExtension.isCollectionIncluded()) {
            return Option.none();
        }

        Option<String> urn = HashSet.ofAll(feature.getTags())
                                    .filter(tag -> tag.startsWith("URN:"))
                                    .filter(tag -> tag.contains(":DATASET:"))
                                    .headOption();

        return Option.of(idMappingService.getStacIdByUrn(urn.get()));
    }

    private Tuple2<IGeometry, BBox> extractGeo(AbstractEntity<? extends EntityFeature> feature,
                                               GeoJSONReader geoJSONReader,
                                               FieldExtension fieldExtension) {

        // Skip geometry extraction according to field extension
        if (!fieldExtension.isGeometryIncluded()) {
            return new Tuple2<>(IGeometry.unlocated(), null);
        }

        Option<IGeometry> geometry = Option.of(feature.getFeature().getGeometry());
        if (geometry.isDefined() && !GeoJsonType.UNLOCATED.equals(geometry.get().getType())) {
            Option<BBox> bbox = Option.ofOptional(feature.getFeature().getBbox()).flatMap(this::extractBBox);
            // Compute the bounding box if not provided by the feature using the geometry
            // If no geometry is provided, the bounding box will be null as the stac spec requires that
            return geometry.flatMap(g -> bbox.map(b -> Tuple.of(g, b)).orElse(geoHelper.computeBBox(g, geoJSONReader)))
                           .getOrElse(() -> Tuple.of(null, null));
        } else {
            return new Tuple2<>(IGeometry.unlocated(), null);
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
