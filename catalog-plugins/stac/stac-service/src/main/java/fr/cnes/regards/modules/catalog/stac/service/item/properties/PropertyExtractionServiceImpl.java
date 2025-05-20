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

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Asset;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Link;
import fr.cnes.regards.modules.catalog.stac.service.item.extensions.FieldExtension;
import fr.cnes.regards.modules.catalog.stac.service.link.UriParamAdder;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.feature.EntityFeature;
import fr.cnes.regards.modules.indexer.domain.DataFile;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.*;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Objects;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.debug;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.warn;
import static io.vavr.Predicates.isNotNull;
import static java.lang.String.format;

@Service
public class PropertyExtractionServiceImpl implements PropertyExtractionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyExtractionServiceImpl.class);

    private final UriParamAdder uriParamAdder;

    private final Gson gson = new Gson();

    public PropertyExtractionServiceImpl(UriParamAdder uriParamAdder) {
        this.uriParamAdder = uriParamAdder;
    }

    @Override
    public Map<String, Object> extractStacProperties(AbstractEntity<? extends EntityFeature> feature,
                                                     List<StacProperty> stacProperties,
                                                     FieldExtension fieldExtension) {

        // Skip extraction according to field extension
        if (!fieldExtension.isPropertiesIncluded()) {
            return null;
        }

        // Group by namespace (without virtual properties only used for criterion mapping, not for response)
        Map<String, List<StacProperty>> groupedProperties = stacProperties.filter(p -> !p.getVirtual())
                                                                          .groupBy(StacProperty::getStacPropertyNamespace);
        // Get base map
        Map<String, Object> rootMap = extractStacPropertiesByNamespace(feature,
                                                                       Option.none(),
                                                                       groupedProperties.get(null)
                                                                                        .getOrElse(List.empty()),
                                                                       fieldExtension);

        // Add properties with namespaces
        return Try.of(() -> rootMap.merge(groupedProperties.filterKeys(Objects::nonNull)
                                                           .map(ppt -> extractStacPropertiesByNamespace(feature,
                                                                                                        Option.of(ppt._1),
                                                                                                        ppt._2,
                                                                                                        fieldExtension))
                                                           .reduce((i, j) -> i == null ? j : i.merge(j))))
                  .getOrElse(rootMap);
    }

    private Map<String, Object> extractStacPropertiesByNamespace(AbstractEntity<? extends EntityFeature> feature,
                                                                 Option<String> namespace,
                                                                 List<StacProperty> stacProperties,
                                                                 FieldExtension fieldExtension) {
        Map<String, Object> result;
        if (namespace.isDefined()) {
            Map<String, Object> wrapped = stacProperties.filter(sp -> fieldExtension.isPropertyIncluded(sp.getStacPropertyName()))
                                                        .map(sp -> extractStacProperty(feature, sp))
                                                        .toMap(kv -> kv)
                                                        .filterValues(Objects::nonNull);
            result = wrapped.isEmpty() ? HashMap.empty() : HashMap.of(namespace.get(), wrapped);
        } else {
            result = stacProperties.filter(sp -> fieldExtension.isPropertyIncluded(sp.getStacPropertyName()))
                                   .map(sp -> extractStacProperty(feature, sp))
                                   .toMap(kv -> kv);
        }
        return result;
    }

    private Tuple2<String, Object> extractStacProperty(AbstractEntity<? extends EntityFeature> feature,
                                                       StacProperty stacProperty) {

        return Tuple.of(stacProperty.getStacPropertyName(),
                        stacProperty.getRegardsPropertyAccessor()
                                    .getGenericExtractValueFn()
                                    .apply(feature)
                                    .map(val -> convertStacProperty(val, stacProperty))
                                    .getOrNull());
    }

    @SuppressWarnings("unchecked")
    private Object convertStacProperty(Object value, StacProperty sp) {
        return sp.getConverter()
                 .convertRegardsToStac(value)
                 .onFailure(t -> warn(LOGGER,
                                      "Could not convert regards property value {} using stac property {} converter",
                                      value,
                                      sp.getStacPropertyName()))
                 .getOrElse(value);
    }

    @Override
    public Map<String, Asset> extractAssets(AbstractEntity<? extends EntityFeature> feature,
                                            Map<String, Asset> staticFeatureAssets,
                                            FieldExtension fieldExtension) {

        // Skip asset extraction according to file extension
        if (!fieldExtension.isAssetsIncluded()) {
            return null;
        }

        Tuple2<String, String> authParam = uriParamAdder.makeAuthParam();
        Map<String, Asset> nullUnsafe = Stream.ofAll(feature.getFeature().getFiles().entries())
                                              .toMap(entry -> extractAsset(entry.getValue(), authParam))
                                              .merge(staticFeatureAssets);
        return nullUnsafe.filterKeys(isNotNull());
    }

    private Tuple2<String, Asset> extractAsset(DataFile value, Tuple2<String, String> authParam) {
        Tuple2<String, Asset> result = Tuple.of(value.getFilename(),
                                                new Asset(value.getChecksum(),
                                                          value.getDigestAlgorithm(),
                                                          value.getFilesize(),
                                                          authdUri(value.asUri(), authParam),
                                                          value.getFilename(),
                                                          format("File size: %d bytes"
                                                                 + "\n\nIs reference: %b"
                                                                 + "\n\nIs online: %b"
                                                                 + "\n\nDatatype: %s"
                                                                 + "\n\nChecksum %s: %s",
                                                                 value.getFilesize(),
                                                                 value.isReference(),
                                                                 value.isOnline(),
                                                                 value.getDataType(),
                                                                 value.getDigestAlgorithm(),
                                                                 value.getChecksum()),
                                                          value.getMimeType().toString(),
                                                          HashSet.of(Asset.fromDataType(value.getDataType()))));
        debug(LOGGER, "Found asset: \n\tDataFile={} ; \n\tAsset={}", value.getChecksum(), result);
        return result;
    }

    /**
     * @return static feature assets
     */
    @Override
    public Map<String, Asset> extractStaticAssets(AbstractEntity<? extends EntityFeature> feature,
                                                  StacProperty stacAssetsProperty,
                                                  FieldExtension fieldExtension) {

        // Skip asset extraction according to file extension
        if (!fieldExtension.isAssetsIncluded()) {
            return HashMap.empty();
        }

        return Try.of(() -> {
            Object object = stacAssetsProperty.getRegardsPropertyAccessor()
                                              .getGenericExtractValueFn()
                                              .apply(feature)
                                              .getOrNull();
            return HashMap.ofAll(Objects.requireNonNull(extractStaticAssetsFromJson(object)));
        }).getOrElse(HashMap.empty());
    }

    private java.util.Map<String, Asset> extractStaticAssetsFromJson(Object object) {
        if (JsonObject.class.isAssignableFrom(object.getClass())) {
            return gson.fromJson((JsonObject) object, new TypeToken<java.util.Map<String, Asset>>() {

            }.getType());
        }
        return java.util.Collections.emptyMap();
    }

    private URI authdUri(URI uri, Tuple2<String, String> authParam) {
        return Try.success(uri).flatMapTry(uriParamAdder.appendParams(HashMap.of(authParam))).getOrElse(uri);
    }

    /**
     * @return static feature links
     */
    public List<Link> extractStaticLinks(AbstractEntity<? extends EntityFeature> feature,
                                         StacProperty stacLinksProperty,
                                         FieldExtension fieldExtension) {

        // Skip links extraction according to file extension
        if (!fieldExtension.isLinksIncluded()) {
            return List.empty();
        }

        return Try.of(() -> {
            Object object = stacLinksProperty.getRegardsPropertyAccessor()
                                             .getGenericExtractValueFn()
                                             .apply(feature)
                                             .getOrNull();
            return List.ofAll(Objects.requireNonNull(extractStaticLinksFromJson(object)));
        }).getOrElse(List.empty());
    }

    private java.util.List<Link> extractStaticLinksFromJson(Object object) {
        if (JsonArray.class.isAssignableFrom(object.getClass())) {
            return gson.fromJson((JsonArray) object, new TypeToken<java.util.List<Link>>() {

            }.getType());
        }
        return java.util.Collections.emptyList();
    }

    @Override
    public Set<String> extractExtensionsFromConfiguration(List<StacProperty> stacProperties,
                                                          Set<String> internalExtensions,
                                                          FieldExtension fieldExtension) {

        // Skip extensions extraction according to file extension
        if (!fieldExtension.isStacExtensionsIncluded()) {
            return null;
        }

        return Try.of(() -> stacProperties.map(StacProperty::getExtension)
                                          .filter(e -> e != null && !e.isEmpty())
                                          .toSet()).getOrElse(HashSet.empty()).addAll(internalExtensions);
    }
}
