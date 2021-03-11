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

package fr.cnes.regards.modules.catalog.stac.service;

import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Item;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Asset;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.Centroid;
import fr.cnes.regards.modules.catalog.stac.domain.utils.StacGeoHelper;
import fr.cnes.regards.modules.catalog.stac.service.collection.dynamic.DynamicCollectionService;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.indexer.domain.DataFile;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.collection.*;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.locationtech.spatial4j.io.GeoJSONReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Default implementation for {@link RegardsFeatureToStacItemConverter} interface.
 */
@Component
public class RegardsFeatureToStacItemConverterImpl implements RegardsFeatureToStacItemConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegardsFeatureToStacItemConverterImpl.class);
    
    private final StacGeoHelper geoHelper;
    private final ConfigurationAccessorFactory configurationAccessorFactory;
    private final DynamicCollectionService dynamicCollectionService;
    private final IRuntimeTenantResolver runtimeTenantResolver;

    public RegardsFeatureToStacItemConverterImpl(
            StacGeoHelper geoHelper,
            ConfigurationAccessorFactory configurationAccessorFactory,
            DynamicCollectionService dynamicCollectionService,
            IRuntimeTenantResolver runtimeTenantResolver
    ) {
        this.geoHelper = geoHelper;
        this.configurationAccessorFactory = configurationAccessorFactory;
        this.dynamicCollectionService = dynamicCollectionService;
        this.runtimeTenantResolver = runtimeTenantResolver;
    }

    @Override
    public Try<Item> convertFeatureToItem(List<StacProperty> properties, OGCFeatLinkCreator linkCreator, DataObject feature) {
        return Try.of(() -> {
            ConfigurationAccessor configurationAccessor = configurationAccessorFactory.makeConfigurationAccessor();
            Map<String,Object> featureStacProperties = extractStacPropertyKeyValues(feature, properties);
            Set<String> extensions = extractExtensions(featureStacProperties);
            Tuple3<IGeometry, BBox, Centroid> geo = extractGeo(feature, configurationAccessor.getGeoJSONReader());
            String collection = extractCollection(feature, properties, featureStacProperties);
            String itemId = feature.getIpId().toString();

            return new Item(
                    extensions,
                    itemId,
                    geo._2,
                    geo._1,
                    geo._3,
                    collection,
                    featureStacProperties,
                    extractLinks(itemId, collection, linkCreator),
                    extractAssets(feature)
            );
        });
    }

    private Map<String, Asset> extractAssets(DataObject feature) {
        return Stream.ofAll(feature.getFeature().getFiles().entries())
            .toMap(entry -> extractAsset(entry.getValue()))
        ;
    }

    private Tuple2<? extends String, ? extends Asset> extractAsset(DataFile value) {
        return Tuple.of(
            value.getChecksum(),
            new Asset(
                value.asUri(),
                value.getFilename(),
                String.format("File size: %d bytes" +
                    "\n\nIs reference: %b" +
                    "\n\nIs online: %b" +
                    "\n\nDatatype: %s" +
                    "\n\nChecksum %s: %s",
                    value.getFilesize(),
                    value.isReference(),
                    value.isOnline(),
                    value.getDataType(),
                    value.getDigestAlgorithm(), value.getChecksum()
                ),
                value.getMimeType().toString(),
                HashSet.of(assetTypeFromDatatype(value.getDataType()))
            )
        );
    }

    private String assetTypeFromDatatype(DataType dataType) {
        switch(dataType){
            case QUICKLOOK_SD: case QUICKLOOK_MD: case QUICKLOOK_HD:
                return Asset.Roles.OVERVIEW;
            case THUMBNAIL:
                return Asset.Roles.THUMBNAIL;
            case DESCRIPTION: case DOCUMENT:
                return Asset.Roles.METADATA;
            default:
                return Asset.Roles.DATA;
        }
    }

    private List<Link> extractLinks(String itemId, String collection, OGCFeatLinkCreator linkCreator) {
        String tenant = runtimeTenantResolver.getTenant();
        return List.of(
                linkCreator.createRootLink()
                        .map(uri -> createLink(uri, Link.Relations.ROOT, "STAC " + tenant + " root")),
                linkCreator.createCollectionLink(collection)
                        .map(uri -> createLink(uri, Link.Relations.COLLECTION, "Parent collection")),
                linkCreator.createItemLink(collection, itemId)
                        .map(uri -> createLink(uri, Link.Relations.SELF, "Self"))
        )
        .flatMap(tl -> tl);
    }

    private Link createLink(URI uri, String rel, String title) {
        return new Link(uri, rel, Asset.MediaType.APPLICATION_JSON, title);
    }

    private String extractCollection(DataObject feature, List<StacProperty> properties, Map<String, Object> stacProperties) {
        return HashSet.ofAll(feature.getTags())
            .filter(tag -> tag.startsWith("URN:"))
            .filter(tag -> tag.contains(":DATASET:"))
            .headOption()
            .getOrElse(() -> extractDynamicCollection(properties, stacProperties));
    }

    private String extractDynamicCollection(List<StacProperty> properties, Map<String, Object> featureStacProperties) {
        return dynamicCollectionService.extractDynamicCollectionName(properties, featureStacProperties);
    }

    private Tuple3<IGeometry, BBox, Centroid> extractGeo(DataObject feature, GeoJSONReader geoJSONReader) {
        Option<IGeometry> geometry = Option.of(feature.getFeature().getGeometry())
                .orElse(() -> Option.of(feature.getFeature().getNormalizedGeometry()));
        Option<BBox> bbox = Option.ofOptional(feature.getFeature().getBbox()).flatMap(this::extractBBox);

        return geometry
            .flatMap(g -> bbox.map(b -> Tuple.of(g, b, b.centroid()))
                .orElse(geoHelper.computeBBoxCentroid(g, geoJSONReader))
            )
            .orElse(bbox.map(bb -> Tuple.of(null, bb, bb.centroid())))
            .getOrElse(() -> Tuple.of(null, null, null));
    }

    private Option<BBox> extractBBox(Double[] doubles) {
        if (doubles.length == 4) {
            return Option.of(new BBox(doubles[0], doubles[1], doubles[2], doubles[3]));
        }
        else {
            return Option.none();
        }
    }

    private Set<String> extractExtensions(Map<String, Object> stacProperties) {
        return stacProperties
            .keySet()
            .flatMap(name -> {
                int colonIndex = name.indexOf(":");
                return colonIndex == -1 ? Option.none() : Option.of(name.substring(0, colonIndex));
            });
    }

    private Map<String, Object> extractStacPropertyKeyValues(DataObject feature, List<StacProperty> properties) {
        return HashSet.ofAll(feature.getFeature().getProperties())
            .flatMap(regardsProp ->
                findCorrespondingStacProperty(regardsProp, properties)
                    .map(sp -> extractPropertyRegardsKeyValue(regardsProp, sp))
            )
            .toMap(kv -> kv);
    }

    private Tuple2<String, Object> extractPropertyRegardsKeyValue(IProperty<?> regardsProp, StacProperty sp) {
        return Tuple.of(sp.getStacPropertyName(), convertRegardsToStacValue(regardsProp, sp));
    }

    @SuppressWarnings("unchecked")
    private Object convertRegardsToStacValue(IProperty<?> regardsProp, StacProperty sp) {
        return sp.getConverter()
            .convertRegardsToStac(regardsProp.getValue())
            .onFailure(t -> LOGGER.warn("Could not convert regards property {}={} to stac property {}",
                    regardsProp.getName(),
                    regardsProp.getValue(),
                    sp.getStacPropertyName())
            )
            .getOrElse(regardsProp.getValue());
    }

    private Option<StacProperty> findCorrespondingStacProperty(IProperty<?> regardsProp, List<StacProperty> properties) {
        return properties.find(sp -> regardsProp.getName().equals(sp.getRegardsPropertyAccessor()));
    }

}
