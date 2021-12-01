package fr.cnes.regards.modules.catalog.stac.plugin.it.swot;

import com.jayway.jsonpath.JsonPath;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.security.utils.HttpConstants;
import fr.cnes.regards.framework.test.integration.RequestBuilderCustomizer;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.CollectionSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.DownloadPreparationBody;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.StacApiConstants;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.ModZipService;
import fr.cnes.regards.modules.search.domain.plugin.SearchEngineConfiguration;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.util.ArrayList;

/**
 * Cross layer integration test : from RESTful API to Elasticsearch index
 *
 * @author Marc SORDI
 */
@TestPropertySource(locations = { "classpath:test.properties" },
        properties = { "regards.tenant=swotv2", "spring.jpa.properties.hibernate.default_schema=swotv2" })
//@MultitenantTransactional
public class SwotV2EngineControllerIT extends AbstractSwotIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwotV2EngineControllerIT.class);

    @Autowired
    private ModZipService modZipService;

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

        SearchEngineConfiguration collectionConf = loadFromJson(
                getConfigFolder().resolve("STAC-collection-configuration-v2.json"), SearchEngineConfiguration.class);
        searchEngineService.createConf(collectionConf);
    }

    @Test
    @Ignore
    public void loadTest() {
        // Test initialization
    }

    /**
     * Basic COLLECTION search without criterion
     */
    @Test
    public void searchCollectionsAsPost() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        CollectionSearchBody body = CollectionSearchBody.builder().build();
        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH, body, customizer,
                           "Cannot search STAC collections");
    }

    /**
     * Basic ITEM search without criterion
     */
    @Test
    public void searchItemsAsPost() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        ItemSearchBody body = ItemSearchBody.builder().build();
        performDefaultPost(StacApiConstants.STAC_SEARCH_PATH, body, customizer, "Cannot search STAC items");
    }

    @Test
    public void searchCollectionsWithSpatioTemporalParameters() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        //        customizer.expectValue("$.context.matched", 1);
        //        customizer.expectValue("$.collections[0].title", "L1B HR SLC Title");
        // Define item criteria
        Map<String, SearchBody.QueryObject> iq = HashMap.of("start_datetime", SearchBody.DatetimeQueryObject.builder()
                                                                    .lte(OffsetDateTime.parse("2021-08-19T08:47:59.813Z")).build(), "end_datetime",
                                                            SearchBody.DatetimeQueryObject.builder().gte(OffsetDateTime
                                                                                                                 .parse("2021-07-19T08:47:59.813Z"))
                                                                    .build());
        BBox bbox = new BBox(1.1540490566502684, 43.498828738236014, 1.7531472622166748, 43.704683650908095);
        CollectionSearchBody.CollectionItemSearchBody itemBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                .bbox(bbox).query(iq).build();
        CollectionSearchBody body = CollectionSearchBody.builder().item(itemBody).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH, body, customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsWithItemVariables() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        //        customizer.expectValue("$.context.matched", 1);
        // Define item criteria
        Map<String, SearchBody.QueryObject> iq = HashMap.of("hydro:variables.uri",
                                                            SearchBody.StringQueryObject.builder()
                                                                    .contains("https://w3id.org/hysope2/waterLevel")
                                                                    .matchType("keyword").build());
        CollectionSearchBody.CollectionItemSearchBody itemBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                .query(iq).build();
        CollectionSearchBody body = CollectionSearchBody.builder().item(itemBody).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH, body, customizer,
                           "Cannot search STAC collections");
    }

    // FIXME https://odin.si.c-s.fr/plugins/tracker/?aid=131977
    @Test
    public void searchCollectionsWithItemVariablesAnd() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        //        customizer.expectValue("$.context.matched", 1);
        // Define item criteria
        // https://w3id.org/hysope2/waterLevel
        // https://w3id.org/hysope2/landWaterMask
        Map<String, SearchBody.QueryObject> iq = HashMap.of("hydro:variables.uri",
                                                            SearchBody.StringQueryObject.builder().containsAll(
                                                                    List.of("https://w3id.org/hysope2/waterLevel",
                                                                            "https://w3id.org/hysope2/landWaterMask"))
                                                                    .matchType("keyword").build(), "total_items",
                                                            SearchBody.NumberQueryObject.builder().gt(0D).build());
        CollectionSearchBody.CollectionItemSearchBody itemBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                .query(iq).build();
        //        CollectionSearchBody body = CollectionSearchBody.builder().item(itemBody).build();
        CollectionSearchBody body = CollectionSearchBody.builder().query(iq).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH, body, customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void given_noCollection_when_prepareDownload_then_badRequest() {
        RequestBuilderCustomizer customizer = customizer().expectStatusBadRequest();
        DownloadPreparationBody body = DownloadPreparationBody.builder().build();
        performDefaultPost(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                                   + StacApiConstants.STAC_DOWNLOAD_AS_ZIP_PREPARE_PATH_SUFFIX, body, customizer,
                           "Download preparation failed");
    }

    @Test
    public void given_notUrnCollection_when_prepareDownload_then_successfulRequest_withErrors() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectToHaveSize("collections", 1);
        customizer.expectToHaveSize("$.collections[0].errors", 1);
        DownloadPreparationBody.DownloadCollectionPreparationBody downloadCollectionPreparationBody = DownloadPreparationBody.DownloadCollectionPreparationBody
                .builder().collectionId("unknown").build();

        DownloadPreparationBody body = DownloadPreparationBody.builder()
                .collections(List.of(downloadCollectionPreparationBody)).build();
        performDefaultPost(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                                   + StacApiConstants.STAC_DOWNLOAD_AS_ZIP_PREPARE_PATH_SUFFIX, body, customizer,
                           "Download preparation failed");
    }

    @Test
    public void given_UnknownUrnCollection_when_prepareDownload_then_successfulRequest_withErrors() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectToHaveSize("collections", 1);
        customizer.expectToHaveSize("$.collections[0].errors", 1);
        DownloadPreparationBody.DownloadCollectionPreparationBody downloadCollectionPreparationBody = DownloadPreparationBody.DownloadCollectionPreparationBody
                .builder().collectionId("URN:AIP:DATASET:swotv2:b32d9001-4c15-4c57-841a-000000000000:V1").build();

        DownloadPreparationBody body = DownloadPreparationBody.builder()
                .collections(List.of(downloadCollectionPreparationBody)).build();
        performDefaultPost(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                                   + StacApiConstants.STAC_DOWNLOAD_AS_ZIP_PREPARE_PATH_SUFFIX, body, customizer,
                           "Download preparation failed");
    }

    @Test
    public void given_oneCollection_when_prepareDownloadWithoutFilter_then_successfulRequest() {
        prepareDownload(HashMap.of("L2_HR_RASTER_100m", null), 351000, 2, 3);
    }

    @Test
    public void given_oneCollection_when_prepareDownloadWithFilter_then_successfulRequest() {
        Map<String, SearchBody.QueryObject> iq = HashMap
                .of("version", SearchBody.StringQueryObject.builder().eq("012").build());
        CollectionSearchBody.CollectionItemSearchBody collectionItemSearchBody = CollectionSearchBody.CollectionItemSearchBody
                .builder().query(iq).build();
        prepareDownload(HashMap.of("L2_HR_RASTER_100m", collectionItemSearchBody), 101000, 1, 2);
    }

    @Test
    public void given_TwoCollection_when_prepareDownloadWithFilter_then_successfulRequest() {
        prepareDownload(HashMap.of("L2_HR_RASTER_100m", null, "L2_HR_RASTER_250m", null), 553000, 3, 5);
    }

    /**
     * See https://docs.spring.io/spring-framework/docs/current/reference/html/testing.html#spring-mvc-test-async-requests
     * for testing asynchronous endpoints
     */
    @Test
    public void given_TwoCollection_when_download_then_successfulRequest() throws Exception {
        ResultActions resultActions = prepareDownload(HashMap.of("L2_HR_RASTER_100m", null, "L2_HR_RASTER_250m", null),
                                                      553000, 3, 5);

        // Get tiny url to download all
        String json = payload(resultActions);
        String downloadAll = JsonPath.read(json, "$.downloadAll");
        java.util.List<NameValuePair> pairs = URLEncodedUtils.parse(URI.create(downloadAll), Charset.defaultCharset());
        String tinyurl = pairs.stream().filter(pair -> "tinyurl".equals(pair.getName())).findFirst().get().getValue();

        // Get mod_zip file
        RequestBuilderCustomizer downloadCustomizer = customizer().expectStatusOk();
        downloadCustomizer.addParameter("tinyurl", tinyurl);
        downloadCustomizer.addHeader(HttpConstants.ACCEPT, MediaType.TEXT_PLAIN_VALUE);
        performDefaultGet(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                                  + StacApiConstants.STAC_DOWNLOAD_ALL_COLLECTIONS_AS_ZIP_SUFFIX, downloadCustomizer,
                          "Cannot download all collections");
    }

    @Test
    public void given_unknownTinyUrl_when_download_then_badRequest() {

        // Get mod_zip file
        RequestBuilderCustomizer downloadCustomizer = customizer().expectStatusBadRequest();
        downloadCustomizer.addParameter("tinyurl", "unknown-tiny-url");
        downloadCustomizer.addHeader(HttpConstants.ACCEPT, MediaType.TEXT_PLAIN_VALUE);
        performDefaultGet(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                                  + StacApiConstants.STAC_DOWNLOAD_ALL_COLLECTIONS_AS_ZIP_SUFFIX, downloadCustomizer,
                          "Cannot download all collections");
    }

    private ResultActions prepareDownload(Map<String, CollectionSearchBody.CollectionItemSearchBody> itemSearchBodies,
            long totalSize, long totalItems, long totalFiles) {

        // Search all collections
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        CollectionSearchBody body = CollectionSearchBody.builder().build();
        ResultActions resultActions = performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH, body, customizer,
                                                         "Cannot search STAC collections");

        // Get urn from catalog
        String json = payload(resultActions);
        // Get collection length
        java.util.List<DownloadPreparationBody.DownloadCollectionPreparationBody> downloadCollectionPreparationBodies = new ArrayList<>();
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
                downloadCollectionPreparationBodies
                        .add(DownloadPreparationBody.DownloadCollectionPreparationBody.builder()
                                     .collectionId(collectionId).filters(itemBody).build());
            }
        }

        DownloadPreparationBody downloadPreparationBody = DownloadPreparationBody.builder()
                .collections(List.ofAll(downloadCollectionPreparationBodies)).build();

        // Assertions
        customizer = customizer().expectStatusOk();
        customizer.expectValue("totalSize", totalSize);
        customizer.expectValue("totalItems", totalItems);
        customizer.expectValue("totalFiles", totalFiles);
        customizer.expectToHaveSize("collections", downloadPreparationBody.getCollections().size());

        return performDefaultPost(StacApiConstants.STAC_DOWNLOAD_BY_COLLECTION_PATH
                                          + StacApiConstants.STAC_DOWNLOAD_AS_ZIP_PREPARE_PATH_SUFFIX,
                                  downloadPreparationBody, customizer, "Download preparation failed");
    }
}
