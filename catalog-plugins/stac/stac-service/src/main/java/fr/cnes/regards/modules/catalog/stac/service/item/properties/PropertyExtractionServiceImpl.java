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

import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Asset;
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

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.debug;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.warn;
import static io.vavr.Predicates.isNotNull;
import static java.lang.String.format;

@Service
public class PropertyExtractionServiceImpl implements PropertyExtractionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyExtractionServiceImpl.class);

    private final UriParamAdder uriParamAdder;

    public PropertyExtractionServiceImpl(UriParamAdder uriParamAdder) {
        this.uriParamAdder = uriParamAdder;
    }

    @Override
    public Map<String, Object> extractStacProperties(AbstractEntity<? extends EntityFeature> feature,
            List<StacProperty> stacProperties) {
        // Group by namespace
        Map<String, List<StacProperty>> groupedProperties = stacProperties.groupBy(s -> s.getStacPropertyNamespace());
        // Get base map
        Map<String, Object> rootMap = extractStacPropertiesByNamespace(feature, Option.none(),
                                                                       groupedProperties.get(null).getOrElse(List.empty()));

        // Add namespaced properties
        return Try.of(() -> rootMap.merge(groupedProperties.filterKeys(k -> k != null)
                                                  .map(ppt -> extractStacPropertiesByNamespace(feature,
                                                                                               Option.of(ppt._1),
                                                                                               ppt._2))
                                                  .reduce((i, j) -> i == null ? j : i.merge(j)))).getOrElse(rootMap);
    }

    private Map<String, Object> extractStacPropertiesByNamespace(AbstractEntity<? extends EntityFeature> feature,
            Option<String> namespace, List<StacProperty> stacProperties) {
        Map<String, Object> result;
        if (namespace.isDefined()) {
            Map<String, Object> wrapped = stacProperties.map(sp -> extractStacProperty(feature, sp)).toMap(kv -> kv)
                    .filterValues(v -> v != null);
            result = wrapped.isEmpty() ? HashMap.empty() : HashMap.of(namespace.get(), wrapped);
        } else {
            result = stacProperties.map(sp -> extractStacProperty(feature, sp)).toMap(kv -> kv);
        }
        return result;
    }

    private Tuple2<String, Object> extractStacProperty(AbstractEntity<? extends EntityFeature> feature,
            StacProperty stacProperty) {
        Tuple2<String, Object> tuple2 = Tuple.of(stacProperty.getStacPropertyName(),
                                                 stacProperty.getRegardsPropertyAccessor().getGenericExtractValueFn()
                                                         .apply(feature)
                                                         .map(val -> convertStacProperty(val, stacProperty))
                                                         .getOrNull());
        return tuple2;
    }

    @SuppressWarnings("unchecked")
    private Object convertStacProperty(Object value, StacProperty sp) {
        return sp.getConverter().convertRegardsToStac(value).onFailure(
                t -> warn(LOGGER, "Could not convert regards property value {} using stac property {} converter", value,
                          sp.getStacPropertyName())).getOrElse(value);
    }

    @Override
    public Map<String, Asset> extractAssets(AbstractEntity<? extends EntityFeature> feature) {
        Tuple2<String, String> authParam = uriParamAdder.makeAuthParam();
        Map<String, Asset> nullUnsafe = Stream.ofAll(feature.getFeature().getFiles().entries())
                .toMap(entry -> extractAsset(entry.getValue(), authParam));
        return nullUnsafe.filterKeys(isNotNull());
    }

    private Tuple2<String, Asset> extractAsset(DataFile value, Tuple2<String, String> authParam) {
        Tuple2<String, Asset> result = Tuple.of(value.getFilename(),
                                                new Asset(authdUri(value.asUri(), authParam), value.getFilename(),
                                                          format("File size: %d bytes" + "\n\nIs reference: %b"
                                                                         + "\n\nIs online: %b" + "\n\nDatatype: %s"
                                                                         + "\n\nChecksum %s: %s", value.getFilesize(),
                                                                 value.isReference(), value.isOnline(),
                                                                 value.getDataType(), value.getDigestAlgorithm(),
                                                                 value.getChecksum()), value.getMimeType().toString(),
                                                          HashSet.of(assetTypeFromDatatype(value.getDataType()))));
        debug(LOGGER, "Found asset: \n\tDataFile={} ; \n\tAsset={}", value.getChecksum(), result);
        return result;
    }

    private URI authdUri(URI uri, Tuple2<String, String> authParam) {
        return Try.success(uri).flatMapTry(uriParamAdder.appendParams(HashMap.of(authParam))).getOrElse(uri);
    }

    private String assetTypeFromDatatype(DataType dataType) {
        switch (dataType) {
            case QUICKLOOK_SD:
            case QUICKLOOK_MD:
            case QUICKLOOK_HD:
                return Asset.Roles.OVERVIEW;
            case THUMBNAIL:
                return Asset.Roles.THUMBNAIL;
            case DESCRIPTION:
            case DOCUMENT:
                return Asset.Roles.METADATA;
            default:
                return Asset.Roles.DATA;
        }
    }

    public Set<String> extractExtensions(Map<String, Object> stacProperties) {
        return stacProperties.keySet().flatMap(name -> {
            int colonIndex = name.indexOf(":");
            return colonIndex == -1 ? Option.none() : Option.of(name.substring(0, colonIndex));
        });
    }
}
