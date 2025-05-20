/*
 * Copyright 2017-2025 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.stac.service.collection.common;

import fr.cnes.regards.modules.catalog.stac.domain.StacConstants;
import fr.cnes.regards.modules.catalog.stac.domain.api.Context;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacCollectionProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Asset;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Link;
import fr.cnes.regards.modules.catalog.stac.domain.utils.OffsetDatetimeUtils;
import fr.cnes.regards.modules.catalog.stac.service.collection.IdMappingService;
import fr.cnes.regards.modules.catalog.stac.service.configuration.collection.CollectionConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.item.properties.PropertyExtractionService;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.Dataset;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import fr.cnes.regards.modules.opensearch.service.exception.OpenSearchUnknownParameter;
import fr.cnes.regards.modules.search.service.SearchException;
import io.vavr.Tuple;
import io.vavr.collection.*;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.COLLECTION_CONSTRUCTION;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;

/**
 * This collection mapper allows to map internal properties to STAC ones.
 *
 * @author Marc SORDI
 */
@Service
public class CollectionMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionMapper.class);

    private static final String PROPRIETARY_LICENSING = "proprietary";

    private final IdMappingService idMappingService;

    private final PropertyExtractionService propertyExtractionService;

    private final CollectionLinksMapper collectionLinksMapper;

    public CollectionMapper(IdMappingService idMappingService,
                            PropertyExtractionService propertyExtractionService,
                            CollectionLinksMapper collectionLinksMapper) {
        this.idMappingService = idMappingService;
        this.propertyExtractionService = propertyExtractionService;
        this.collectionLinksMapper = collectionLinksMapper;
    }

    public List<Collection> buildCollections(Stream<AbstractEntity<?>> stream,
                                             List<StacProperty> stacProperties,
                                             OGCFeatLinkCreator ogcFeatLinkCreator,
                                             Map<String, Long> datasetCount,
                                             CollectionConfigurationAccessor collectionConfigurationAccessor,
                                             boolean disableParentDiscovery) {
        return stream.flatMap(entity -> buildFromEntity(entity,
                                                        stacProperties,
                                                        ogcFeatLinkCreator,
                                                        datasetCount,
                                                        collectionConfigurationAccessor,
                                                        disableParentDiscovery)).toList();
    }

    public Try<Collection> buildFromEntity(AbstractEntity<?> entity,
                                           List<StacProperty> stacProperties,
                                           OGCFeatLinkCreator ogcFeatLinkCreator,
                                           Map<String, Long> datasetCount,
                                           CollectionConfigurationAccessor collectionConfigurationAccessor,
                                           boolean disableParentDiscovery) {
        return trying(() -> {

            // Retrieve information from entity properties
            Map<String, Object> summaries = extractSummaries(entity,
                                                             collectionConfigurationAccessor.getSummariesProperties());
            Set<String> extensions = extractExtensions(stacProperties);

            return new Collection(StacConstants.STAC_SPEC_VERSION,
                                  extensions,
                                  idMappingService.getStacIdByUrn(entity.getIpId().toString()),
                                  extractTitle(entity, collectionConfigurationAccessor.getTitleProperty()),
                                  extractDescription(entity, collectionConfigurationAccessor.getDescriptionProperty()),
                                  extractKeywords(entity, collectionConfigurationAccessor.getKeywordsProperty()),
                                  extractLicense(entity, collectionConfigurationAccessor.getLicenseProperty()),
                                  extractProviders(entity, collectionConfigurationAccessor.getProvidersProperty()),
                                  extractExtent(entity,
                                                collectionConfigurationAccessor.getLowerTemporalExtentProperty(),
                                                collectionConfigurationAccessor.getUpperTemporalExtentProperty()),
                                  summaries,
                                  extractLinks(ogcFeatLinkCreator,
                                               entity,
                                               collectionConfigurationAccessor.getLinksProperty(),
                                               disableParentDiscovery),
                                  extractAssets(entity, collectionConfigurationAccessor.getAssetsProperty()),
                                  buildContext(entity, datasetCount),
                                  datasetCount == null ?
                                      null :
                                      datasetCount.get(entity.getIpId().toString()).getOrNull());

        }).mapFailure(COLLECTION_CONSTRUCTION,
                      () -> String.format("Failed to build collection for URN %s", entity.getIpId()));
    }

    /**
     * @return strongly recommended summaries (but not required)
     */
    private Map<String, Object> extractSummaries(AbstractEntity<?> entity, List<StacProperty> stacProperties) {
        return propertyExtractionService.extractStacProperties(entity, stacProperties);
    }

    private Set<String> extractExtensions(List<StacProperty> stacProperties) {
        return propertyExtractionService.extractExtensionsFromConfiguration(stacProperties, HashSet.empty());
    }

    /**
     * @return optional title
     */
    private String extractTitle(AbstractEntity<?> entity, StacCollectionProperty stacCollectionProperty) {
        return stacCollectionProperty.getRegardsPropertyAccessor()
                                     .getGenericExtractValueFn()
                                     .apply(entity)
                                     .map(Object::toString)
                                     .getOrNull();
    }

    /**
     * @return required description (fallback to @{@link Dataset#getLabel()})
     */
    private String extractDescription(AbstractEntity<?> entity, StacCollectionProperty stacCollectionProperty) {
        return stacCollectionProperty.getRegardsPropertyAccessor()
                                     .getGenericExtractValueFn()
                                     .apply(entity)
                                     .map(Object::toString)
                                     .getOrElse(entity.getLabel());
    }

    /**
     * @return optional keywords
     */
    private List<String> extractKeywords(AbstractEntity<?> entity, StacCollectionProperty stacCollectionProperty) {
        Option<PropertyType> propertyType = Try.of(() -> stacCollectionProperty.getRegardsPropertyAccessor()
                                                                               .getAttributeModel()
                                                                               .getType()).toOption();
        return stacCollectionProperty.getRegardsPropertyAccessor().getGenericExtractValueFn().apply(entity).map(o -> {
            LOGGER.debug("Property type : {}", propertyType);
            if (propertyType.isDefined()) {
                switch (propertyType.get()) {
                    case STRING:
                        return List.of((String) o);
                    case STRING_ARRAY:
                        return List.of((String[]) o);
                    default:
                        // Skip
                }
            }
            return List.<String>empty();
        }).getOrNull();
    }

    /**
     * @return required license (fallback to PROPRIETARY_LICENSING)
     */
    private String extractLicense(AbstractEntity<?> entity, StacCollectionProperty stacCollectionProperty) {
        return stacCollectionProperty.getRegardsPropertyAccessor()
                                     .getGenericExtractValueFn()
                                     .apply(entity)
                                     .map(Object::toString)
                                     .getOrElse(PROPRIETARY_LICENSING);
    }

    /**
     * @return optional providers
     */
    private Object extractProviders(AbstractEntity<?> entity, StacCollectionProperty stacCollectionProperty) {
        return stacCollectionProperty.getRegardsPropertyAccessor().getGenericExtractValueFn().apply(entity).getOrNull();
    }

    /**
     * @return required extent
     */
    private Extent extractExtent(AbstractEntity<?> entity,
                                 StacCollectionProperty lowerTemporalExtent,
                                 StacCollectionProperty upperTemporalExtent) {
        if (lowerTemporalExtent != null && upperTemporalExtent != null) {
            return Extent.maximalExtent()
                         .withTemporal(extractTemporalExtent(entity, lowerTemporalExtent, upperTemporalExtent));
        }
        return Extent.maximalExtent();
    }

    private Extent.Temporal extractTemporalExtent(AbstractEntity<?> entity,
                                                  StacCollectionProperty lowerTemporalExtent,
                                                  StacCollectionProperty upperTemporalExtent) {
        return new Extent.Temporal(List.of(Tuple.of(getTemporalExtentBound(entity, lowerTemporalExtent).getOrElse(
                                                        OffsetDatetimeUtils.lowestBound()),
                                                    getTemporalExtentBound(entity, upperTemporalExtent).getOrElse(
                                                        OffsetDatetimeUtils.upperBound()))));
    }

    private Try<OffsetDateTime> getTemporalExtentBound(AbstractEntity<?> entity,
                                                       StacCollectionProperty temporalExtentBound) {
        return Try.of(() -> {
            Try<?> bound = temporalExtentBound.getRegardsPropertyAccessor().getGenericExtractValueFn().apply(entity);
            return bound.map(b -> (OffsetDateTime) b).getOrNull();
        });
    }

    /**
     * @return required links appended with feature ones
     */
    private List<Link> extractLinks(OGCFeatLinkCreator linkCreator,
                                    AbstractEntity<?> entity,
                                    StacCollectionProperty stacCollectionProperty,
                                    boolean disableParentDiscovery) throws SearchException, OpenSearchUnknownParameter {
        return collectionLinksMapper.getLinks(entity.getIpId(),
                                              linkCreator,
                                              entity.getProviderId(),
                                              disableParentDiscovery)
                                    .appendAll(propertyExtractionService.extractStaticLinks(entity,
                                                                                            stacCollectionProperty.toStacProperty()));
    }

    private Map<String, Asset> extractAssets(AbstractEntity<?> entity, StacCollectionProperty stacAssetsProperty) {
        return propertyExtractionService.extractAssets(entity, HashMap.empty())
                                        .merge(propertyExtractionService.extractStaticAssets(entity,
                                                                                             stacAssetsProperty.toStacProperty()));
    }

    /**
     * This context will be deprecated in the future.
     *
     * @return collection context
     */
    private Context buildContext(AbstractEntity<?> entity, Map<String, Long> datasetCount) {
        return new Context(null,
                           null,
                           datasetCount == null ? null : datasetCount.get(entity.getIpId().toString()).getOrNull());
    }
}
