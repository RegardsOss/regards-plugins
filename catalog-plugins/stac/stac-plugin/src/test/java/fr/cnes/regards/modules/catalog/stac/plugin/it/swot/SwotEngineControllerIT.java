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
package fr.cnes.regards.modules.catalog.stac.plugin.it.swot;

import com.google.gson.JsonObject;
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.framework.jpa.multitenant.transactional.MultitenantTransactional;
import fr.cnes.regards.framework.test.integration.RequestBuilderCustomizer;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.DateInterval;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody.StringQueryObject;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.CollectionSearchBody;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.StacApiConstants;
import fr.cnes.regards.modules.catalog.stac.service.collection.EsAggregationHelper;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.CollectionSearchService;
import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.opensearch.service.exception.OpenSearchUnknownParameter;
import fr.cnes.regards.modules.search.domain.plugin.SearchEngineMappings;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.ICatalogSearchService;
import fr.cnes.regards.modules.search.service.SearchException;
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
 * <p>
 * FIXME : add assertion everywhere
 */
@TestPropertySource(locations = { "classpath:test-local.properties" },
        properties = { "regards.tenant=swot", "spring.jpa.properties.hibernate.default_schema=swot" })
@MultitenantTransactional
public class SwotEngineControllerIT extends AbstractSwotIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwotEngineControllerIT.class);

    @Autowired
    private ICatalogSearchService catalogSearchService;

    @Autowired
    private CollectionSearchService collectionSearchService;

    @Autowired
    private EsAggregationHelper aggregationHelper;

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
                          customizer, "Cannot reach STAC static collections", "static");
    }

    @Test
    public void getDynamicCollections() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // TODO get JSON result and make assertion on expected collection links
        performDefaultGet(StacApiConstants.STAC_COLLECTIONS_PATH + StacApiConstants.STAC_COLLECTION_PATH_SUFFIX,
                          customizer, "Cannot reach STAC dynamic collections", "dynamic");
    }

    @Test
    public void getDynamicCollectionFirstLevelItems() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // L2_HR_RASTER URN
        String urn = "URN:DYNCOLL:eyJscyI6W3sicCI6Imh5ZHJvOmRhdGFfdHlwZSIsInYiOiJMMl9IUl9SQVNURVIifV19";
        performDefaultGet(StacApiConstants.STAC_COLLECTIONS_PATH + StacApiConstants.STAC_ITEMS_PATH_SUFFIX, customizer,
                          "Cannot reach STAC collection items", urn);
    }

    @Test
    public void searchItems() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.addParameter("datetime", "2022-01-01T00:00:00Z/2022-07-01T00:00:00Z");
        // customizer.addParameter("bbox", "1.4,43.5,1.5,43.6");
        performDefaultGet(StacApiConstants.STAC_SEARCH_PATH, customizer, "Cannot search STAC items");
    }

    @Test
    public void searchItemsAsPost() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        ItemSearchBody body = ItemSearchBody.builder()
                .datetime(DateInterval.parseDateInterval("2022-01-01T00:00:00Z/2022-07-01T00:00:00Z").get().get())
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
                .query(HashMap.of("hydro:data_type", StringQueryObject.builder().eq("L1B_HR_SLC").build())).build();
        performDefaultPost(StacApiConstants.STAC_SEARCH_PATH, body, customizer, "Cannot search STAC items");
    }

    @Test
    public void searchCollectionsAsPostWithItemParameter() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // Define item criteria
        Map<String, SearchBody.QueryObject> q = HashMap
                .of("hydro:data_type", StringQueryObject.builder().eq("L1B_HR_SLC").build());
        CollectionSearchBody.CollectionItemSearchBody itemBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                .query(q).build();
        CollectionSearchBody body = CollectionSearchBody.builder().item(itemBody).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH, body, customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsAsPostWithTitle() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // Define collection criteria
        Map<String, SearchBody.QueryObject> q = HashMap
                .of("title", StringQueryObject.builder().contains("L1B").build());
        CollectionSearchBody body = CollectionSearchBody.builder().query(q).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH, body, customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsAsPostWithDescription() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // Define collection criteria
        Map<String, SearchBody.QueryObject> q = HashMap
                .of("description", StringQueryObject.builder().contains("Description").build(), "description",
                    StringQueryObject.builder().contains("L2").build());
        CollectionSearchBody body = CollectionSearchBody.builder().query(q).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH, body, customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsAsPostWithKeywords() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // Define collection criteria
        Map<String, SearchBody.QueryObject> q = HashMap
                .of("keywords", StringQueryObject.builder().contains("Level 2").build());
        CollectionSearchBody body = CollectionSearchBody.builder().query(q).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH, body, customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsAsPostWithLicense() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // Define collection criteria
        Map<String, SearchBody.QueryObject> q = HashMap
                .of("license", StringQueryObject.builder().contains("LicenseOne").build());
        CollectionSearchBody body = CollectionSearchBody.builder().query(q).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH, body, customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsAsPostWithProviderName() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        // Define collection criteria
        Map<String, SearchBody.QueryObject> q = HashMap
                .of("providers.name", StringQueryObject.builder().contains("JPL").build());
        CollectionSearchBody body = CollectionSearchBody.builder().query(q).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH, body, customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsAsPostWithCollectionAndItemParameters() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 1);
        customizer.expectValue("$.collections[0].title", "L1B HR SLC Title");
        // Define item criteria
        Map<String, SearchBody.QueryObject> iq = HashMap
                .of("hydro:data_type", StringQueryObject.builder().eq("L1B_HR_SLC").build());
        CollectionSearchBody.CollectionItemSearchBody itemBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                .query(iq).build();
        // Define collection criteria
        Map<String, SearchBody.QueryObject> cq = HashMap
                .of("title", StringQueryObject.builder().contains("L1B").build());
        CollectionSearchBody body = CollectionSearchBody.builder().item(itemBody).query(cq).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH, body, customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsWithSpatioTemporalParameters() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        //        customizer.expectValue("$.context.matched", 1);
        //        customizer.expectValue("$.collections[0].title", "L1B HR SLC Title");
        // Define item criteria
        Map<String, SearchBody.QueryObject> iq = HashMap
                .of("hydro:data_type", StringQueryObject.builder().eq("L1B_HR_SLC").build());
        CollectionSearchBody.CollectionItemSearchBody itemBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                .intersects(IGeometry.simplePolygon(1.3, 43.5, 1.5, 43.5, 1.5, 43.6, 1.3, 43.6))
                .datetime(DateInterval.parseDateInterval("2022-01-01T00:00:00Z/2022-07-01T00:00:00Z").get().get())
                .build();
        // Define collection criteria
        Map<String, SearchBody.QueryObject> cq = HashMap
                .of("keywords", StringQueryObject.builder().contains("L2").build());
        CollectionSearchBody body = CollectionSearchBody.builder().item(itemBody).query(cq).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH, body, customizer,
                           "Cannot search STAC collections");
    }

    @Test
    public void searchCollectionsAsPostWithInconsistentCollectionAndItemParameters() {
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectValue("$.context.matched", 0);

        // Define item criteria
        Map<String, SearchBody.QueryObject> iq = HashMap
                .of("hydro:data_type", StringQueryObject.builder().eq("L1B_HR_SLC").build());
        CollectionSearchBody.CollectionItemSearchBody itemBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                .query(iq).build();
        // Define collection criteria
        Map<String, SearchBody.QueryObject> cq = HashMap
                .of("title", StringQueryObject.builder().contains("L2").build());
        CollectionSearchBody body = CollectionSearchBody.builder().item(itemBody).query(cq).build();

        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH, body, customizer,
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
    public void searchDataobjectsReturningDatasets() {

        // Search dataset
        String engine_type = "legacy";
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        addSearchTermQuery(customizer, "datetime", "[2020-01-01T00:00:00 TO 2020-06-01T00:00:00]");
        performDefaultGet(SearchEngineMappings.TYPE_MAPPING + SearchEngineMappings.SEARCH_DATAOBJECTS_DATASETS_MAPPING,
                          customizer, "Search all error", engine_type);

        //        addCommontMatchers(customizer);
        //        customizer.expect(MockMvcResultMatchers.jsonPath("$.content.length()", Matchers.equalTo(1)));
        //        addSearchTermQuery(customizer, STAR_SYSTEM, protect(SOLAR_SYSTEM));
        //        ResultActions result = performDefaultGet(SearchEngineMappings.TYPE_MAPPING
        //                + SearchEngineMappings.SEARCH_DATASETS_MAPPING, customizer, "Search all error", ENGINE_TYPE);
        //
        //        String datasetUrn = JsonPath.read(payload(result), "$.content[0].content.id");
        //
        //        customizer = customizer().expectStatusOk();
        //        addCommontMatchers(customizer);
        //        customizer.expect(MockMvcResultMatchers.jsonPath("$.links.length()", Matchers.equalTo(1)));
        //        customizer.expect(MockMvcResultMatchers.jsonPath("$.content[0].links.length()", Matchers.equalTo(1)));
        //        performDefaultGet(SearchEngineMappings.TYPE_MAPPING + SearchEngineMappings.SEARCH_DATASET_DATAOBJECTS_MAPPING,
        //                          customizer, "Search all error", ENGINE_TYPE, datasetUrn);
    }

    @Test
    public void searchTags() throws SearchException, OpenSearchUnknownParameter {
        String propertyPath = "tags";
        String partialText = "URN:AIP:DATASET";
        List<String> matchingDatasets = catalogSearchService
                .retrieveEnumeratedPropertyValues(ICriterion.all(), SearchType.DATAOBJECTS, propertyPath, 500,
                                                  partialText);
        LOGGER.info("List of matching datasets : {}", matchingDatasets);
    }

    @Test
    public void searchTagAggregation() throws SearchException, OpenSearchUnknownParameter {
        String aggregationName = "datasetIds";
        Aggregations aggregations = aggregationHelper.getDatasetAggregations(aggregationName, ICriterion.all(), 500);
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
}
