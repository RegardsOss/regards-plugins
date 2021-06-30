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
package fr.cnes.regards.modules.catalog.stac.service.collection.search;

import fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.Context;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.CollectionSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.SearchCollectionsResponse;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacCollectionProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Asset;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.service.collection.EsAggregationHelper;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.configuration.collection.CollectionConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.collection.CollectionConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.criterion.IdentitiesCriterionBuilder;
import fr.cnes.regards.modules.catalog.stac.service.criterion.StacSearchCriterionBuilder;
import fr.cnes.regards.modules.catalog.stac.service.item.properties.PropertyExtractionService;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.search.AbstractSearchService;
import fr.cnes.regards.modules.dam.domain.entities.Dataset;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.ICatalogSearchService;
import fr.cnes.regards.modules.search.service.accessright.AccessRightFilterException;
import fr.cnes.regards.modules.search.service.accessright.IAccessRightFilter;
import io.vavr.Tuple2;
import io.vavr.collection.*;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.*;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;

/**
 * Implementation of {@link CollectionSearchService}
 *
 * @author Marc SORDI
 */
@Service
public class CollectionSearchServiceImpl extends AbstractSearchService implements CollectionSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionSearchServiceImpl.class);

    private static final String DATASET_AGG_NAME = "datasetIds";

    private static final String PROPRIETARY_LICENSING = "proprietary";

    // FIXME rendre configurable
    private static final int DATASET_AGG_SIZE = 500;

    @Autowired
    private StacSearchCriterionBuilder critBuilder;

    @Autowired
    private ICatalogSearchService catalogSearchService;

    @Autowired
    private ConfigurationAccessorFactory configurationAccessorFactory;

    @Autowired
    private CollectionConfigurationAccessorFactory collectionConfigurationAccessorFactory;

    @Autowired
    private IdentitiesCriterionBuilder identitiesCriterionBuilder;

    @Autowired
    private EsAggregationHelper aggregationHelper;

    @Autowired
    private IAccessRightFilter accessRightFilter;

    @Autowired
    private PropertyExtractionService propertyExtractionService;

    @Override
    public Try<SearchCollectionsResponse> search(CollectionSearchBody collectionSearchBody, Integer page,
            SearchPageLinkCreator searchCollectionPageLinkCreator, SearchPageLinkCreator searchItemPageLinkCreator) {

        // Retrieve configured collection properties
        CollectionConfigurationAccessor collectionConfigurationAccessor = collectionConfigurationAccessorFactory
                .makeConfigurationAccessor();
        List<StacProperty> collectionStacProperties = collectionConfigurationAccessor.getStacProperties();
        // Retrieve configured item properties
        ConfigurationAccessor configurationAccessor = configurationAccessorFactory.makeConfigurationAccessor();
        List<StacProperty> itemStacProperties = configurationAccessor.getStacProperties();

        // Build page parameters
        Pageable pageable = pageable(collectionSearchBody.getLimit(), page, collectionSearchBody.getSortBy(),
                                     collectionStacProperties);

        // Build item search criteria
        CollectionSearchBody.CollectionItemSearchBody collectionItemSearchBody =
                collectionSearchBody.getItem() == null ?
                        CollectionSearchBody.CollectionItemSearchBody.builder().build() :
                        collectionSearchBody.getItem();
        ICriterion itemCriteria = critBuilder.buildCriterion(itemStacProperties, collectionItemSearchBody)
                .getOrElse(ICriterion.all());
        Option<ICriterion> idCriterion;
        Try<Map<String, Long>> datasetCount = Try.success(null);
        if (!itemCriteria.isEmpty()) {
            // Search for all matching dataset ids using item query parameters
            // We had to filter results as tags not only contain dataset ids
            datasetCount = trying(() -> getDatasetIds(itemCriteria, DATASET_AGG_SIZE))
                    .mapFailure(DATASET_AGGREGATION_FAILURE, () -> String
                            .format("Collection search failure on item search filtering with %s",
                                    collectionItemSearchBody));
            // Add it to the collection search criteria
            idCriterion = identitiesCriterionBuilder
                    .buildCriterion(itemStacProperties, List.ofAll(datasetCount.get().keySet()));
        } else {
            idCriterion = Option.none();
        }

        // Build collection search criteria
        // possibly incorporating the filter on the collections from the previous search
        ICriterion collectionCriteria = idCriterion.isDefined() ?
                ICriterion.and(idCriterion.get(),
                               critBuilder.buildCriterion(collectionStacProperties, collectionSearchBody)
                                       .getOrElse(ICriterion.all())) :
                critBuilder.buildCriterion(collectionStacProperties, collectionSearchBody).getOrElse(ICriterion.all());

        // Search for all matching collections
        Map<String, Long> finalDatasetCount = datasetCount.get();
        return trying(
                () -> catalogSearchService.<Dataset>search(collectionCriteria, SearchType.DATASETS, null, pageable))
                .mapFailure(SEARCH, () -> String
                        .format("Collection search failure for page %d of %s", page, collectionSearchBody)).flatMap(
                        facetPage -> extractCollection(facetPage, itemStacProperties, null,
                                                       searchCollectionPageLinkCreator, searchItemPageLinkCreator,
                                                       finalDatasetCount, collectionConfigurationAccessor));
    }

    private Map<String, Long> getDatasetIds(ICriterion itemCriteria, int size) throws AccessRightFilterException {
        Aggregations aggregations = aggregationHelper
                .getDatasetAggregations(DATASET_AGG_NAME, accessRightFilter.addAccessRights(itemCriteria), size);
        Terms datasetIdsAgg = aggregations.get(DATASET_AGG_NAME);
        return List.ofAll(datasetIdsAgg.getBuckets()).map(b -> new Tuple2<>(b.getKeyAsString(), b.getDocCount()))
                .toMap(kv -> kv);
    }

    private Try<SearchCollectionsResponse> extractCollection(FacetPage<Dataset> facetPage,
            List<StacProperty> stacProperties, OGCFeatLinkCreator featLinkCreator,
            SearchPageLinkCreator searchCollectionPageLinkCreator, SearchPageLinkCreator searchItemPageLinkCreator,
            Map<String, Long> datasetCount, CollectionConfigurationAccessor collectionConfigurationAccessor) {
        return trying(() -> {
            Context context = new Context(facetPage.getNumberOfElements(), facetPage.getPageable().getPageSize(),
                                          facetPage.getTotalElements());
            SearchCollectionsResponse response = new SearchCollectionsResponse(HashSet.empty(), buildCollections(
                    Stream.ofAll(facetPage.get()), stacProperties, datasetCount, collectionConfigurationAccessor,
                    searchItemPageLinkCreator), extractLinks(searchCollectionPageLinkCreator, facetPage), context);
            return response;
        }).mapFailure(COLLECTIONSRESPONSE_CONSTRUCTION, () -> "Failed to build founded collection response");
    }

    private List<Collection> buildCollections(Stream<Dataset> datasetStream, List<StacProperty> stacProperties,
            Map<String, Long> datasetCount, CollectionConfigurationAccessor collectionConfigurationAccessor,
            SearchPageLinkCreator searchItemPageLinkCreator) {
        return datasetStream.flatMap(
                dataset -> buidFromDataset(dataset, stacProperties, datasetCount, collectionConfigurationAccessor,
                                           searchItemPageLinkCreator)).toList();
    }

    private Try<Collection> buidFromDataset(Dataset dataset, List<StacProperty> stacProperties,
            Map<String, Long> datasetCount, CollectionConfigurationAccessor collectionConfigurationAccessor,
            SearchPageLinkCreator searchItemPageLinkCreator) {
        return trying(() -> {

            // Retrieve information from dataset properties
            Map<String, Object> summaries = extractSummaries(dataset,
                                                             collectionConfigurationAccessor.getSummariesProperties());
            Set<String> extensions = extractExtensions(summaries);

            return new Collection(StacSpecConstants.Version.STAC_SPEC_VERSION, extensions,
                                  extractTitle(dataset, collectionConfigurationAccessor.getTitleProperty()),
                                  dataset.getIpId().toString(),
                                  extractDescription(dataset, collectionConfigurationAccessor.getDescriptionProperty()),
                                  extractItemLinks(searchItemPageLinkCreator),
                                  extractKeywords(dataset, collectionConfigurationAccessor.getKeywordsProperty()),
                                  extractLicense(dataset, collectionConfigurationAccessor.getLicenseProperty()),
                                  extractProviders(dataset, collectionConfigurationAccessor.getProvidersProperty()),
                                  extractExtent(), summaries, extractAssets(dataset),
                                  buildContext(dataset, datasetCount));

        }).mapFailure(COLLECTION_CONSTRUCTION,
                      () -> String.format("Failed to build collection for URN %s", dataset.getIpId()));
    }

    /**
     * @return optional title
     */
    private String extractTitle(Dataset dataset, StacCollectionProperty stacCollectionProperty) {
        return stacCollectionProperty.getRegardsPropertyAccessor().getGenericExtractValueFn().apply(dataset)
                .map(v -> v.toString()).getOrNull();
    }

    /**
     * @return required description (fallback to @{@link Dataset#getLabel()})
     */
    private String extractDescription(Dataset dataset, StacCollectionProperty stacCollectionProperty) {
        return stacCollectionProperty.getRegardsPropertyAccessor().getGenericExtractValueFn().apply(dataset)
                .map(v -> v.toString()).getOrElse(dataset.getLabel());
    }

    /**
     * @return required links
     */
    private List<Link> extractItemLinks(SearchPageLinkCreator searchItemPageLinkCreator) {
        return List.of(searchItemPageLinkCreator.createSelfPageLink()
                               .map(uri -> new Link(uri, Link.Relations.SELF, Asset.MediaType.APPLICATION_JSON,
                                                    "this search page"))).flatMap(l -> l);
    }

    /**
     * @return optional keywords
     */
    private List<String> extractKeywords(Dataset dataset, StacCollectionProperty stacCollectionProperty) {
        Option<PropertyType> propertyType = Try
                .of(() -> stacCollectionProperty.getRegardsPropertyAccessor().getAttributeModel().getType()).toOption();
        return stacCollectionProperty.getRegardsPropertyAccessor().getGenericExtractValueFn().apply(dataset).map(o -> {
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
    private String extractLicense(Dataset dataset, StacCollectionProperty stacCollectionProperty) {
        return stacCollectionProperty.getRegardsPropertyAccessor().getGenericExtractValueFn().apply(dataset)
                .map(v -> v.toString()).getOrElse(PROPRIETARY_LICENSING);
    }

    /**
     * @return optional providers
     */
    private Object extractProviders(Dataset dataset, StacCollectionProperty stacCollectionProperty) {
        return stacCollectionProperty.getRegardsPropertyAccessor().getGenericExtractValueFn().apply(dataset)
                .getOrNull();
    }

    /**
     * @return required extent
     */
    private Extent extractExtent() {
        // FIXME à calculer à la volée pour précision max ... mais peut-être couteux en temps et pas utile! A voir!
        return Extent.maximalExtent();
    }

    /**
     * @return strongly recommended summaries (but not required)
     */
    private Map<String, Object> extractSummaries(Dataset dataset, List<StacProperty> stacProperties) {
        return propertyExtractionService.extractStacProperties(dataset, stacProperties);
    }

    private Set<String> extractExtensions(Map<String, Object> stacProperties) {
        return propertyExtractionService.extractExtensions(stacProperties);
    }

    private Map<String, Asset> extractAssets(Dataset dataset) {
        return propertyExtractionService.extractAssets(dataset);
    }

    /**
     * @return collection context
     */
    private Context buildContext(Dataset dataset, Map<String, Long> datasetCount) {
        return new Context(null, null,
                           datasetCount == null ? null : datasetCount.get(dataset.getIpId().toString()).getOrNull());
    }
}
