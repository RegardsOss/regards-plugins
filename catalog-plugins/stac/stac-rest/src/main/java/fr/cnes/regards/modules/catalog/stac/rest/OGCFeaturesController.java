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
import fr.cnes.regards.modules.catalog.stac.domain.api.CollectionsResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.ItemCollectionResponse;
import fr.cnes.regards.modules.catalog.stac.domain.spec.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.Item;
import fr.cnes.regards.modules.catalog.stac.domain.spec.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.rest.link.LinkCreatorService;
import fr.cnes.regards.modules.catalog.stac.rest.utils.TryToResponseEntity;
import fr.cnes.regards.modules.catalog.stac.service.collection.CollectionService;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.configuration.collection.CollectionConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.collection.CollectionConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.item.ItemSearchService;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static fr.cnes.regards.modules.catalog.stac.rest.utils.StacApiConstants.*;

/**
 * OGC Feature API
 *
 * @see <a href="https://github.com/radiantearth/stac-api-spec/tree/release/v1.0.0/ogcapi-features"></a>
 */
@RestController
@RequestMapping(path = STAC_COLLECTIONS_PATH, produces = StacConstants.APPLICATION_JSON_MEDIA_TYPE)
public class OGCFeaturesController implements TryToResponseEntity {

    private final CollectionService collectionService;

    private final ConfigurationAccessorFactory configFactory;

    private final CollectionConfigurationAccessorFactory collectionConfigFactory;

    private final LinkCreatorService linker;

    private final ItemSearchService itemSearchService;

    @Autowired
    public OGCFeaturesController(CollectionService collectionService,
                                 ConfigurationAccessorFactory configFactory,
                                 CollectionConfigurationAccessorFactory collectionConfigFactory,
                                 LinkCreatorService linker,
                                 ItemSearchService itemSearchService) {
        this.collectionService = collectionService;
        this.configFactory = configFactory;
        this.collectionConfigFactory = collectionConfigFactory;
        this.linker = linker;
        this.itemSearchService = itemSearchService;
    }

    @Operation(summary = "the feature collections in the dataset",
               description = "A body of Feature Collections that belong or are used together with additional links. "
                             + "Request may not return the full set of metadata per Feature Collection.")
    @ApiResponses(value = { @ApiResponse(responseCode = "200",
                                         description = "The feature collections shared by this API.") })
    @ResourceAccess(description = "the feature collections in the dataset", role = DefaultRole.PUBLIC)
    @GetMapping
    public ResponseEntity<CollectionsResponse> getCollections() {
        boolean appendAuthParam = !configFactory.makeConfigurationAccessor().isDisableauthParam();
        ConfigurationAccessor config = configFactory.makeConfigurationAccessor();
        CollectionConfigurationAccessor collectionConfig = collectionConfigFactory.makeConfigurationAccessor();
        OGCFeatLinkCreator linkCreator = linker.makeOGCFeatLinkCreator(appendAuthParam);
        return toResponseEntity(collectionService.buildRootCollectionsResponse(linkCreator, config, collectionConfig));
    }

    /**
     * Get the stac collection from Regards collection
     *
     * @param collectionId this is the urn
     */
    @Operation(summary = "describe the feature collection with id `collectionId`",
               description = "A single Feature Collection for the given if collectionId. "
                             + "Request this endpoint to get a full list of metadata for the Feature Collection.")
    @ApiResponses(value = { @ApiResponse(responseCode = "200",
                                         description = "Information about the feature collection with id collectionId."),
                            @ApiResponse(responseCode = "404", description = "Collection not found.") })
    @ResourceAccess(description = "describe the feature collection with id `collectionId`", role = DefaultRole.PUBLIC)
    @GetMapping(STAC_COLLECTION_PATH_SUFFIX)
    public ResponseEntity<Collection> describeCollection(@PathVariable(COLLECTION_ID_PARAM) String collectionId) {
        boolean appendAuthParam = !configFactory.makeConfigurationAccessor().isDisableauthParam();
        ConfigurationAccessor config = configFactory.makeConfigurationAccessor();
        CollectionConfigurationAccessor collectionConfig = collectionConfigFactory.makeConfigurationAccessor();
        OGCFeatLinkCreator linkCreator = linker.makeOGCFeatLinkCreator(appendAuthParam);
        return toResponseEntity(collectionService.buildCollection(collectionId, linkCreator, config, collectionConfig));
    }

    @Operation(summary = "fetch features",
               description = "Fetch features of the feature collection with id collectionId.")
    @ApiResponses(value = { @ApiResponse(responseCode = "200",
                                         description = "The response is a document consisting of features in the collection."),
                            @ApiResponse(responseCode = "404", description = "Collection not found.") })
    @ResourceAccess(description = "fetch features", role = DefaultRole.PUBLIC)
    @GetMapping(value = STAC_ITEMS_PATH_SUFFIX,
                produces = { StacConstants.APPLICATION_GEO_JSON_MEDIA_TYPE, StacConstants.APPLICATION_JSON_MEDIA_TYPE })
    public ResponseEntity<ItemCollectionResponse> getFeatures(
        @PathVariable(name = COLLECTION_ID_PARAM) String collectionId,
        @RequestParam(name = LIMIT_QUERY_PARAM, required = false) Integer limit,
        @RequestParam(name = BBOX_QUERY_PARAM, required = false) BBox bbox,
        @RequestParam(name = DATETIME_QUERY_PARAM, required = false) String datetime,
        @RequestParam(name = PAGE_QUERY_PARAM, required = false, defaultValue = "1") Integer page) {
        boolean appendAuthParam = !configFactory.makeConfigurationAccessor().isDisableauthParam();
        return toResponseEntity(collectionService.getItemsForCollection(collectionId,
                                                                        limit,
                                                                        page,
                                                                        bbox,
                                                                        datetime,
                                                                        linker.makeOGCFeatLinkCreator(appendAuthParam),
                                                                        isb -> linker.makeCollectionItemsPageLinkCreator(
                                                                            page,
                                                                            collectionId,
                                                                            appendAuthParam)));
    }

    @Operation(summary = "fetch a single feature",
               description = "Fetch the feature with id featureId in the feature collection with id collectionId.")
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "The feature content."),
                            @ApiResponse(responseCode = "404", description = "Feature not found.") })
    @ResourceAccess(description = "fetch a single feature", role = DefaultRole.PUBLIC)
    @GetMapping(STAC_ITEM_PATH_SUFFIX)
    public ResponseEntity<Item> getFeature(@PathVariable(name = COLLECTION_ID_PARAM) String collectionId,
                                           @PathVariable(name = ITEM_ID_PARAM) String featureId) {
        boolean appendAuthParam = !configFactory.makeConfigurationAccessor().isDisableauthParam();
        OGCFeatLinkCreator linkCreator = linker.makeOGCFeatLinkCreator(appendAuthParam);
        Try<Item> result = itemSearchService.searchById(featureId, linkCreator);
        return toResponseEntity(result);
    }
}
