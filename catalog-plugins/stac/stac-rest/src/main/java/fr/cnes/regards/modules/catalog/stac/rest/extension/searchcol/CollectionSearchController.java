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
package fr.cnes.regards.modules.catalog.stac.rest.extension.searchcol;

import fr.cnes.regards.framework.security.annotation.ResourceAccess;
import fr.cnes.regards.framework.security.role.DefaultRole;
import fr.cnes.regards.modules.catalog.stac.domain.StacConstants;
import fr.cnes.regards.modules.catalog.stac.domain.api.extension.searchcol.*;
import fr.cnes.regards.modules.catalog.stac.domain.spec.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.rest.link.LinkCreatorService;
import fr.cnes.regards.modules.catalog.stac.rest.pagination.SearchOtherPageCollectionBodySerdeService;
import fr.cnes.regards.modules.catalog.stac.rest.utils.TryToResponseEntity;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.CollectionSearchService;
import fr.cnes.regards.modules.catalog.stac.service.collection.timeline.TimelineService;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static fr.cnes.regards.modules.catalog.stac.rest.utils.StacApiConstants.*;

/**
 * Search collection API.
 * <p>
 * This endpoint is dedicated to return REGARDS datasets as STAC collections
 * from an item search and/or a collection search.
 * Both item and collection query parameters can be passed.
 * </p>
 * We add a non-standard 0-based <code>page</code> query param for pagination. Links to next/prev page are done using the
 * {@link #otherPage(Boolean, String, Integer)} endpoint.
 *
 * @author Marc SORDI
 * @see <a href="https://github.com/radiantearth/stac-api-spec/tree/release/v1.0.0/item-search">Description</a>>
 */
@RestController
@RequestMapping(path = STAC_COLLECTION_SEARCH_PATH, produces = StacConstants.APPLICATION_JSON_MEDIA_TYPE)
public class CollectionSearchController implements TryToResponseEntity {

    private final CollectionSearchBodyFactory collectionSearchBodyFactory;

    private final CollectionItemSearchBodyFactory collectionItemSearchBodyFactory;

    private final SearchOtherPageCollectionBodySerdeService searchTokenSerde;

    private final LinkCreatorService linkCreatorService;

    private final CollectionSearchService collectionSearchService;

    private final TimelineService timelineService;

    private final ConfigurationAccessorFactory configFactory;

    public CollectionSearchController(CollectionSearchBodyFactory collectionSearchBodyFactory,
                                      CollectionItemSearchBodyFactory collectionItemSearchBodyFactory,
                                      SearchOtherPageCollectionBodySerdeService searchTokenSerde,
                                      LinkCreatorService linkCreatorService,
                                      CollectionSearchService collectionSearchService,
                                      TimelineService timelineService,
                                      ConfigurationAccessorFactory configFactory) {
        this.collectionSearchBodyFactory = collectionSearchBodyFactory;
        this.collectionItemSearchBodyFactory = collectionItemSearchBodyFactory;
        this.searchTokenSerde = searchTokenSerde;
        this.linkCreatorService = linkCreatorService;
        this.collectionSearchService = collectionSearchService;
        this.timelineService = timelineService;
        this.configFactory = configFactory;
    }

    @Operation(summary = "Search collection with simple filtering",
               description = "Retrieve collection matching filters. Intended as a shorthand API for simple queries.")
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "A set of collections.") })
    @ResourceAccess(description = "Search collection with simple filtering", role = DefaultRole.PUBLIC)
    @GetMapping
    public ResponseEntity<SearchCollectionsResponse> getCollectionSearch(
        @RequestParam(name = LIMIT_QUERY_PARAM, required = false, defaultValue = "10") Integer limit,
        @RequestParam(name = PAGE_QUERY_PARAM, required = false, defaultValue = "1") Integer page,
        @RequestParam(name = BBOX_QUERY_PARAM, required = false) BBox bbox,
        @RequestParam(name = DATETIME_QUERY_PARAM, required = false) String datetime,
        @RequestParam(name = COLLECTIONS_QUERY_PARAM, required = false) List<String> collections,
        @RequestParam(name = IDS_QUERY_PARAM, required = false) List<String> ids,
        @RequestParam(name = FIELDS_QUERY_PARAM, required = false) String fields,
        @RequestParam(name = QUERY_QUERY_PARAM, required = false) String query,
        @RequestParam(name = SORT_BY_QUERY_PARAM, required = false) String sortBy,
        @RequestParam(name = STAC_COLLECTION_ITEM_QUERY_PARAM_PREFIX + BBOX_QUERY_PARAM, required = false)
        BBox itemBbox,
        @RequestParam(name = STAC_COLLECTION_ITEM_QUERY_PARAM_PREFIX + DATETIME_QUERY_PARAM, required = false)
        String itemDatetime,
        @RequestParam(name = STAC_COLLECTION_ITEM_QUERY_PARAM_PREFIX + COLLECTIONS_QUERY_PARAM, required = false)
        List<String> itemCollections,
        @RequestParam(name = STAC_COLLECTION_ITEM_QUERY_PARAM_PREFIX + IDS_QUERY_PARAM, required = false)
        List<String> itemIds,
        @RequestParam(name = STAC_COLLECTION_ITEM_QUERY_PARAM_PREFIX + QUERY_QUERY_PARAM, required = false)
        String itemQuery) {
        CollectionSearchBody collectionSearchBody = collectionSearchBodyFactory.parseCollectionSearch(page,
                                                                                                      limit,
                                                                                                      bbox,
                                                                                                      datetime,
                                                                                                      collections,
                                                                                                      ids,
                                                                                                      fields,
                                                                                                      query,
                                                                                                      sortBy)
                                                                               .getOrElse(CollectionSearchBody.builder()
                                                                                                              .build())
                                                                               .withItem(collectionItemSearchBodyFactory.parseCollectionSearch(
                                                                                   itemBbox,
                                                                                   itemDatetime,
                                                                                   itemCollections,
                                                                                   itemIds,
                                                                                   itemQuery).getOrNull());
        boolean appendAuthParam = !configFactory.makeConfigurationAccessor().isDisableauthParam();
        return toResponseEntity(collectionSearchService.search(collectionSearchBody,
                                                               page,
                                                               linkCreatorService.makeSearchCollectionPageLinkCreation(
                                                                   page,
                                                                   collectionSearchBody,
                                                                   appendAuthParam),
                                                               linkCreatorService.makeOGCFeatLinkCreator(appendAuthParam)));
    }

    @Operation(summary = "Search collections with complex filtering using both collection and item query parameters",
               description = "Retrieve collections matching filters. Full-featured query API.")
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "A set of collections.") })
    @ResourceAccess(description = "Search collection with complex filtering", role = DefaultRole.PUBLIC)
    @PostMapping
    public ResponseEntity<SearchCollectionsResponse> postCollectionSearch(
        @RequestBody CollectionSearchBody collectionSearchBody,
        @RequestParam(name = PAGE_QUERY_PARAM, required = false, defaultValue = "1") Integer page) {
        boolean appendAuthParam = !configFactory.makeConfigurationAccessor().isDisableauthParam();
        return toResponseEntity(collectionSearchService.search(collectionSearchBody,
                                                               collectionSearchBody.getPage() == null ?
                                                                   page :
                                                                   collectionSearchBody.getPage(),
                                                               linkCreatorService.makeSearchCollectionPageLinkCreation(
                                                                   page,
                                                                   collectionSearchBody,
                                                                   appendAuthParam),
                                                               linkCreatorService.makeOGCFeatLinkCreator(appendAuthParam)));
    }

    @Operation(summary = "continue to next/previous search collection page",
               description = "Pagination for search in STAC is done through links,"
                             + " this endpoint provides the way to reuse"
                             + " the same search parameters but skip to an offset of results.")
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "A set of collections.") })
    @ResourceAccess(description = "continue to next/previous search page", role = DefaultRole.PUBLIC)
    @GetMapping("paginate")
    public ResponseEntity<SearchCollectionsResponse> otherPage(
        @RequestParam(name = SEARCH_COLLECTION_BODY_QUERY_PARAM) String collectionBodyBase64,
        @RequestParam(name = PAGE_QUERY_PARAM, required = false, defaultValue = "1") Integer page) {
        Try<CollectionSearchBody> tryCollectionSearchBody = searchTokenSerde.deserialize(collectionBodyBase64);
        boolean appendAuthParam = !configFactory.makeConfigurationAccessor().isDisableauthParam();
        return toResponseEntity(tryCollectionSearchBody.flatMap(collectionSearchBody -> collectionSearchService.search(
            collectionSearchBody,
            page,
            linkCreatorService.makeSearchCollectionPageLinkCreation(page, collectionSearchBody, appendAuthParam),
            linkCreatorService.makeOGCFeatLinkCreator(appendAuthParam))));
    }

    @Operation(summary = "Return the collections timeline",
               description = "Search the timeline for each collection located in the request")
    @ApiResponses(value = { @ApiResponse(responseCode = "200",
                                         description = "A list of collections with their own timeline associated",
                                         content = { @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                              schema = @Schema(implementation = FiltersByCollection.class)) }) })
    @ResourceAccess(description = "", role = DefaultRole.PUBLIC)
    @PostMapping(value = COLLECTIONS_TIMELINE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TimelineByCollectionResponse> timelineCollections(
        @RequestBody TimelineFiltersByCollection timelineFiltersByCollection) {
        return ResponseEntity.ok(timelineService.buildCollectionTimelines(timelineFiltersByCollection));
    }
}
