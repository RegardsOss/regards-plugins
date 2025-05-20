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
package fr.cnes.regards.modules.catalog.stac.plugin.it.swot;

import com.jayway.jsonpath.JsonPath;
import fr.cnes.regards.framework.geojson.coordinates.Position;
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.framework.gson.adapters.OffsetDateTimeAdapter;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.security.utils.HttpConstants;
import fr.cnes.regards.framework.test.integration.RequestBuilderCustomizer;
import fr.cnes.regards.modules.catalog.stac.domain.StacConstants;
import fr.cnes.regards.modules.catalog.stac.domain.StacProperties;
import fr.cnes.regards.modules.catalog.stac.domain.api.DateInterval;
import fr.cnes.regards.modules.catalog.stac.domain.api.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.SearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.extension.searchcol.CollectionSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.extension.searchcol.FiltersByCollection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.plugin.it.AbstractStacIT;
import fr.cnes.regards.modules.catalog.stac.rest.utils.StacApiConstants;
import fr.cnes.regards.modules.search.domain.plugin.SearchEngineConfiguration;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

/**
 * Cross layer integration test : from RESTful API to Elasticsearch index
 *
 * @author Marc SORDI
 */
@TestPropertySource(locations = { "classpath:test.properties" },
                    properties = { "regards.tenant=swotv2", "spring.jpa.properties.hibernate.default_schema=swotv2" })
public class SwotV2EngineControllerIT extends AbstractStacIT {

    @Before
    @Override
    public void prepareData() throws ModuleException, InterruptedException, IOException {
        cleanDatabase();
        super.prepareData();
    }

    @Override
    protected String getDataFolderName() {
        return "swot_v2";
    }

    @Override
    protected String getDataModel() {
        return "001_data_model_hygor_V2.xml";
    }

    @Override
    protected String getDatasetModel() {
        return "002_dataset_model_hygor_V2.xml";
    }

    @Override
    protected void initPlugins() throws ModuleException {
        SearchEngineConfiguration conf = loadFromJson(getConfigFolder().resolve("STAC-api-configuration-v2.json"),
                                                      SearchEngineConfiguration.class);
        searchEngineService.createConf(conf);

        SearchEngineConfiguration collectionConf = loadFromJson(getConfigFolder().resolve(
            "STAC-collection-configuration-v2.json"), SearchEngineConfiguration.class);
        searchEngineService.createConf(collectionConf);
    }

    /**
     * Basic COLLECTION search without criterion
     */
    @Test
    public void searchCollectionsAsPost() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        CollectionSearchBody body = CollectionSearchBody.builder().build();
        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    /**
     * Get items from a collection
     */
    @Test
    public void searchItemsAsGet() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.addParameter("collections", "SWOT_L2_HR_Raster_100m");
        customizer.addParameter("limit", "1");
        performDefaultGet(StacApiConstants.STAC_SEARCH_PATH, customizer, "Cannot search STAC items");
    }

    /**
     * Get items from a collection
     */
    @Test
    public void searchItemsAsGetWithoutAuthParams() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        //TODO : change this header with plugin config
        customizer.addHeader(StacConstants.DISABLE_AUTH_PARAMS, "true");
        customizer.addParameter("collections", "SWOT_L2_HR_Raster_100m");
        customizer.addParameter("limit", "1");
        performDefaultGet(StacApiConstants.STAC_SEARCH_PATH, customizer, "Cannot search STAC items");
    }

    /**
     * Get items from a collection and a specific datetime
     */
    @Test
    public void searchItemsWithDatetimeAsGet() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.addParameter("collections", "SWOT_L2_HR_Raster_100m");
        customizer.addParameter("datetime", "2000-09-29T08:00:00.000Z/2024-10-29T17:00:00.000Z");
        performDefaultGet(StacApiConstants.STAC_SEARCH_PATH, customizer, "Cannot search STAC items");
    }

    /**
     * Basic ITEM search without criterion
     */
    @Test
    public void searchItemsAsPost() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        Map<String, SearchBody.QueryObject> iq = HashMap.of("hydro:data_type",
                                                            SearchBody.StringQueryObject.builder()
                                                                                        .eq("L2_HR_RASTER_100m")
                                                                                        .build());
        ItemSearchBody body = ItemSearchBody.builder().query(iq).limit(1).build();
        performDefaultPost(StacApiConstants.STAC_SEARCH_PATH, body, customizer, "Cannot search STAC items");
    }

    /**
     * Basic ITEM search returning null geometry
     */
    @Test
    public void searchItemWithNullGeometryAsPost() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        customizer.expectValue("$.features[0].geometry", null);
        Map<String, SearchBody.QueryObject> iq = HashMap.of("version",
                                                            SearchBody.StringQueryObject.builder().eq("012").build(),
                                                            "hydro:data_type",
                                                            SearchBody.StringQueryObject.builder()
                                                                                        .eq("L2_HR_RASTER_100m")
                                                                                        .build());
        ItemSearchBody body = ItemSearchBody.builder().query(iq).build();
        performDefaultPost(StacApiConstants.STAC_SEARCH_PATH, body, customizer, "Cannot search STAC items");
    }

    @Test
    public void searchCollectionsWithSpatioTemporalParameters() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // Define item criteria
        Map<String, SearchBody.QueryObject> iq = HashMap.of("start_datetime",
                                                            SearchBody.DatetimeQueryObject.builder()
                                                                                          .lte(OffsetDateTime.parse(
                                                                                              "2021-08-19T08:47:59.813Z"))
                                                                                          .build(),
                                                            "end_datetime",
                                                            SearchBody.DatetimeQueryObject.builder()
                                                                                          .gte(OffsetDateTime.parse(
                                                                                              "2021-07-19T08:47:59.813Z"))
                                                                                          .build());
        BBox bbox = new BBox(1.1540490566502684, 43.498828738236014, 1.7531472622166748, 43.704683650908095);
        CollectionSearchBody.CollectionItemSearchBody itemBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                                                                                                              .bbox(bbox)
                                                                                                              .query(iq)
                                                                                                              .build();
        CollectionSearchBody body = CollectionSearchBody.builder().item(itemBody).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsWithItemVariables() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // Define item criteria
        Map<String, SearchBody.QueryObject> iq = HashMap.of("hydro:variables.uri",
                                                            SearchBody.StringQueryObject.builder()
                                                                                        .contains(
                                                                                            "https://w3id.org/hysope2/waterLevel")
                                                                                        .matchType("keyword")
                                                                                        .build());
        CollectionSearchBody.CollectionItemSearchBody itemBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                                                                                                              .query(iq)
                                                                                                              .build();
        CollectionSearchBody body = CollectionSearchBody.builder().item(itemBody).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsWithItemVariablesAnd() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        Map<String, SearchBody.QueryObject> cq = HashMap.of("hydro:variables.uri",
                                                            SearchBody.StringQueryObject.builder()
                                                                                        .containsAll(List.of(
                                                                                            "https://w3id.org/hysope2/waterLevel",
                                                                                            "https://w3id.org/hysope2/landWaterMask"))
                                                                                        .matchType("keyword")
                                                                                        .build(),
                                                            "total_items",
                                                            SearchBody.NumberQueryObject.builder().gt(1D).build());
        CollectionSearchBody body = CollectionSearchBody.builder().query(cq).build();
        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollections_withLongRangeQuery() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        Map<String, SearchBody.QueryObject> cq = HashMap.of("total_items",
                                                            SearchBody.NumberQueryObject.builder()
                                                                                        .gte(2D)
                                                                                        .lte(3D)
                                                                                        .build());
        CollectionSearchBody body = CollectionSearchBody.builder().query(cq).build();
        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollections_withStringQuery() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        Map<String, SearchBody.QueryObject> cq = HashMap.of("dcs:data_file_format",
                                                            SearchBody.StringQueryObject.builder()
                                                                                        .eq("NetCDF5")
                                                                                        .build());
        CollectionSearchBody body = CollectionSearchBody.builder().query(cq).build();
        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollections_withBooleanQuery() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        customizer.expectValue("$.collections[0].summaries.resolution:spatial", "250m");
        Map<String, SearchBody.QueryObject> cq = HashMap.of("certified",
                                                            SearchBody.BooleanQueryObject.builder().neq(true).build());
        CollectionSearchBody body = CollectionSearchBody.builder().query(cq).build();
        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollections_withDoubleRangeQuery() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        customizer.expectValue("$.collections[0].summaries.resolution:spatial", "250m");
        Map<String, SearchBody.QueryObject> cq = HashMap.of("measures:double_measure",
                                                            SearchBody.NumberQueryObject.builder()
                                                                                        .gte(18D)
                                                                                        .lte(19D)
                                                                                        .build());
        CollectionSearchBody body = CollectionSearchBody.builder().query(cq).build();
        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollections_withIntegerQuery() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        Map<String, SearchBody.QueryObject> cq = HashMap.of("measures:int_measure",
                                                            SearchBody.NumberQueryObject.builder().eq(19D).build());
        CollectionSearchBody body = CollectionSearchBody.builder().query(cq).build();
        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollections_withIds() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        CollectionSearchBody body = CollectionSearchBody.builder().ids(List.of("SWOT_L2_HR_Raster_100m")).build();
        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void given_noCollection_when_prepareDownload_then_badRequest() {
        RequestBuilderCustomizer customizer = customizer().expectStatusBadRequest();
        FiltersByCollection body = FiltersByCollection.builder().build();
        performDefaultPost(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                           + StacApiConstants.STAC_DOWNLOAD_AS_ZIP_PREPARE_PATH_SUFFIX,
                           body,
                           customizer,
                           "Download preparation failed");
    }

    @Test
    public void given_notUrnCollection_when_prepareDownload_then_successfulRequest_withErrors() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectToHaveSize("collections", 1);
        customizer.expectToHaveSize("$.collections[0].errors", 1);
        FiltersByCollection.CollectionFilters downloadCollectionPreparationBody = FiltersByCollection.CollectionFilters.builder()
                                                                                                                       .collectionId(
                                                                                                                           "unknown")
                                                                                                                       .build();

        FiltersByCollection body = FiltersByCollection.builder()
                                                      .collections(List.of(downloadCollectionPreparationBody))
                                                      .build();
        performDefaultPost(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                           + StacApiConstants.STAC_DOWNLOAD_AS_ZIP_PREPARE_PATH_SUFFIX,
                           body,
                           customizer,
                           "Download preparation failed");
    }

    @Test
    public void given_UnknownUrnCollection_when_prepareDownload_then_successfulRequest_withErrors() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectToHaveSize("collections", 1);
        customizer.expectToHaveSize("$.collections[0].errors", 1);
        FiltersByCollection.CollectionFilters downloadCollectionPreparationBody = FiltersByCollection.CollectionFilters.builder()
                                                                                                                       .collectionId(
                                                                                                                           "URN:AIP:DATASET:swotv2:b32d9001-4c15-4c57-841a-000000000000:V1")
                                                                                                                       .build();

        FiltersByCollection body = FiltersByCollection.builder()
                                                      .collections(List.of(downloadCollectionPreparationBody))
                                                      .build();
        performDefaultPost(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                           + StacApiConstants.STAC_DOWNLOAD_AS_ZIP_PREPARE_PATH_SUFFIX,
                           body,
                           customizer,
                           "Download preparation failed");
    }

    @Test
    public void given_oneCollection_when_prepareDownloadWithoutFilter_then_successfulRequest() {
        prepareDownload(HashMap.of("L2_HR_RASTER_100m", null), 351000, 2, 3);
    }

    @Test
    public void given_oneCollection_when_getCollectionInformation_then_successfulRequest() {
        ResultActions resultActions = getCollectionInformation(HashMap.of("L2_HR_RASTER_100m", null));
        String json = payload(resultActions);
        Assert.assertNotNull("Payload is required", json);

        // Check that the collection information is present
        java.util.Map<String, Object> collection = JsonPath.read(json, "$.collections[0]");
        Assert.assertNotNull("Collection information is required", collection);
        Assert.assertEquals(351000, collection.get("size"));
        Assert.assertEquals(2, collection.get("items"));
        Assert.assertEquals(3, collection.get("files"));
        Assert.assertNotNull("Sample is required", collection.get("sample"));
    }

    @Test
    public void given_unknownCollection_when_getCollectionInformation_then_errorRequest() {

        // Body
        FiltersByCollection.CollectionFilters downloadCollectionPreparationBody = FiltersByCollection.CollectionFilters.builder()
                                                                                                                       .collectionId(
                                                                                                                           "unknown")
                                                                                                                       .build();

        FiltersByCollection filtersByCollection = FiltersByCollection.builder()
                                                                     .collections(List.of(
                                                                         downloadCollectionPreparationBody))
                                                                     .build();

        // Assertions
        RequestBuilderCustomizer customizer = customizer().expectStatusBadRequest();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_INFORMATION_PATH,
                           filtersByCollection,
                           customizer,
                           "Cannot get collection information");
    }

    @Test
    public void given_oneCollection_when_prepareDownloadWithAppendAuthParams_then_token_is_in_download_link() {
        ResultActions resultActions = prepareDownload(HashMap.of("L2_HR_RASTER_100m", null), 351000, 2, 3, true);

        // Get tiny url to download all
        String json = payload(resultActions);
        String downloadAll = JsonPath.read(json, "$.downloadAll");
        java.util.List<NameValuePair> pairs = URLEncodedUtils.parse(URI.create(downloadAll), Charset.defaultCharset());
        String token = pairs.stream()
                            .filter(pair -> "token".equals(pair.getName()))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Token is required"))
                            .getValue();

        // Expect not null token
        Assert.assertNotNull(token);
    }

    @Test
    public void given_oneCollection_when_prepareDownloadWithoutAuthParams_then_token_is_not_in_download_link() {
        ResultActions resultActions = prepareDownload(HashMap.of("L2_HR_RASTER_100m", null), 351000, 2, 3, false);

        // Get tiny url to download all
        String json = payload(resultActions);
        String downloadAll = JsonPath.read(json, "$.downloadAll");
        java.util.List<NameValuePair> pairs = URLEncodedUtils.parse(URI.create(downloadAll), Charset.defaultCharset());

        Optional<NameValuePair> tokenPair = pairs.stream().filter(pair -> "token".equals(pair.getName())).findFirst();
        // No token should be found
        Assert.assertTrue("Token should not be found", tokenPair.isEmpty());
    }

    @Test
    public void given_oneCollection_when_prepareDownloadWithNullAuthParams_then_token_is_in_download_link() {
        ResultActions resultActions = prepareDownload(HashMap.of("L2_HR_RASTER_100m", null), 351000, 2, 3, null);

        // We want to be sure that when appendAuthParams is not set by default the token will be present in the
        // download links
        // Get tiny url to download all
        String json = payload(resultActions);
        String downloadAll = JsonPath.read(json, "$.downloadAll");
        java.util.List<NameValuePair> pairs = URLEncodedUtils.parse(URI.create(downloadAll), Charset.defaultCharset());
        String token = pairs.stream()
                            .filter(pair -> "token".equals(pair.getName()))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Token is required"))
                            .getValue();

        // Expect not null token
        Assert.assertNotNull(token);
    }

    /**
     * Test without criterion
     */
    @Test
    public void given_oneCollection_when_prepareDownloadWithoutFilter_then_successfulRequest_And_DownloadScript() {
        ResultActions resultActions = prepareDownload(HashMap.of("L2_HR_RASTER_100m", null), 351000, 2, 3);

        // Get tiny url to download all
        String json = payload(resultActions);
        String downloadAll = JsonPath.read(json, "$.downloadAllScript");
        java.util.List<NameValuePair> pairs = URLEncodedUtils.parse(URI.create(downloadAll), Charset.defaultCharset());
        String tinyurl = pairs.stream()
                              .filter(pair -> "tinyurl".equals(pair.getName()))
                              .findFirst()
                              .orElseThrow(() -> new AssertionError("Tinyurl is required"))
                              .getValue();

        RequestBuilderCustomizer downloadCustomizer = customizer().expectStatusOk();
        downloadCustomizer.addParameter("tinyurl", tinyurl);
        performDefaultGet(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                          + StacApiConstants.STAC_DOWNLOAD_ALL_COLLECTIONS_SCRIPT_SUFFIX,
                          downloadCustomizer,
                          "Cannot download all collections");
    }

    /**
     * Test without criterion but 2 collections
     */
    @Test
    public void given_twoCollection_when_prepareDownloadWithoutFilter_then_successfulRequest_And_DownloadScript() {
        ResultActions resultActions = prepareDownload(HashMap.of("L2_HR_RASTER_100m", null, "L2_HR_RASTER_250m", null),
                                                      553000,
                                                      3,
                                                      5);

        // Get tiny url to download all
        String json = payload(resultActions);
        String downloadAll = JsonPath.read(json, "$.downloadAllScript");
        java.util.List<NameValuePair> pairs = URLEncodedUtils.parse(URI.create(downloadAll), Charset.defaultCharset());
        String tinyurl = pairs.stream()
                              .filter(pair -> "tinyurl".equals(pair.getName()))
                              .findFirst()
                              .orElseThrow(() -> new AssertionError("Tinyurl is required"))
                              .getValue();

        RequestBuilderCustomizer downloadCustomizer = customizer().expectStatusOk();
        downloadCustomizer.addParameter("tinyurl", tinyurl);
        performDefaultGet(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                          + StacApiConstants.STAC_DOWNLOAD_ALL_COLLECTIONS_SCRIPT_SUFFIX,
                          downloadCustomizer,
                          "Cannot download all collections");
    }

    /**
     * Test with datetime criterion
     */
    @Test
    public void given_oneCollection_when_prepareDownloadWithFilter1_then_successfulRequest_And_DownloadScript() {
        CollectionSearchBody.CollectionItemSearchBody collectionItemSearchBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                                                                                                                              .datetime(
                                                                                                                                  DateInterval.from(
                                                                                                                                      OffsetDateTimeAdapter.parse(
                                                                                                                                          "2010-06-15T00:00:00Z")))
                                                                                                                              .build();
        ResultActions resultActions = prepareDownload(HashMap.of("L2_HR_RASTER_100m", collectionItemSearchBody),
                                                      351000,
                                                      2,
                                                      3);

        // Get tiny url to download all
        String json = payload(resultActions);
        String downloadAll = JsonPath.read(json, "$.downloadAllScript");
        java.util.List<NameValuePair> pairs = URLEncodedUtils.parse(URI.create(downloadAll), Charset.defaultCharset());
        String tinyurl = pairs.stream()
                              .filter(pair -> "tinyurl".equals(pair.getName()))
                              .findFirst()
                              .orElseThrow(() -> new AssertionError("Tinyurl is required"))
                              .getValue();

        RequestBuilderCustomizer downloadCustomizer = customizer().expectStatusOk();
        downloadCustomizer.addParameter("tinyurl", tinyurl);
        performDefaultGet(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                          + StacApiConstants.STAC_DOWNLOAD_ALL_COLLECTIONS_SCRIPT_SUFFIX,
                          downloadCustomizer,
                          "Cannot download all collections");
    }

    /**
     * Test with intersect criterion
     */
    @Test
    public void given_oneCollection_when_prepareDownloadWithFilter2_then_successfulRequest_And_DownloadScript() {
        CollectionSearchBody.CollectionItemSearchBody collectionItemSearchBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                                                                                                                              .intersects(
                                                                                                                                  IGeometry.polygon(
                                                                                                                                      IGeometry.toPolygonCoordinates(
                                                                                                                                          IGeometry.positions(
                                                                                                                                              new Position[] {
                                                                                                                                                  IGeometry.position(
                                                                                                                                                      1D,
                                                                                                                                                      43D),
                                                                                                                                                  IGeometry.position(
                                                                                                                                                      2D,
                                                                                                                                                      43D),
                                                                                                                                                  IGeometry.position(
                                                                                                                                                      2D,
                                                                                                                                                      44D),
                                                                                                                                                  IGeometry.position(
                                                                                                                                                      1D,
                                                                                                                                                      44D),
                                                                                                                                                  IGeometry.position(
                                                                                                                                                      1D,
                                                                                                                                                      43D) }))))
                                                                                                                              .build();
        ResultActions resultActions = prepareDownload(HashMap.of("L2_HR_RASTER_100m", collectionItemSearchBody),
                                                      250000,
                                                      1,
                                                      1);

        // Get tiny url to download all
        String json = payload(resultActions);
        String downloadAll = JsonPath.read(json, "$.downloadAllScript");
        java.util.List<NameValuePair> pairs = URLEncodedUtils.parse(URI.create(downloadAll), Charset.defaultCharset());
        String tinyurl = pairs.stream()
                              .filter(pair -> "tinyurl".equals(pair.getName()))
                              .findFirst()
                              .orElseThrow(() -> new AssertionError("Tinyurl is required"))
                              .getValue();

        RequestBuilderCustomizer downloadCustomizer = customizer().expectStatusOk();
        downloadCustomizer.addParameter("tinyurl", tinyurl);
        performDefaultGet(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                          + StacApiConstants.STAC_DOWNLOAD_ALL_COLLECTIONS_SCRIPT_SUFFIX,
                          downloadCustomizer,
                          "Cannot download all collections");
    }

    /**
     * Test with BBOX criterion
     */
    @Test
    public void given_oneCollection_when_prepareDownloadWithFilter3_then_successfulRequest_And_DownloadScript() {
        CollectionSearchBody.CollectionItemSearchBody collectionItemSearchBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                                                                                                                              .bbox(
                                                                                                                                  new BBox(
                                                                                                                                      1D,
                                                                                                                                      43D,
                                                                                                                                      2D,
                                                                                                                                      44D))
                                                                                                                              .build();
        ResultActions resultActions = prepareDownload(HashMap.of("L2_HR_RASTER_100m", collectionItemSearchBody),
                                                      250000,
                                                      1,
                                                      1);

        // Get tiny url to download all
        String json = payload(resultActions);
        String downloadAll = JsonPath.read(json, "$.downloadAllScript");
        java.util.List<NameValuePair> pairs = URLEncodedUtils.parse(URI.create(downloadAll), Charset.defaultCharset());
        String tinyurl = pairs.stream()
                              .filter(pair -> "tinyurl".equals(pair.getName()))
                              .findFirst()
                              .orElseThrow(() -> new AssertionError("Tinyurl not found"))
                              .getValue();

        RequestBuilderCustomizer downloadCustomizer = customizer().expectStatusOk();
        downloadCustomizer.addParameter("tinyurl", tinyurl);
        performDefaultGet(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                          + StacApiConstants.STAC_DOWNLOAD_ALL_COLLECTIONS_SCRIPT_SUFFIX,
                          downloadCustomizer,
                          "Cannot download all collections");
    }

    /**
     * Test with query criterion : integer query parameter
     */
    @Test
    public void given_oneCollection_when_prepareDownloadWithFilter4_then_successfulRequest_And_DownloadScript() {
        Map<String, SearchBody.QueryObject> iq = HashMap.of("spatial:pass_id",
                                                            SearchBody.NumberQueryObject.builder().eq(43D).build());
        CollectionSearchBody.CollectionItemSearchBody collectionItemSearchBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                                                                                                                              .query(
                                                                                                                                  iq)
                                                                                                                              .build();
        ResultActions resultActions = prepareDownload(HashMap.of("L2_HR_RASTER_100m", collectionItemSearchBody),
                                                      250000,
                                                      1,
                                                      1);

        // Get tiny url to download all
        String json = payload(resultActions);
        String downloadAll = JsonPath.read(json, "$.downloadAllScript");
        java.util.List<NameValuePair> pairs = URLEncodedUtils.parse(URI.create(downloadAll), Charset.defaultCharset());
        String tinyurl = pairs.stream()
                              .filter(pair -> "tinyurl".equals(pair.getName()))
                              .findFirst()
                              .orElseThrow(() -> new AssertionError("Tinyurl is required"))
                              .getValue();

        RequestBuilderCustomizer downloadCustomizer = customizer().expectStatusOk();
        downloadCustomizer.addParameter("tinyurl", tinyurl);
        //        downloadCustomizer.addHeader(HttpConstants.ACCEPT, MediaType.TEXT_PLAIN_VALUE);
        performDefaultGet(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                          + StacApiConstants.STAC_DOWNLOAD_ALL_COLLECTIONS_SCRIPT_SUFFIX,
                          downloadCustomizer,
                          "Cannot download all collections");
    }

    /**
     * Test with query criterion : string query parameter
     */
    @Test
    public void given_oneCollection_when_prepareDownloadWithFilter5_then_successfulRequest_And_DownloadScript() {
        Map<String, SearchBody.QueryObject> iq = HashMap.of("dcs:item_type",
                                                            SearchBody.StringQueryObject.builder().eq("tile").build());
        CollectionSearchBody.CollectionItemSearchBody collectionItemSearchBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                                                                                                                              .query(
                                                                                                                                  iq)
                                                                                                                              .build();
        ResultActions resultActions = prepareDownload(HashMap.of("L2_HR_RASTER_100m", collectionItemSearchBody),
                                                      351000,
                                                      2,
                                                      3);

        // Get tiny url to download all
        String json = payload(resultActions);
        String downloadAll = JsonPath.read(json, "$.downloadAllScript");
        java.util.List<NameValuePair> pairs = URLEncodedUtils.parse(URI.create(downloadAll), Charset.defaultCharset());
        String tinyurl = pairs.stream()
                              .filter(pair -> "tinyurl".equals(pair.getName()))
                              .findFirst()
                              .orElseThrow(() -> new AssertionError("Tinyurl is required"))
                              .getValue();

        RequestBuilderCustomizer downloadCustomizer = customizer().expectStatusOk();
        downloadCustomizer.addParameter("tinyurl", tinyurl);
        //        downloadCustomizer.addHeader(HttpConstants.ACCEPT, MediaType.TEXT_PLAIN_VALUE);
        performDefaultGet(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                          + StacApiConstants.STAC_DOWNLOAD_ALL_COLLECTIONS_SCRIPT_SUFFIX,
                          downloadCustomizer,
                          "Cannot download all collections");
    }

    /**
     * Test with query criterion : start_datetime & end_datetime query parameter
     */
    @Test
    public void given_oneCollection_when_prepareDownloadWithFilter6_then_successfulRequest_And_DownloadScript() {
        Map<String, SearchBody.QueryObject> iq = HashMap.of(StacProperties.START_DATETIME_PROPERTY_NAME,
                                                            SearchBody.DatetimeQueryObject.builder()
                                                                                          .lte(OffsetDateTimeAdapter.parse(
                                                                                              "2020-04-02T23:59:59Z"))
                                                                                          .build(),
                                                            StacProperties.END_DATETIME_PROPERTY_NAME,
                                                            SearchBody.DatetimeQueryObject.builder()
                                                                                          .gte(OffsetDateTimeAdapter.parse(
                                                                                              "2020-04-02T00:00:00Z"))
                                                                                          .build());

        CollectionSearchBody.CollectionItemSearchBody collectionItemSearchBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                                                                                                                              .query(
                                                                                                                                  iq)
                                                                                                                              .build();
        ResultActions resultActions = prepareDownload(HashMap.of("L2_HR_RASTER_100m", collectionItemSearchBody),
                                                      101000,
                                                      1,
                                                      2);

        // Get tiny url to download all
        String json = payload(resultActions);
        String downloadAll = JsonPath.read(json, "$.downloadAllScript");
        java.util.List<NameValuePair> pairs = URLEncodedUtils.parse(URI.create(downloadAll), Charset.defaultCharset());
        String tinyurl = pairs.stream()
                              .filter(pair -> "tinyurl".equals(pair.getName()))
                              .findFirst()
                              .orElseThrow(() -> new AssertionError("Tinyurl is required"))
                              .getValue();

        RequestBuilderCustomizer downloadCustomizer = customizer().expectStatusOk();
        downloadCustomizer.addParameter("tinyurl", tinyurl);
        //        downloadCustomizer.addHeader(HttpConstants.ACCEPT, MediaType.TEXT_PLAIN_VALUE);
        performDefaultGet(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                          + StacApiConstants.STAC_DOWNLOAD_ALL_COLLECTIONS_SCRIPT_SUFFIX,
                          downloadCustomizer,
                          "Cannot download all collections");
    }

    @Test
    public void given_oneCollection_when_prepareDownloadWithFilter_then_successfulRequest() {
        Map<String, SearchBody.QueryObject> iq = HashMap.of("version",
                                                            SearchBody.StringQueryObject.builder().eq("012").build());
        CollectionSearchBody.CollectionItemSearchBody collectionItemSearchBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                                                                                                                              .query(
                                                                                                                                  iq)
                                                                                                                              .build();
        prepareDownload(HashMap.of("L2_HR_RASTER_100m", collectionItemSearchBody), 101000, 1, 2);
    }

    @Test
    public void given_TwoCollection_when_prepareDownloadWithFilter_then_successfulRequest() {
        prepareDownload(HashMap.of("L2_HR_RASTER_100m", null, "L2_HR_RASTER_250m", null), 553000, 3, 5);
    }

    /**
     * See <a href="https://docs.spring.io/spring-framework/docs/current/reference/html/testing.html#spring-mvc-test-async-requests">...</a>
     * for testing asynchronous endpoints
     */
    @Test
    public void given_TwoCollection_when_download_then_successfulRequest() throws Exception {
        ResultActions resultActions = prepareDownload(HashMap.of("L2_HR_RASTER_100m", null, "L2_HR_RASTER_250m", null),
                                                      553000,
                                                      3,
                                                      5);

        // Get tiny url to download all
        String json = payload(resultActions);
        String downloadAll = JsonPath.read(json, "$.downloadAll");
        java.util.List<NameValuePair> pairs = URLEncodedUtils.parse(URI.create(downloadAll), Charset.defaultCharset());
        String tinyurl = pairs.stream()
                              .filter(pair -> "tinyurl".equals(pair.getName()))
                              .findFirst()
                              .orElseThrow(() -> new AssertionError("Tinyurl is required"))
                              .getValue();

        // Get mod_zip file
        RequestBuilderCustomizer downloadCustomizer = customizer().expectStatusOk();
        downloadCustomizer.addParameter("tinyurl", tinyurl);
        downloadCustomizer.addHeader(HttpConstants.ACCEPT, MediaType.TEXT_PLAIN_VALUE);
        performDefaultGet(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                          + StacApiConstants.STAC_DOWNLOAD_ALL_COLLECTIONS_AS_ZIP_SUFFIX,
                          downloadCustomizer,
                          "Cannot download all collections");
    }

    @Test
    public void given_oneCollection_when_download_then_successfulRequest() {
        ResultActions resultActions = prepareDownload(HashMap.of("L2_HR_RASTER_100m", null), 351000, 2, 3);

        // Get tiny url to download all
        String json = payload(resultActions);
        String downloadAll = JsonPath.read(json, "$.collections[0].sample.download");
        java.util.List<NameValuePair> pairs = URLEncodedUtils.parse(URI.create(downloadAll), Charset.defaultCharset());
        String tinyurl = pairs.stream()
                              .filter(pair -> "tinyurl".equals(pair.getName()))
                              .findFirst()
                              .orElseThrow(() -> new AssertionError("Tinyurl is required"))
                              .getValue();

        RequestBuilderCustomizer downloadCustomizer = customizer().expectStatusOk();
        downloadCustomizer.addParameter("tinyurl", tinyurl);
        downloadCustomizer.addHeader(HttpConstants.ACCEPT, MediaType.TEXT_PLAIN_VALUE);

        // Get mod_zip file
        ResultActions allItemsActions = performDefaultGet(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                                                          + StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_AS_ZIP_SUFFIX,
                                                          downloadCustomizer,
                                                          "Cannot download all collections",
                                                          "uselessCollectionId");
        String modZip4Collection = payload(allItemsActions);
        String[] lines = modZip4Collection.split("\n");
        // Expected 3 files (all files with RAWDATA type of all collection items)
        Assert.assertEquals(3, lines.length);

        // Get sample mod_zip file
        ResultActions sampleResultActions = performDefaultGet(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                                                              + StacApiConstants.STAC_DOWNLOAD_SAMPLE_BY_COLLECTION_AS_ZIP_SUFFIX,
                                                              downloadCustomizer,
                                                              "Cannot download all collections",
                                                              "uselessCollectionId");
        String sampleModZipFiles = payload(sampleResultActions);
        String[] sampleLines = sampleModZipFiles.split("\n");
        // Expected 1 or 2 files (first item files)
        Assert.assertTrue(3 > sampleLines.length);
    }

    /**
     * FIXME : pb de test en local ... renvoie 200 au lieu de 400!!!!! mais ok sous la PIC
     * FIXME : ce n'est pas le cas renvoie bien 200
     */
    @Test
    @Ignore("test ko... to rewrite")
    public void given_unknownTinyUrl_when_download_then_badRequest() {

        // Get mod_zip file
        RequestBuilderCustomizer downloadCustomizer = customizer().expectStatusBadRequest();
        downloadCustomizer.addParameter("tinyurl", "unknown-tiny-url");
        downloadCustomizer.addHeader(HttpConstants.ACCEPT, MediaType.TEXT_PLAIN_VALUE);
        performDefaultGet(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                          + StacApiConstants.STAC_DOWNLOAD_ALL_COLLECTIONS_AS_ZIP_SUFFIX,
                          downloadCustomizer,
                          "Cannot download all collections");
    }

    private ResultActions prepareDownload(Map<String, CollectionSearchBody.CollectionItemSearchBody> itemSearchBodies,
                                          long totalSize,
                                          long totalItems,
                                          long totalFiles) {
        return prepareDownload(itemSearchBodies, totalSize, totalItems, totalFiles, Boolean.TRUE);
    }

    private ResultActions prepareDownload(Map<String, CollectionSearchBody.CollectionItemSearchBody> itemSearchBodies,
                                          long totalSize,
                                          long totalItems,
                                          long totalFiles,
                                          Boolean appendAuthParameters) {

        java.util.List<FiltersByCollection.CollectionFilters> collectionFilters = prepareCollectionFilters(
            itemSearchBodies);

        FiltersByCollection downloadPreparationBody = null;
        if (appendAuthParameters == null) {
            downloadPreparationBody = FiltersByCollection.builder().collections(List.ofAll(collectionFilters)).build();
        } else {
            downloadPreparationBody = FiltersByCollection.builder()
                                                         .collections(List.ofAll(collectionFilters))
                                                         .appendAuthParameters(appendAuthParameters)
                                                         .build();
        }

        // Assertions
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("totalSize", totalSize);
        customizer.expectValue("totalItems", totalItems);
        customizer.expectValue("totalFiles", totalFiles);
        customizer.expectToHaveSize("collections", downloadPreparationBody.getCollections().size());

        return performDefaultPost(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                                  + StacApiConstants.STAC_DOWNLOAD_AS_ZIP_PREPARE_PATH_SUFFIX,
                                  downloadPreparationBody,
                                  customizer,
                                  "Download preparation failed");
    }

    /**
     * Get collection information
     *
     * @param itemSearchBodies item search bodies
     * @return result actions
     */
    private ResultActions getCollectionInformation(Map<String, CollectionSearchBody.CollectionItemSearchBody> itemSearchBodies) {

        java.util.List<FiltersByCollection.CollectionFilters> collectionFilters = prepareCollectionFilters(
            itemSearchBodies);

        FiltersByCollection filtersByCollection = FiltersByCollection.builder()
                                                                     .collections(List.ofAll(collectionFilters))
                                                                     .build();

        // Assertions
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectToHaveSize("collections", filtersByCollection.getCollections().size());

        return performDefaultPost(StacApiConstants.STAC_COLLECTION_INFORMATION_PATH,
                                  filtersByCollection,
                                  customizer,
                                  "Cannot get collection information");
    }

    /**
     * Prepare collection filters
     *
     * @param itemSearchBodies item search bodies
     * @return collection filters
     */
    private java.util.List<FiltersByCollection.CollectionFilters> prepareCollectionFilters(Map<String, CollectionSearchBody.CollectionItemSearchBody> itemSearchBodies) {

        // Search all collections
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        CollectionSearchBody body = CollectionSearchBody.builder().build();
        ResultActions resultActions = performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                                                         body,
                                                         customizer,
                                                         "Cannot search STAC collections");

        // Get urn from catalog
        String json = payload(resultActions);
        // Get collection length
        java.util.List<FiltersByCollection.CollectionFilters> collectionFilters = new ArrayList<>();
        int collectionNb = JsonPath.read(json, "$.collections.length()");
        for (int i = 0; i < collectionNb; i++) {
            String collectionDataType = JsonPath.read(json, "$.collections[" + i + "].summaries.hydro:data_type");
            if (itemSearchBodies.keySet().contains(collectionDataType)) {
                // Get collection urn
                String collectionId = JsonPath.read(json, "$.collections[" + i + "].id");
                // Build prepare body
                CollectionSearchBody.CollectionItemSearchBody itemBody = itemSearchBodies.get(collectionDataType)
                                                                                         .isDefined() ?
                    itemSearchBodies.get(collectionDataType).get() :
                    CollectionSearchBody.CollectionItemSearchBody.builder().build();
                collectionFilters.add(FiltersByCollection.CollectionFilters.builder()
                                                                           .collectionId(collectionId)
                                                                           .correlationId(UUID.randomUUID().toString())
                                                                           .filters(itemBody)
                                                                           .build());
            }
        }

        return collectionFilters;
    }
}
