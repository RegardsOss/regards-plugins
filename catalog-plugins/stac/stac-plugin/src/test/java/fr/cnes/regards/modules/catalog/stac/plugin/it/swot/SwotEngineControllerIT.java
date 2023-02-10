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
package fr.cnes.regards.modules.catalog.stac.plugin.it.swot;

import com.google.gson.JsonObject;
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.framework.jpa.multitenant.transactional.MultitenantTransactional;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.test.integration.RequestBuilderCustomizer;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.DateInterval;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody.StringQueryObject;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.CollectionSearchBody;
import fr.cnes.regards.modules.catalog.stac.plugin.it.AbstractStacIT;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.StacApiConstants;
import fr.cnes.regards.modules.catalog.stac.service.collection.EsAggregationHelper;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.CollectionSearchService;
import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.search.domain.plugin.SearchEngineConfiguration;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.ICatalogSearchService;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

/**
 * Cross layer integration test : from RESTful API to Elasticsearch index
 *
 * @author Marc SORDI
 */
@TestPropertySource(locations = { "classpath:test.properties" },
                    properties = { "regards.tenant=swot", "spring.jpa.properties.hibernate.default_schema=swot" })
@MultitenantTransactional
public class SwotEngineControllerIT extends AbstractStacIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwotEngineControllerIT.class);

    @Autowired
    private ICatalogSearchService catalogSearchService;

    @Autowired
    private CollectionSearchService collectionSearchService;

    @Autowired
    private EsAggregationHelper aggregationHelper;

    @Override
    protected String getDataFolderName() {
        return "swot_v1";
    }

    @Override
    protected String getDataModel() {
        return "data_model_hygor_V0.1.0.xml";
    }

    @Override
    protected String getDatasetModel() {
        return "dataset_model_hygor_V0.1.0.xml";
    }

    @Override
    protected void initPlugins() throws ModuleException {
        SearchEngineConfiguration conf = loadFromJson(getConfigFolder().resolve("STAC-engine-configuration.json"),
                                                      SearchEngineConfiguration.class);
        searchEngineService.createConf(conf);

        SearchEngineConfiguration collectionConf = loadFromJson(getConfigFolder().resolve(
            "STAC-collection-engine-configuration.json"), SearchEngineConfiguration.class);
        searchEngineService.createConf(collectionConf);
    }

    @Test
    public void getLandingPage() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        performDefaultGet(StacApiConstants.STAC_PATH, customizer, "Cannot reach STAC landing page");
    }

    @Test
    public void getConformancePage() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        performDefaultGet(StacApiConstants.STAC_CONFORMANCE_PATH, customizer, "Cannot reach STAC conformance page");
    }

    @Test
    public void getStaticCollections() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        performDefaultGet(StacApiConstants.STAC_COLLECTIONS_PATH + StacApiConstants.STAC_COLLECTION_PATH_SUFFIX,
                          customizer,
                          "Cannot reach STAC static collections",
                          "static");
    }

    @Test
    public void getDynamicCollections() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // TODO get JSON result and make assertion on expected collection links
        performDefaultGet(StacApiConstants.STAC_COLLECTIONS_PATH + StacApiConstants.STAC_COLLECTION_PATH_SUFFIX,
                          customizer,
                          "Cannot reach STAC dynamic collections",
                          "dynamic");
    }

    @Test
    public void getDynamicCollectionFirstLevelItems() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // L2_HR_RASTER URN
        String urn = "URN:DYNCOLL:eyJscyI6W3sicCI6Imh5ZHJvOmRhdGFfdHlwZSIsInYiOiJMMl9IUl9SQVNURVIifV19";
        performDefaultGet(StacApiConstants.STAC_COLLECTIONS_PATH + StacApiConstants.STAC_ITEMS_PATH_SUFFIX,
                          customizer,
                          "Cannot reach STAC collection items",
                          urn);
    }

    @Test
    public void searchItems() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.addParameter("datetime", "2022-01-01T00:00:00Z/2022-07-01T00:00:00Z");
        performDefaultGet(StacApiConstants.STAC_SEARCH_PATH, customizer, "Cannot search STAC items");
    }

    /**
     * bbox outside of authorized -360/+360 range
     */
    @Test
    public void searchItemsWithBbox_OutOfRange_withMaxExtent() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        customizer.expectValue("$.features[0].properties.version", "A");
        customizer.addParameter("bbox", "-230.0, 50.0, 380.0, 60.0"); // Match A between +50/+60
        performDefaultGet(StacApiConstants.STAC_SEARCH_PATH, customizer, "Cannot search STAC items");
    }

    /**
     * bbox minX < -360
     */
    @Test
    public void searchItemsWithBbox_OutOfRange_MinX() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        customizer.expectValue("$.features[0].properties.version", "A");
        customizer.addParameter("bbox", "-800.0, 50.0, -700.0, 60.0"); // Match A between +50/+60
        performDefaultGet(StacApiConstants.STAC_SEARCH_PATH, customizer, "Cannot search STAC items");
    }

    /**
     * bbox maxX > 360
     */
    @Test
    public void searchItemsWithBbox_OutOfRange_MaxX() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        customizer.expectValue("$.features[0].properties.version", "A");
        customizer.addParameter("bbox", "700.0, 50.0, 800.0, 60.0"); // Match A between +50/+60
        performDefaultGet(StacApiConstants.STAC_SEARCH_PATH, customizer, "Cannot search STAC items");
    }

    /**
     * Nominal use case : bbox between -180/+180 with lat between +50/+60
     */
    @Test
    public void searchItemsWithBbox_A() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        customizer.expectValue("$.features[0].properties.version", "A");
        customizer.addParameter("bbox", "-10.0,50.0,+10.0,60.0");
        performDefaultGet(StacApiConstants.STAC_SEARCH_PATH, customizer, "Cannot search STAC items");
    }

    /**
     * Nominal use case : bbox between -180/+360 with lat between +40/+50
     */
    @Test
    public void searchItemsWithBbox_B() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        customizer.expectValue("$.features[0].properties.version", "B");
        customizer.addParameter("bbox", "-70.0,40.0,190.0,50.0");
        performDefaultGet(StacApiConstants.STAC_SEARCH_PATH, customizer, "Cannot search STAC items");
    }

    /**
     * Nominal use case : bbox between -360/+180 with lat between +30/+40
     */
    @Test
    public void searchItemsWithBbox_C() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        customizer.expectValue("$.features[0].properties.version", "C");
        customizer.addParameter("bbox", "-200.0,30.0,70.0,40.0");
        performDefaultGet(StacApiConstants.STAC_SEARCH_PATH, customizer, "Cannot search STAC items");
    }

    /**
     * Nominal use case : bbox between -360/+360 with lat between +20/+30
     */
    @Test
    public void searchItemsWithBbox_D() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        customizer.expectValue("$.features[0].properties.version", "D");
        customizer.addParameter("bbox", "-200.0,20.0,200.0,30.0");
        performDefaultGet(StacApiConstants.STAC_SEARCH_PATH, customizer, "Cannot search STAC items");
    }

    /**
     * Nominal use case : bbox between +180/+360 with lat between +10/+20
     */
    @Test
    public void searchItemsWithBbox_E() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        customizer.expectValue("$.features[0].properties.version", "E");
        customizer.addParameter("bbox", "190.0,10.0,250.0,20.0");
        performDefaultGet(StacApiConstants.STAC_SEARCH_PATH, customizer, "Cannot search STAC items");
    }

    /**
     * Nominal use case : bbox between -360/-180 with lat between +0/+10
     */
    @Test
    public void searchItemsWithBbox_F() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        customizer.expectValue("$.features[0].properties.version", "F");
        customizer.addParameter("bbox", "-350.0,0.0,-300.0,10.0");
        performDefaultGet(StacApiConstants.STAC_SEARCH_PATH, customizer, "Cannot search STAC items");
    }

    @Test
    public void searchItemsAsPost() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        ItemSearchBody body = ItemSearchBody.builder()
                                            .datetime(DateInterval.parseDateInterval(
                                                "2022-01-01T00:00:00Z/2022-07-01T00:00:00Z").get().get())
                                            .build();
        performDefaultPost(StacApiConstants.STAC_SEARCH_PATH, body, customizer, "Cannot search STAC items");
    }

    @Test
    public void searchItemsAsPostWithUnknownProperty() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        JsonObject body = new JsonObject();
        body.addProperty("unknown", "L1B_HR_SLC");
        performDefaultPost(StacApiConstants.STAC_SEARCH_PATH, body, customizer, "Cannot search STAC items");
    }

    @Test
    public void searchItemsOfDataType() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        ItemSearchBody body = ItemSearchBody.builder()
                                            .query(HashMap.of("hydro:data_type",
                                                              StringQueryObject.builder().eq("L1B_HR_SLC").build()))
                                            .build();
        performDefaultPost(StacApiConstants.STAC_SEARCH_PATH, body, customizer, "Cannot search STAC items");
    }

    @Test
    public void searchCollectionsAsPostWithItemParameter() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // Define item criteria
        Map<String, SearchBody.QueryObject> q = HashMap.of("hydro:data_type",
                                                           StringQueryObject.builder().eq("L1B_HR_SLC").build());
        CollectionSearchBody.CollectionItemSearchBody itemBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                                                                                                              .query(q)
                                                                                                              .build();
        CollectionSearchBody body = CollectionSearchBody.builder().item(itemBody).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsAsPostWithTitle() {
        String text = "rast";
        searchCollectionsAsPostWithTitle(text, "keyword", 0);
        searchCollectionsAsPostWithTitle(text, "text", 1);
        searchCollectionsAsPostWithTitle(text, null, 1); // Same as text
        text = "RAST";
        searchCollectionsAsPostWithTitle(text, "keyword", 1);
        searchCollectionsAsPostWithTitle(text, "text", 1);
        searchCollectionsAsPostWithTitle(text, null, 1); // Same as text
    }

    private void searchCollectionsAsPostWithTitle(String text, String matchType, int matchedCollections) {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", matchedCollections);
        // Define collection criteria
        Map<String, SearchBody.QueryObject> q = HashMap.of("title",
                                                           StringQueryObject.builder()
                                                                            .contains(text)
                                                                            .matchType(matchType)
                                                                            .build());
        CollectionSearchBody body = CollectionSearchBody.builder().query(q).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsAsPostWithTitleWith2Value() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // customizer.expectValue("$.context.matched", 1);
        // Define collection criteria
        //        Map<String, SearchBody.QueryObject> q = HashMap
        //                .of("title", StringQueryObject.builder().containsAll(io.vavr.collection.List.of("hr slc")).matchType("text").build());
        Map<String, SearchBody.QueryObject> q = HashMap.of("title",
                                                           StringQueryObject.builder()
                                                                            .containsAll(io.vavr.collection.List.of(
                                                                                "ras",
                                                                                "HR"))
                                                                            .matchType("text")
                                                                            .build());
        CollectionSearchBody body = CollectionSearchBody.builder().query(q).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsAsPostWithDescription() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // Define collection criteria
        Map<String, SearchBody.QueryObject> q = HashMap.of("description",
                                                           StringQueryObject.builder().contains("Description").build(),
                                                           "description",
                                                           StringQueryObject.builder().contains("L2").build());
        CollectionSearchBody body = CollectionSearchBody.builder().query(q).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsAsPostWithKeywords() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // Define collection criteria
        Map<String, SearchBody.QueryObject> q = HashMap.of("keywords",
                                                           StringQueryObject.builder().contains("Level 2").build());
        CollectionSearchBody body = CollectionSearchBody.builder().query(q).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsAsPostWithLicense() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // Define collection criteria
        Map<String, SearchBody.QueryObject> q = HashMap.of("license",
                                                           StringQueryObject.builder().contains("LicenseOne").build());
        CollectionSearchBody body = CollectionSearchBody.builder().query(q).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsAsPostWithProviderName() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // Define collection criteria
        Map<String, SearchBody.QueryObject> q = HashMap.of("providers.name",
                                                           StringQueryObject.builder().contains("JPL").build());
        CollectionSearchBody body = CollectionSearchBody.builder().query(q).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsAsPostWithCollectionAndItemParameters() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        customizer.expectValue("$.collections[0].title", "L1B HR SLC Title");
        // Define item criteria
        Map<String, SearchBody.QueryObject> iq = HashMap.of("hydro:data_type",
                                                            StringQueryObject.builder().eq("L1B_HR_SLC").build());
        CollectionSearchBody.CollectionItemSearchBody itemBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                                                                                                              .query(iq)
                                                                                                              .build();
        // Define collection criteria
        Map<String, SearchBody.QueryObject> cq = HashMap.of("title",
                                                            StringQueryObject.builder().contains("L1B").build());
        CollectionSearchBody body = CollectionSearchBody.builder().item(itemBody).query(cq).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsWithSpatioTemporalParameters() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        //        customizer.expectValue("$.context.matched", 1);
        //        customizer.expectValue("$.collections[0].title", "L1B HR SLC Title");
        // Define item criteria
        Map<String, SearchBody.QueryObject> iq = HashMap.of("hydro:data_type",
                                                            StringQueryObject.builder().eq("L1B_HR_SLC").build());
        CollectionSearchBody.CollectionItemSearchBody itemBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                                                                                                              .intersects(
                                                                                                                  IGeometry.simplePolygon(
                                                                                                                      1.3,
                                                                                                                      43.5,
                                                                                                                      1.5,
                                                                                                                      43.5,
                                                                                                                      1.5,
                                                                                                                      43.6,
                                                                                                                      1.3,
                                                                                                                      43.6))
                                                                                                              .datetime(
                                                                                                                  DateInterval.parseDateInterval(
                                                                                                                                  "2022-01-01T00:00:00Z/2022-07-01T00:00:00Z")
                                                                                                                              .get()
                                                                                                                              .get())
                                                                                                              .build();
        // Define collection criteria
        Map<String, SearchBody.QueryObject> cq = HashMap.of("keywords",
                                                            StringQueryObject.builder()
                                                                             .contains("L2")
                                                                             .matchType("keyword")
                                                                             .build());
        CollectionSearchBody body = CollectionSearchBody.builder().item(itemBody).query(cq).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsAsPostWithInconsistentCollectionAndItemParameters() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 0);

        // Define item criteria
        Map<String, SearchBody.QueryObject> iq = HashMap.of("hydro:data_type",
                                                            StringQueryObject.builder().eq("L1B_HR_SLC").build());
        CollectionSearchBody.CollectionItemSearchBody itemBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                                                                                                              .query(iq)
                                                                                                              .build();
        // Define collection criteria
        Map<String, SearchBody.QueryObject> cq = HashMap.of("title",
                                                            StringQueryObject.builder().contains("L2").build());
        CollectionSearchBody body = CollectionSearchBody.builder().item(itemBody).query(cq).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH,
                           body,
                           customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsAsGet() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // customizer.addParameter("item_datetime", "2022-01-01T00:00:00Z/2022-07-01T00:00:00Z");
        customizer.addParameter("item_query", "{\"hydro:data_type\":{\"eq\":\"L1B_HR_SLC\"}}");
        // Define item criteria
        // Map<String, SearchBody.QueryObject> q = HashMap.of("hydro:data_type", StringQueryObject.builder().eq("L1B_HR_SLC").build());

        performDefaultGet(StacApiConstants.STAC_COLLECTION_SEARCH_PATH, customizer, "Cannot search STAC collections");
    }

    @Test
    public void searchTags() {
        String propertyPath = "tags";
        String partialText = "URN:AIP:DATASET";
        List<String> matchingDatasets = catalogSearchService.retrieveEnumeratedPropertyValues(ICriterion.all(),
                                                                                              SearchType.DATAOBJECTS,
                                                                                              propertyPath,
                                                                                              500,
                                                                                              partialText);
        LOGGER.info("List of matching datasets : {}", matchingDatasets);
    }

    @Test
    public void searchTagAggregation() {
        String aggregationName = "datasetIds";
        Aggregations aggregations = aggregationHelper.getDatasetAggregations(aggregationName, ICriterion.all(), 500L);
        Terms datasetIdsAgg = aggregations.get(aggregationName);
        for (Terms.Bucket bucket : datasetIdsAgg.getBuckets()) {
            LOGGER.info("DATASET {} has {} matching items", bucket.getKey(), bucket.getDocCount());
        }
    }

    /**
     * Add query to current request
     *
     * @param customizer           current {@link RequestBuilderCustomizer}
     * @param relativePropertyName name without properties prefix
     * @param value                the property value
     */
    private void addSearchTermQuery(RequestBuilderCustomizer customizer, String relativePropertyName, String value) {
        customizer.addParameter("q", StaticProperties.FEATURE_PROPERTIES + "." + relativePropertyName + ":" + value);
    }

    /**
     * Add full text query to current request
     */
    private void addFullTextSearchQuery(RequestBuilderCustomizer customizer, String value) {
        customizer.addParameter("q", value);
    }
}
