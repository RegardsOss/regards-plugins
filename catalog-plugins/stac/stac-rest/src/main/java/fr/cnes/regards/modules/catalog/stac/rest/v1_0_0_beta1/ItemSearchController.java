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

package fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.security.annotation.ResourceAccess;
import fr.cnes.regards.framework.security.role.DefaultRole;
import fr.cnes.regards.framework.security.utils.jwt.JWTAuthentication;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemCollectionResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBodyFactory;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.link.LinkCreatorService;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.pagination.SearchOtherPageItemBodySerdeService;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.TryToResponseEntity;
import fr.cnes.regards.modules.catalog.stac.service.ItemSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.vavr.collection.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Asset.MediaType.APPLICATION_JSON;
import static fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.StacApiConstants.*;

/**
 * Search API.
 *
 * We add a non-standard 0-based <code>page</code> query param for pagination. Links to next/prev page are done using the
 * {@link #otherPage(String, Integer)} endpoint.
 *
 * @see <a href="https://github.com/radiantearth/stac-api-spec/tree/v1.0.0-beta.1/item-search">Description</a>>
 */
@RestController
@RequestMapping(
        path = STAC_SEARCH_PATH,
        produces = APPLICATION_JSON,
        consumes = APPLICATION_JSON
)
public class ItemSearchController implements TryToResponseEntity {

    private final ItemSearchBodyFactory itemSearchBodyFactory;
    private final SearchOtherPageItemBodySerdeService searchTokenSerde;
    private final LinkCreatorService linkCreatorService;
    private final ItemSearchService itemSearchService;

    @Autowired
    public ItemSearchController(
            ItemSearchBodyFactory itemSearchBodyFactory,
            SearchOtherPageItemBodySerdeService searchTokenSerde,
            LinkCreatorService linkCreatorService,
            ItemSearchService itemSearchService
    ) {
        this.itemSearchBodyFactory = itemSearchBodyFactory;
        this.searchTokenSerde = searchTokenSerde;
        this.linkCreatorService = linkCreatorService;
        this.itemSearchService = itemSearchService;
    }

    @Operation(summary = "search with simple filtering",
            description = "Retrieve Items matching filters. Intended as a shorthand API for simple queries.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "An ItemCollection.")
    })
    @ResourceAccess(
            description = "search with simple filtering",
            role = DefaultRole.PUBLIC
    )
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<ItemCollectionResponse> simple(
            HttpRequest request,
            @RequestParam(name = LIMIT_QUERY_PARAM, required = false, defaultValue = "10") Integer limit,
            @RequestParam(name = PAGE_QUERY_PARAM, required = false, defaultValue = "0") Integer page,
            @RequestParam(name = BBOX_QUERY_PARAM, required = false) BBox bbox,
            @RequestParam(name = DATETIME_QUERY_PARAM, required = false) String datetime,
            @RequestParam(name = COLLECTIONS_QUERY_PARAM, required = false) List<String> collections,
            @RequestParam(name = IDS_QUERY_PARAM, required = false) List<String> ids,
            @RequestParam(name = FIELDS_QUERY_PARAM, required = false) String fields,
            @RequestParam(name = QUERY_QUERY_PARAM, required = false) String query,
            @RequestParam(name = SORTBY_QUERY_PARAM, required = false) String sortBy
    ) throws ModuleException {
        final JWTAuthentication auth = (JWTAuthentication) SecurityContextHolder.getContext().getAuthentication();

        return toResponseEntity(itemSearchBodyFactory
            .parseItemSearch(limit, bbox, datetime, collections, ids, fields, query, sortBy)
            .flatMap(itemSearchBody -> itemSearchService.search(
                itemSearchBody,
                page,
                linkCreatorService.makeOGCFeatLinkCreator(auth),
                linkCreatorService.makeSearchPageLinkCreator(auth, page, itemSearchBody)
            )));
    }

    @Operation(summary = "search with complex filtering",
            description = "Retrieve Items matching filters. Full-featured query API.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "An ItemCollection.")
    })
    @ResourceAccess(
            description = "search with complex filtering",
            role = DefaultRole.PUBLIC
    )
    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<ItemCollectionResponse> complex(
            @RequestBody ItemSearchBody itemSearchBody,
            @RequestParam(name = PAGE_QUERY_PARAM, required = false, defaultValue = "0") Integer page
    ) throws ModuleException {
        final JWTAuthentication auth = (JWTAuthentication) SecurityContextHolder.getContext().getAuthentication();

        return toResponseEntity(itemSearchService.search(
            itemSearchBody,
            page,
            linkCreatorService.makeOGCFeatLinkCreator(auth),
            linkCreatorService.makeSearchPageLinkCreator(auth, page, itemSearchBody)
        ));
    }

    @Operation(
            summary = "continue to next/previous search page",
            description = "Pagination for search in STAC is done through links," +
                    " this endpoint provides the way to reuse" +
                    " the same search parameters but skip to an offset of results.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "An ItemCollection.")
    })
    @ResourceAccess(
        description = "continue to next/previous search page",
        role = DefaultRole.PUBLIC
    )
    @RequestMapping(path="paginate", method = RequestMethod.GET)
    public ResponseEntity<ItemCollectionResponse> otherPage(
            @RequestParam(name = SEARCH_ITEMBODY_QUERY_PARAM, required = false) String itemBodyBase64,
            @RequestParam(name = PAGE_QUERY_PARAM, required = false, defaultValue = "0") Integer page
    ) throws ModuleException {
        final JWTAuthentication auth = (JWTAuthentication) SecurityContextHolder.getContext().getAuthentication();

        return toResponseEntity(searchTokenSerde.deserialize(itemBodyBase64)
            .flatMap(itemSearchBody -> itemSearchService.search(
                itemSearchBody,
                page,
                linkCreatorService.makeOGCFeatLinkCreator(auth),
                linkCreatorService.makeSearchPageLinkCreator(auth, page, itemSearchBody)
            ))
        );
    }

}
