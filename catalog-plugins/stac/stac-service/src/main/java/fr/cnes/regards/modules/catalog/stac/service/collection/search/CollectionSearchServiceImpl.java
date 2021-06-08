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
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.CollectionsResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.CollectionSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.criterion.IdentitiesCriterionBuilder;
import fr.cnes.regards.modules.catalog.stac.service.criterion.StacSearchCriterionBuilder;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.search.AbstractSearchService;
import fr.cnes.regards.modules.dam.domain.entities.Dataset;
import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.opensearch.service.exception.OpenSearchUnknownParameter;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.ICatalogSearchService;
import fr.cnes.regards.modules.search.service.SearchException;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.collection.TreeSet;
import io.vavr.control.Option;
import io.vavr.control.Try;
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

    /**
     * Prefix to identify dataset tags
     */
    private static final String DATASET_PREFIX = "URN:AIP:DATASET";

    @Autowired
    private StacSearchCriterionBuilder critBuilder;

    @Autowired
    private ICatalogSearchService catalogSearchService;

    @Autowired
    private ConfigurationAccessorFactory configurationAccessorFactory;

    @Autowired
    private IdentitiesCriterionBuilder identitiesCriterionBuilder;

    // FIXME remove exception ... use trying
    @Override
    public Try<CollectionsResponse> search(CollectionSearchBody collectionSearchBody, Integer page)
            throws SearchException, OpenSearchUnknownParameter {
        // Retrieve configured STAC properties
        List<StacProperty> stacProperties = configurationAccessorFactory.makeConfigurationAccessor()
                .getStacProperties();
        // Build page parameters
        Pageable pageable = pageable(collectionSearchBody.getLimit(), page, collectionSearchBody.getSortBy(),
                                     stacProperties);

        // Build item search criteria
        CollectionSearchBody.CollectionItemSearchBody collectionItemSearchBody =
                collectionSearchBody.getItem() == null ?
                        CollectionSearchBody.CollectionItemSearchBody.builder().build() :
                        collectionSearchBody.getItem();
        ICriterion itemCriteria = critBuilder.buildCriterion(stacProperties, collectionItemSearchBody)
                .getOrElse(ICriterion.all());
        Option<ICriterion> idCriterion;
        if (!itemCriteria.isEmpty()) {
            // Search for all matching dataset ids using item query parameters
            // We had to filter results as tags not only contain dataset ids
            List<String> matchingTags = catalogSearchService
                    .retrieveEnumeratedPropertyValues(itemCriteria, SearchType.DATAOBJECTS,
                                                      StaticProperties.FEATURE_TAGS, 500, DATASET_PREFIX).stream()
                    .filter(t -> t.startsWith(DATASET_PREFIX)).collect(TreeSet.collector()).toList();
            // Add it to the collection search criteria
            idCriterion = identitiesCriterionBuilder.buildCriterion(stacProperties, matchingTags);
        } else {
            idCriterion = Option.none();
        }

        // Build collection search criteria
        // possibly incorporating the filter on the collections from the previous search
        ICriterion collectionCriteria = idCriterion.isDefined() ?
                ICriterion.and(idCriterion.get(), critBuilder.buildCriterion(stacProperties, collectionSearchBody)
                        .getOrElse(ICriterion.all())) :
                critBuilder.buildCriterion(stacProperties, collectionSearchBody).getOrElse(ICriterion.all());

        // Search for all matching collections
        return trying(
                () -> catalogSearchService.<Dataset>search(collectionCriteria, SearchType.DATASETS, null, pageable))
                .mapFailure(SEARCH, () -> String.format("Search failure for page %d of %s", page, collectionSearchBody))
                .flatMap(facetPage -> extractCollection(facetPage, stacProperties, null, null));
        // FIXME set link creator according to expected standard on collection response
    }

    private Try<CollectionsResponse> extractCollection(FacetPage<Dataset> facetPage, List<StacProperty> stacProperties,
            OGCFeatLinkCreator featLinkCreator, SearchPageLinkCreator searchPageLinkCreator) {
        return trying(() -> new CollectionsResponse(buildCollectionLinks(),
                                                    buildCollections(Stream.ofAll(facetPage.get()), stacProperties)))
                .mapFailure(COLLECTIONSRESPONSE_CONSTRUCTION, () -> "Failed to build founded collection response");

        //        return trying(() -> {
        //
        //
        //            ItemCollectionResponse.Context context = new ItemCollectionResponse.Context(facetPage.getNumberOfElements(),
        //                                                                                        facetPage.getPageable()
        //                                                                                                .getPageSize(),
        //                                                                                        facetPage.getTotalElements());
        //            ItemCollectionResponse response = new ItemCollectionResponse(SEARCH_EXTENSIONS,
        //                                                                         extractStacItems(Stream.ofAll(facetPage.get()),
        //                                                                                          stacProperties,
        //                                                                                          featLinkCreator),
        //                                                                         List.empty(), // resolved later
        //                                                                         context);
        //            return response.withLinks(extractLinks(searchPageLinkCreator, response));
        //        }).mapFailure(ITEMCOLLECTIONRESPONSE_CONSTRUCTION, () -> "Failed to create ItemCollectionResponse");
    }

    private List<Link> buildCollectionLinks() {
        return null;
    }

    private List<Collection> buildCollections(Stream<Dataset> datasetStream, List<StacProperty> stacProperties) {
        return datasetStream.flatMap(dataset -> buidFromDataset(dataset, stacProperties)).toList();
    }

    //    private Collection buildFromDataset(Dataset dataset) {
    //        return new Collection(StacSpecConstants.Version.STAC_SPEC_VERSION, List.empty(), name, DEFAULT_STATIC_ID,
    //                              "Static collections", staticCollectionLinks(linkCreator), List.empty(), "", List.empty(),
    //                              Extent.maximalExtent(), // no extent at this level
    //                              HashMap.empty() // no summaries at this level
    //        );
    //    }

    private Try<Collection> buidFromDataset(Dataset dataset, List<StacProperty> stacProperties) {
        return trying(() -> {

            //            String urn = resourceName.toString();
            //
            //            StacProperty datetimeStacProp = config.getDatetimeStacProperty();
            //            List<StacProperty> stacProps = config.getStacProperties();
            //            List<StacProperty> nonDatetimeStacProps = stacProps.remove(datetimeStacProp);
            //
            //            List<QueryableAttribute> creationDate = extentSummaryService.extentSummaryQueryableAttributes(
            //                    datetimeStacProp,
            //                    nonDatetimeStacProps
            //            );
            //
            //            CollectionWithStats collectionWithStats = catalogSearchService.getCollectionWithDataObjectsStats(
            //                    resourceName,
            //                    SearchType.DATAOBJECTS,
            //                    creationDate.toJavaList()
            //            );

            //            List<Link> links = getLinks(resourceName, linkCreator, urn);
            //
            //            List<Provider> providers = config.getProviders(urn)
            //                    .map(x -> new Provider(x.getName(), x.getDescription(), x.getUrl(), x.getRoles()));
            //
            //            List<Aggregation> aggs = List.ofAll(collectionWithStats.getAggregationList());
            //            Map<StacProperty, Aggregation> aggregationMap = extentSummaryService.toAggregationMap(stacProps, aggs);
            //            Extent extent = extentSummaryService.extractExtent(aggregationMap);
            //            Map<String, Object> summary = extentSummaryService.extractSummary(aggregationMap);
            //
            //            AbstractEntity<?> regardsCollection = collectionWithStats.getCollection();

            return new Collection(StacSpecConstants.Version.STAC_SPEC_VERSION, List.empty(), dataset.getLabel(),
                                  dataset.getIpId().toString(), "", List.empty(), List.empty(), null, List.empty(),
                                  null, null);

        }).mapFailure(COLLECTION_CONSTRUCTION,
                      () -> String.format("Failed to build collection for URN %s", dataset.getIpId()));
    }

    //    private Try<ItemCollectionResponse> extractItemCollection(
    //            FacetPage<AbstractEntity<? extends EntityFeature>> facetPage,
    //            List<StacProperty> stacProperties,
    //            OGCFeatLinkCreator featLinkCreator,
    //            SearchPageLinkCreator searchPageLinkCreator
    //    ) {
    //        return trying(() -> {
    //            ItemCollectionResponse.Context context = new ItemCollectionResponse.Context(
    //                    facetPage.getNumberOfElements(),
    //                    facetPage.getPageable().getPageSize(),
    //                    facetPage.getTotalElements()
    //            );
    //            ItemCollectionResponse response = new ItemCollectionResponse(
    //                    SEARCH_EXTENSIONS,
    //                    extractStacItems(Stream.ofAll(facetPage.get()), stacProperties, featLinkCreator),
    //                    List.empty(), // resolved later
    //                    context
    //            );
    //            return response.withLinks(extractLinks(searchPageLinkCreator, response));
    //        })
    //                .mapFailure(
    //                        ITEMCOLLECTIONRESPONSE_CONSTRUCTION,
    //                        () -> "Failed to create ItemCollectionResponse"
    //                );
    //    }
    //
    //    private List<Link> extractLinks(SearchPageLinkCreator searchPageLinkCreator, ItemCollectionResponse response) {
    //        return List.of(
    //                searchPageLinkCreator.createSelfPageLink(response)
    //                        .map(uri -> new Link(uri, Link.Relations.SELF, Asset.MediaType.APPLICATION_JSON, "this search page")),
    //                searchPageLinkCreator.createNextPageLink(response)
    //                        .map(uri -> new Link(uri, Link.Relations.NEXT, Asset.MediaType.APPLICATION_JSON, "next search page")),
    //                searchPageLinkCreator.createPrevPageLink(response)
    //                        .map(uri -> new Link(uri, Link.Relations.PREV, Asset.MediaType.APPLICATION_JSON, "prev search page"))
    //        )
    //                .flatMap(l -> l);
    //    }
    //
    //    private List<Item> extractStacItems(
    //            Stream<AbstractEntity<? extends EntityFeature>> entityStream,
    //            List<StacProperty> stacProperties,
    //            OGCFeatLinkCreator featLinkCreator
    //    ) {
    //        return entityStream
    //                .flatMap(entity ->
    //                                 itemConverter.convertFeatureToItem(stacProperties, featLinkCreator, entity)
    //                )
    //                .toList();
    //    }

}
