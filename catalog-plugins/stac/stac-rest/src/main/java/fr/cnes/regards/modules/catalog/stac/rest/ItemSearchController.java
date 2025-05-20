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

package fr.cnes.regards.modules.catalog.stac.rest;

import fr.cnes.regards.framework.security.annotation.ResourceAccess;
import fr.cnes.regards.framework.security.role.DefaultRole;
import fr.cnes.regards.modules.catalog.stac.domain.StacConstants;
import fr.cnes.regards.modules.catalog.stac.domain.api.ItemCollectionResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.ItemSearchBodyFactory;
import fr.cnes.regards.modules.catalog.stac.domain.spec.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.rest.link.LinkCreatorService;
import fr.cnes.regards.modules.catalog.stac.rest.pagination.SearchOtherPageItemBodySerdeService;
import fr.cnes.regards.modules.catalog.stac.rest.utils.HeaderUtils;
import fr.cnes.regards.modules.catalog.stac.rest.utils.TryToResponseEntity;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.item.ItemSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.vavr.collection.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static fr.cnes.regards.modules.catalog.stac.rest.utils.StacApiConstants.*;

/**
 * Search API.
 * <p>
 * We add a non-standard 0-based <code>page</code> query param for pagination. Links to next/prev page are done using the
 * {@link #otherPage(Boolean, String, Integer)}  endpoint.
 *
 * @see <a href="https://github.com/radiantearth/stac-api-spec/tree/release/v1.0.0/item-search">Description</a>>
 */
@RestController
@RequestMapping(path = STAC_SEARCH_PATH,
                produces = { StacConstants.APPLICATION_GEO_JSON_MEDIA_TYPE, StacConstants.APPLICATION_JSON_MEDIA_TYPE })
public class ItemSearchController implements TryToResponseEntity {

    private final ItemSearchBodyFactory itemSearchBodyFactory;

    private final SearchOtherPageItemBodySerdeService searchTokenSerde;

    private final LinkCreatorService linkCreatorService;

    private final ItemSearchService itemSearchService;

    private final ConfigurationAccessorFactory configFactory;

    @Autowired
    public ItemSearchController(ItemSearchBodyFactory itemSearchBodyFactory,
                                SearchOtherPageItemBodySerdeService searchTokenSerde,
                                LinkCreatorService linkCreatorService,
                                ItemSearchService itemSearchService,
                                ConfigurationAccessorFactory configFactory) {
        this.itemSearchBodyFactory = itemSearchBodyFactory;
        this.searchTokenSerde = searchTokenSerde;
        this.linkCreatorService = linkCreatorService;
        this.itemSearchService = itemSearchService;
        this.configFactory = configFactory;
    }

    @Operation(summary = "search with simple filtering",
               description = "Retrieve Items matching filters. Intended as a shorthand API for simple queries.")
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "An ItemCollection.") })
    @ResourceAccess(description = "search with simple filtering", role = DefaultRole.PUBLIC)
    @GetMapping
    public ResponseEntity<ItemCollectionResponse> getItemSearch(
        @RequestParam(name = LIMIT_QUERY_PARAM, required = false, defaultValue = "10") Integer limit,
        @RequestParam(name = PAGE_QUERY_PARAM, required = false, defaultValue = "1") Integer page,
        @RequestParam(name = BBOX_QUERY_PARAM, required = false) BBox bbox,
        @RequestParam(name = DATETIME_QUERY_PARAM, required = false) String datetime,
        @RequestParam(name = COLLECTIONS_QUERY_PARAM, required = false) List<String> collections,
        @RequestParam(name = IDS_QUERY_PARAM, required = false) List<String> ids,
        @RequestParam(name = FIELDS_QUERY_PARAM, required = false) String fields,
        @RequestParam(name = QUERY_QUERY_PARAM, required = false) String query,
        @RequestParam(name = SORT_BY_QUERY_PARAM, required = false) String sortBy,
        @RequestHeader Map<String, String> headers) {
        boolean appendAuthParam = !configFactory.makeConfigurationAccessor().isDisableauthParam();
        Map<String, String> stacHeaders = HeaderUtils.getStacHeaders(headers);
        return toResponseEntity(itemSearchBodyFactory.parseItemSearch(page,
                                                                      limit,
                                                                      bbox,
                                                                      datetime,
                                                                      collections,
                                                                      ids,
                                                                      fields,
                                                                      query,
                                                                      sortBy)
                                                     .flatMap(itemSearchBody -> itemSearchService.search(itemSearchBody,
                                                                                                         page,
                                                                                                         linkCreatorService.makeOGCFeatLinkCreator(
                                                                                                             appendAuthParam,
                                                                                                             stacHeaders),
                                                                                                         linkCreatorService.makeSearchPageLinkCreator(
                                                                                                             page,
                                                                                                             itemSearchBody,
                                                                                                             appendAuthParam,
                                                                                                             stacHeaders),
                                                                                                         stacHeaders)));
    }

    @Operation(summary = "search with complex filtering",
               description = "Retrieve Items matching filters. Full-featured query API.")
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "An ItemCollection.") })
    @ResourceAccess(description = "search with complex filtering", role = DefaultRole.PUBLIC)
    @PostMapping
    public ResponseEntity<ItemCollectionResponse> postItemSearch(@RequestBody ItemSearchBody itemSearchBody,
                                                                 @RequestParam(name = PAGE_QUERY_PARAM,
                                                                               required = false,
                                                                               defaultValue = "1") Integer page,
                                                                 @RequestHeader Map<String, String> headers) {
        boolean appendAuthParam = !configFactory.makeConfigurationAccessor().isDisableauthParam();
        Map<String, String> stacHeaders = HeaderUtils.getStacHeaders(headers);
        return toResponseEntity(itemSearchService.search(itemSearchBody,
                                                         itemSearchBody.getPage() == null ?
                                                             page :
                                                             itemSearchBody.getPage(),
                                                         linkCreatorService.makeOGCFeatLinkCreator(appendAuthParam,
                                                                                                   stacHeaders),
                                                         linkCreatorService.makeSearchPageLinkCreator(page,
                                                                                                      itemSearchBody,
                                                                                                      appendAuthParam,
                                                                                                      stacHeaders),
                                                         stacHeaders));
    }

    @Operation(summary = "continue to next/previous search page",
               description =
                   "Pagination for search in STAC is done through links, this endpoint provides the way to reuse"
                   + " the same search parameters but skip to an offset of results.")
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "An ItemCollection.") })
    @ResourceAccess(description = "continue to next/previous search page", role = DefaultRole.PUBLIC)
    @GetMapping("paginate")
    public ResponseEntity<ItemCollectionResponse> otherPage(
        @RequestParam(name = SEARCH_ITEM_BODY_QUERY_PARAM) String itemBodyBase64,
        @RequestParam(name = PAGE_QUERY_PARAM, required = false, defaultValue = "1") Integer page,
        @RequestHeader Map<String, String> headers) {
        boolean appendAuthParam = !configFactory.makeConfigurationAccessor().isDisableauthParam();
        Map<String, String> stacHeaders = HeaderUtils.getStacHeaders(headers);
        return toResponseEntity(searchTokenSerde.deserialize(itemBodyBase64)
                                                .flatMap(itemSearchBody -> itemSearchService.search(itemSearchBody,
                                                                                                    page,
                                                                                                    linkCreatorService.makeOGCFeatLinkCreator(
                                                                                                        appendAuthParam,
                                                                                                        stacHeaders),
                                                                                                    linkCreatorService.makeSearchPageLinkCreator(
                                                                                                        page,
                                                                                                        itemSearchBody,
                                                                                                        appendAuthParam,
                                                                                                        stacHeaders),
                                                                                                    stacHeaders)));
    }

}
