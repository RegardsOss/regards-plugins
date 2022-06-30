/*
 * Copyright 2017-2022 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

import com.google.common.collect.Lists;
import fr.cnes.regards.framework.modules.tinyurl.domain.TinyUrl;
import fr.cnes.regards.framework.modules.tinyurl.service.TinyUrlService;
import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.Context;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.CollectionSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.DownloadPreparationResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.FiltersByCollection;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.SearchCollectionsResponse;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacCollectionProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Asset;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.service.collection.IdMappingService;
import fr.cnes.regards.modules.catalog.stac.service.collection.EsAggregationHelper;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag.EODagParameters;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.configuration.collection.CollectionConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.collection.CollectionConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.criterion.IdentitiesCriterionBuilder;
import fr.cnes.regards.modules.catalog.stac.service.criterion.StacSearchCriterionBuilder;
import fr.cnes.regards.modules.catalog.stac.service.item.properties.PropertyExtractionService;
import fr.cnes.regards.modules.catalog.stac.service.link.DownloadLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.search.AbstractSearchService;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.Dataset;
import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.StringMatchType;
import fr.cnes.regards.modules.indexer.domain.summary.DocFilesSummary;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

import static fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants.PropertyName.ID_PROPERTY_NAME;
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

    @Autowired
    private StacSearchCriterionBuilder searchCriterionBuilder;

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

    @Autowired
    private TinyUrlService tinyUrlService;
    
    @Autowired
    private IdMappingService idMappingService;

    @Override
    public Try<SearchCollectionsResponse> search(CollectionSearchBody collectionSearchBody,
                                                 Integer page,
                                                 SearchPageLinkCreator searchCollectionPageLinkCreator,
                                                 SearchPageLinkCreator searchItemPageLinkCreator) {

        // Retrieve configured collection properties
        CollectionConfigurationAccessor collectionConfigurationAccessor = collectionConfigurationAccessorFactory.makeConfigurationAccessor();
        List<StacProperty> collectionStacProperties = collectionConfigurationAccessor.getStacProperties();
        // Retrieve configured item properties
        ConfigurationAccessor configurationAccessor = configurationAccessorFactory.makeConfigurationAccessor();
        List<StacProperty> itemStacProperties = configurationAccessor.getStacProperties();

        // Build page parameters
        Pageable pageable = pageable(collectionSearchBody.getLimit(),
                                     page,
                                     collectionSearchBody.getSortBy(),
                                     collectionStacProperties);

        // Build item search criteria
        CollectionSearchBody.CollectionItemSearchBody collectionItemSearchBody =
            collectionSearchBody.getItem() == null ?
                CollectionSearchBody.CollectionItemSearchBody.builder().build() :
                collectionSearchBody.getItem();
        ICriterion itemCriteria = searchCriterionBuilder.buildCriterion(itemStacProperties, collectionItemSearchBody)
                                                        .getOrElse(ICriterion.all());
        Option<ICriterion> idCriterion;
        Try<Map<String, Long>> datasetCount = Try.success(null);
        if (!itemCriteria.isEmpty()) {
            // Search for all matching dataset ids using item query parameters
            // We had to filter results as tags not only contain dataset ids
            datasetCount = trying(() -> getDatasetIds(itemCriteria,
                                                      aggregationHelper.getDatasetTotalCount())).mapFailure(
                DATASET_AGGREGATION_FAILURE,
                () -> String.format("Collection search failure on item search filtering with %s",
                                    collectionItemSearchBody));
            // Add it to the collection search criteria
            idCriterion = identitiesCriterionBuilder.buildCriterion(itemStacProperties,
                                                                    List.ofAll(datasetCount.get().keySet()));
            // No dataset matches!
            if (!idCriterion.isDefined()) {
                return extractCollection(new FacetPage<>(new ArrayList<>(), new java.util.HashSet<>(), pageable, 0),
                                         collectionStacProperties,
                                         searchCollectionPageLinkCreator,
                                         searchItemPageLinkCreator,
                                         HashMap.empty(),
                                         collectionConfigurationAccessor);
            }
        } else {
            idCriterion = Option.none();
        }

        // Build collection search criteria
        // possibly incorporating the filter on the collections from the previous search
        ICriterion collectionCriteria = idCriterion.isDefined() ?
            ICriterion.and(idCriterion.get(),
                           searchCriterionBuilder.buildCriterion(collectionStacProperties, collectionSearchBody)
                                                 .getOrElse(ICriterion.all())) :
            searchCriterionBuilder.buildCriterion(collectionStacProperties, collectionSearchBody)
                                  .getOrElse(ICriterion.all());

        // Search for all matching collections
        Map<String, Long> finalDatasetCount = datasetCount.get();
        return trying(() -> catalogSearchService.<Dataset>search(collectionCriteria,
                                                                 SearchType.DATASETS,
                                                                 null,
                                                                 pageable)).mapFailure(SEARCH,
                                                                                       () -> String.format(
                                                                                           "Collection search failure for page %d of %s",
                                                                                           page,
                                                                                           collectionSearchBody))
                                                                           .flatMap(facetPage -> extractCollection(
                                                                               facetPage,
                                                                               collectionStacProperties,
                                                                               searchCollectionPageLinkCreator,
                                                                               searchItemPageLinkCreator,
                                                                               finalDatasetCount,
                                                                               collectionConfigurationAccessor));
    }

    private Map<String, Long> getDatasetIds(ICriterion itemCriteria, Long size) throws AccessRightFilterException {
        Aggregations aggregations = aggregationHelper.getDatasetAggregations(DATASET_AGG_NAME,
                                                                             accessRightFilter.addAccessRights(
                                                                                 itemCriteria),
                                                                             size);
        Terms datasetIdsAgg = aggregations.get(DATASET_AGG_NAME);
        return List.ofAll(datasetIdsAgg.getBuckets())
                   .map(b -> new Tuple2<>(b.getKeyAsString(), b.getDocCount()))
                   .toMap(kv -> kv);
    }

    private Try<SearchCollectionsResponse> extractCollection(FacetPage<Dataset> facetPage,
                                                             List<StacProperty> stacProperties,
                                                             SearchPageLinkCreator searchCollectionPageLinkCreator,
                                                             SearchPageLinkCreator searchItemPageLinkCreator,
                                                             Map<String, Long> datasetCount,
                                                             CollectionConfigurationAccessor collectionConfigurationAccessor) {
        return trying(() -> {
            Context context = new Context(facetPage.getNumberOfElements(),
                                          facetPage.getPageable().getPageSize(),
                                          facetPage.getTotalElements());
            return new SearchCollectionsResponse(HashSet.empty(),
                                                 buildCollections(Stream.ofAll(facetPage.get()),
                                                                  stacProperties,
                                                                  datasetCount,
                                                                  collectionConfigurationAccessor,
                                                                  searchItemPageLinkCreator),
                                                 extractLinks(searchCollectionPageLinkCreator, facetPage),
                                                 context);
        }).mapFailure(COLLECTIONSRESPONSE_CONSTRUCTION, () -> "Failed to build founded collection response");
    }

    private List<Collection> buildCollections(Stream<Dataset> datasetStream,
                                              List<StacProperty> stacProperties,
                                              Map<String, Long> datasetCount,
                                              CollectionConfigurationAccessor collectionConfigurationAccessor,
                                              SearchPageLinkCreator searchItemPageLinkCreator) {
        return datasetStream.flatMap(dataset -> buildFromDataset(dataset,
                                                                 stacProperties,
                                                                 datasetCount,
                                                                 collectionConfigurationAccessor,
                                                                 searchItemPageLinkCreator)).toList();
    }

    private Try<Collection> buildFromDataset(Dataset dataset,
                                             List<StacProperty> stacProperties,
                                             Map<String, Long> datasetCount,
                                             CollectionConfigurationAccessor collectionConfigurationAccessor,
                                             SearchPageLinkCreator searchItemPageLinkCreator) {
        return trying(() -> {

            // Retrieve information from dataset properties
            Map<String, Object> summaries = extractSummaries(dataset,
                                                             collectionConfigurationAccessor.getSummariesProperties());
            Set<String> extensions = extractExtensions(stacProperties);

            return new Collection(StacSpecConstants.Version.STAC_SPEC_VERSION,
                                  extensions,
                                  extractTitle(dataset, collectionConfigurationAccessor.getTitleProperty()),
                                  idMappingService.getStacIdByUrn(dataset.getIpId().toString()),
                                  extractDescription(dataset, collectionConfigurationAccessor.getDescriptionProperty()),
                                  extractLinks(searchItemPageLinkCreator,
                                               dataset,
                                               collectionConfigurationAccessor.getLinksProperty()),
                                  extractKeywords(dataset, collectionConfigurationAccessor.getKeywordsProperty()),
                                  extractLicense(dataset, collectionConfigurationAccessor.getLicenseProperty()),
                                  extractProviders(dataset, collectionConfigurationAccessor.getProvidersProperty()),
                                  extractExtent(dataset,
                                                collectionConfigurationAccessor.getLowerTemporalExtentProperty(),
                                                collectionConfigurationAccessor.getUpperTemporalExtentProperty()),
                                  summaries,
                                  extractAssets(dataset, collectionConfigurationAccessor.getAssetsProperty()),
                                  buildContext(dataset, datasetCount));

        }).mapFailure(COLLECTION_CONSTRUCTION,
                      () -> String.format("Failed to build collection for URN %s", dataset.getIpId()));
    }

    /**
     * @return optional title
     */
    private String extractTitle(Dataset dataset, StacCollectionProperty stacCollectionProperty) {
        return stacCollectionProperty.getRegardsPropertyAccessor()
                                     .getGenericExtractValueFn()
                                     .apply(dataset)
                                     .map(Object::toString)
                                     .getOrNull();
    }

    /**
     * @return required description (fallback to @{@link Dataset#getLabel()})
     */
    private String extractDescription(Dataset dataset, StacCollectionProperty stacCollectionProperty) {
        return stacCollectionProperty.getRegardsPropertyAccessor()
                                     .getGenericExtractValueFn()
                                     .apply(dataset)
                                     .map(Object::toString)
                                     .getOrElse(dataset.getLabel());
    }

    /**
     * @return required links appended with feature ones
     */
    private List<Link> extractLinks(SearchPageLinkCreator searchItemPageLinkCreator,
                                    Dataset dataset,
                                    StacCollectionProperty stacCollectionProperty) {
        return List.of(searchItemPageLinkCreator.createSelfPageLink()
                                                .map(uri -> new Link(uri,
                                                                     Link.Relations.SELF,
                                                                     Asset.MediaType.APPLICATION_JSON,
                                                                     "this search page")))
                   .flatMap(l -> l)
                   .appendAll(propertyExtractionService.extractStaticLinks(dataset,
                                                                           stacCollectionProperty.toStacProperty()));
    }

    /**
     * @return optional keywords
     */
    private List<String> extractKeywords(Dataset dataset, StacCollectionProperty stacCollectionProperty) {
        Option<PropertyType> propertyType = Try.of(() -> stacCollectionProperty.getRegardsPropertyAccessor()
                                                                               .getAttributeModel()
                                                                               .getType()).toOption();
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
        return stacCollectionProperty.getRegardsPropertyAccessor()
                                     .getGenericExtractValueFn()
                                     .apply(dataset)
                                     .map(Object::toString)
                                     .getOrElse(PROPRIETARY_LICENSING);
    }

    /**
     * @return optional providers
     */
    private Object extractProviders(Dataset dataset, StacCollectionProperty stacCollectionProperty) {
        return stacCollectionProperty.getRegardsPropertyAccessor()
                                     .getGenericExtractValueFn()
                                     .apply(dataset)
                                     .getOrNull();
    }

    /**
     * @return required extent
     */
    private Extent extractExtent(Dataset dataset,
                                 StacCollectionProperty lowerTemporalExtent,
                                 StacCollectionProperty upperTemporalExtent) {
        if (lowerTemporalExtent != null && upperTemporalExtent != null) {
            return Extent.maximalExtent()
                         .withTemporal(propertyExtractionService.extractTemporalExtent(dataset,
                                                                                       lowerTemporalExtent,
                                                                                       upperTemporalExtent));
        }
        return Extent.maximalExtent();
    }

    /**
     * @return strongly recommended summaries (but not required)
     */
    private Map<String, Object> extractSummaries(Dataset dataset, List<StacProperty> stacProperties) {
        return propertyExtractionService.extractStacProperties(dataset, stacProperties);
    }

    private Set<String> extractExtensions(List<StacProperty> stacProperties) {
        return propertyExtractionService.extractExtensionsFromConfiguration(stacProperties);
    }

    private Map<String, Asset> extractAssets(Dataset dataset, StacCollectionProperty stacAssetsProperty) {
        return propertyExtractionService.extractAssets(dataset)
                                        .merge(propertyExtractionService.extractStaticAssets(dataset,
                                                                                             stacAssetsProperty.toStacProperty()));
    }

    /**
     * @return collection context
     */
    private Context buildContext(Dataset dataset, Map<String, Long> datasetCount) {
        return new Context(null,
                           null,
                           datasetCount == null ? null : datasetCount.get(dataset.getIpId().toString()).getOrNull());
    }

    @Override
    public Try<DownloadPreparationResponse> prepareZipDownload(FiltersByCollection filtersByCollection,
                                                               DownloadLinkCreator downloadLinkCreator) {
        return trying(() -> {
            // Store all tiny url ids to build global download link
            java.util.List<TinyUrl> tinyUrls = new ArrayList<>();
            // Store all tiny url ids to build global download link for script
            java.util.List<TinyUrl> scriptTinyUrls = new ArrayList<>();

            // Prepare response by collection
            List<DownloadPreparationResponse.DownloadCollectionPreparationResponse> collections = filtersByCollection.getCollections()
                                                                                                                     .flatMap(
                                                                                                                         downloadCollectionPreparationBody -> prepareDownloadCollectionResponse(
                                                                                                                             downloadCollectionPreparationBody,
                                                                                                                             downloadLinkCreator,
                                                                                                                             tinyUrls,
                                                                                                                             scriptTinyUrls))
                                                                                                                     .toList();

            // Compute total
            Long totalSize = 0L, totalItems = 0L, totalFiles = 0L;
            for (DownloadPreparationResponse.DownloadCollectionPreparationResponse c : collections) {
                if (c.getErrors().isEmpty()) {
                    totalSize += c.getSize();
                    totalItems += c.getItems();
                    totalFiles += c.getFiles();
                }
            }

            return new DownloadPreparationResponse(totalSize,
                                                   totalItems,
                                                   totalFiles,
                                                   buildAllCollectionsDownloadLink(downloadLinkCreator, tinyUrls),
                                                   buildAllCollectionsScriptDownloadLink(downloadLinkCreator,
                                                                                         scriptTinyUrls),
                                                   collections);
        }).mapFailure(DOWNLOAD_PREPARATION, () -> "Download preparation failure");
    }

    private Try<DownloadPreparationResponse.DownloadCollectionPreparationResponse> prepareDownloadCollectionResponse(
        FiltersByCollection.CollectionFilters collectionFilters,
        DownloadLinkCreator downloadLinkCreator,
        java.util.List<TinyUrl> tinyUrls,
        java.util.List<TinyUrl> scriptTinyUrls) {

        return Try.of(() -> {

            // Translate collection id to urn
            UniformResourceName datasetUrn = parseCollectionUrn(
                    idMappingService.getUrnByStacId(collectionFilters.getCollectionId())).get();

                      // Retrieve configured item properties
                      ConfigurationAccessor configurationAccessor = configurationAccessorFactory.makeConfigurationAccessor();
                      List<StacProperty> itemStacProperties = configurationAccessor.getStacProperties();

            // Build item search criteria with dataset filter
            CollectionSearchBody.CollectionItemSearchBody collectionItemSearchBody =
                    collectionFilters.getFilters() == null ?
                            CollectionSearchBody.CollectionItemSearchBody.builder().build() :
                            collectionFilters.getFilters();
            // Build item criteria
            ICriterion itemCriteria = ICriterion.and(
                    ICriterion.eq(StaticProperties.FEATURE_TAGS, datasetUrn.toString(), StringMatchType.KEYWORD),
                    searchCriterionBuilder.buildCriterion(itemStacProperties, collectionItemSearchBody)
                            .getOrElse(ICriterion.all()));
            // Transform and store request as EODagParameters
            Option<EODagParameters> eoDagParameters = searchCriterionBuilder.buildEODagParameters(itemStacProperties,
                                                                                                  collectionFilters.getCollectionId(),
                                                                                                  collectionItemSearchBody);

                      // Compute summary and getting first hit
                      DocFilesSummary docFilesSummary = computeSummary(itemCriteria, datasetUrn).get();

                      // Compute information for sample
                      DocFilesSummary sampleDocFilesSummary = getSampleDocFileSummary(itemCriteria, datasetUrn).get();

                      Pair<TinyUrl, URI> tinyUrlURIPair = buildCollectionDownloadLink(collectionFilters.getCollectionId(),
                                                                                      itemCriteria,
                                                                                      downloadLinkCreator,
                                                                                      tinyUrls);

                      return new DownloadPreparationResponse.DownloadCollectionPreparationResponse(collectionFilters.getCollectionId(),
                                                                                                   collectionFilters.getCorrelationId(),
                                                                                                   docFilesSummary.getFilesSize(),
                                                                                                   docFilesSummary.getDocumentsCount(),
                                                                                                   docFilesSummary.getFilesCount(),
                                                                                                   docFilesSummary.getDocumentsCount()
                                                                                                       == 0 ?
                                                                                                       null :
                                                                                                       tinyUrlURIPair.getSecond(),
                                                                                                   buildScriptDownloadLink(
                                                                                                       collectionFilters.getCollectionId(),
                                                                                                       eoDagParameters.get(),
                                                                                                       downloadLinkCreator,
                                                                                                       scriptTinyUrls),
                                                                                                   buildCollectionSample(
                                                                                                       collectionFilters.getCollectionId(),
                                                                                                       tinyUrlURIPair.getFirst(),
                                                                                                       downloadLinkCreator,
                                                                                                       sampleDocFilesSummary.getFilesSize(),
                                                                                                       sampleDocFilesSummary.getFilesCount()),
                                                                                                   docFilesSummary.getDocumentsCount()
                                                                                                       == 0 ?
                                                                                                       List.of("No item found") :
                                                                                                       List.empty());
                  })
                  .recover(throwable -> new DownloadPreparationResponse.DownloadCollectionPreparationResponse(
                      collectionFilters.getCollectionId(),
                      collectionFilters.getCorrelationId(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      List.of(throwable.getMessage())));
    }

    private Try<DocFilesSummary> getSampleDocFileSummary(ICriterion itemCriteria, UniformResourceName datasetUrn) {
        return trying(() -> {
            // Retrieve first item
            FacetPage<DataObject> page = catalogSearchService.search(itemCriteria,
                                                                     SearchType.DATAOBJECTS,
                                                                     null,
                                                                     PageRequest.of(0, 1));
            Optional<DataObject> firstItem = page.stream().findFirst();
            // Compute sample summary if item found
            if (firstItem.isPresent()) {
                ICriterion sampleCriterion = ICriterion.eq(ID_PROPERTY_NAME,
                                                           firstItem.get().getIpId().toString(),
                                                           StringMatchType.KEYWORD);
                return catalogSearchService.computeDatasetsSummary(sampleCriterion,
                                                                   SearchType.DATAOBJECTS,
                                                                   datasetUrn,
                                                                   Lists.newArrayList(DataType.RAWDATA));
            }
            // Else return empty summary
            return new DocFilesSummary();
        }).mapFailure(DOWNLOAD_COLLECTION_SAMPLE_SUMMARY, () -> "Cannot compute sample collection summary");
    }

    private Try<UniformResourceName> parseCollectionUrn(String collectionId) {
        return trying(() -> UniformResourceName.fromString(collectionId)).mapFailure(DOWNLOAD_COLLECTION_ID_PARSING,
                                                                                     () -> "Cannot parse collection id as valid URN");
    }

    private Try<DocFilesSummary> computeSummary(ICriterion itemCriteria, UniformResourceName datasetUrn) {
        return trying(() -> catalogSearchService.computeDatasetsSummary(itemCriteria,
                                                                        SearchType.DATAOBJECTS,
                                                                        datasetUrn,
                                                                        Lists.newArrayList(DataType.RAWDATA))).mapFailure(
            DOWNLOAD_COLLECTION_SUMMARY,
            () -> "Cannot compute collection summary");
    }

    /**
     * Build download link for a single collection
     *
     * @param collectionId        collection urn
     * @param itemCriteria        tiny url context
     * @param downloadLinkCreator build proper link
     * @param tinyUrls            set of tiny URLs
     * @return download link
     */
    private Pair<TinyUrl, URI> buildCollectionDownloadLink(String collectionId,
                                                           ICriterion itemCriteria,
                                                           DownloadLinkCreator downloadLinkCreator,
                                                           java.util.List<TinyUrl> tinyUrls) {
        // Store context
        TinyUrl tinyUrl = tinyUrlService.create(itemCriteria);
        // Mutate set of tiny URLs
        tinyUrls.add(tinyUrl);
        // Build URI
        return Pair.of(tinyUrl,
                       downloadLinkCreator.createSingleCollectionDownloadLink(collectionId, tinyUrl.getUuid()).get());
    }

    private URI buildScriptDownloadLink(String collectionId,
                                        EODagParameters parameters,
                                        DownloadLinkCreator downloadLinkCreator,
                                        java.util.List<TinyUrl> tinyUrls) {
        // Store context
        TinyUrl tinyUrl = tinyUrlService.create(parameters);
        // Mutate set of tiny URLs
        tinyUrls.add(tinyUrl);
        // Build URI
        return downloadLinkCreator.createSingleCollectionScriptLink(collectionId, tinyUrl.getUuid()).get();
    }

    /**
     * @param collectionId        collection urn
     * @param tinyUrl             tiny url taken from collection download link
     * @param downloadLinkCreator build proper link
     * @return sample download link
     */
    private DownloadPreparationResponse.DownloadSamplePreparationResponse buildCollectionSample(String collectionId,
                                                                                                TinyUrl tinyUrl,
                                                                                                DownloadLinkCreator downloadLinkCreator,
                                                                                                Long size,
                                                                                                Long files) {
        return new DownloadPreparationResponse.DownloadSamplePreparationResponse(size,
                                                                                 files,
                                                                                 downloadLinkCreator.createSingleCollectionSampleDownloadLink(
                                                                                     collectionId,
                                                                                     tinyUrl.getUuid()).get());
    }

    /**
     * Build download link for a set of collections
     *
     * @param downloadLinkCreator build proper link
     * @param tinyUrls            set of tiny URL
     * @return download link
     */
    private URI buildAllCollectionsDownloadLink(DownloadLinkCreator downloadLinkCreator,
                                                java.util.List<TinyUrl> tinyUrls) {
        TinyUrl tinyUrl;
        switch (tinyUrls.size()) {
            case 0:
                // Return directly
                return null;
            case 1:
                // Reuse existing one
                tinyUrl = tinyUrls.get(0);
                break;
            default:
                // Store a new context
                java.util.Set<String> tinyUrlUuids = tinyUrls.stream()
                                                             .map(TinyUrl::getUuid)
                                                             .collect(Collectors.toSet());
                tinyUrl = tinyUrlService.create(tinyUrlUuids);
                break;
        }
        // Build URI
        return downloadLinkCreator.createAllCollectionsDownloadLink(tinyUrl.getUuid()).get();
    }

    /**
     * Build script download link for a set of collections
     *
     * @param downloadLinkCreator build proper link
     * @param tinyUrls            set of tiny URL
     * @return script download link
     */
    private URI buildAllCollectionsScriptDownloadLink(DownloadLinkCreator downloadLinkCreator,
                                                      java.util.List<TinyUrl> tinyUrls) {
        TinyUrl tinyUrl;
        switch (tinyUrls.size()) {
            case 0:
                // Return directly
                return null;
            case 1:
                // Reuse existing one
                tinyUrl = tinyUrls.get(0);
                break;
            default:
                // Store a new context
                java.util.Set<String> tinyUrlUuids = tinyUrls.stream()
                                                             .map(TinyUrl::getUuid)
                                                             .collect(Collectors.toSet());
                tinyUrl = tinyUrlService.create(tinyUrlUuids);
                break;
        }
        // Build URI
        return downloadLinkCreator.createAllCollectionsScriptLink(tinyUrl.getUuid()).get();
    }
}
