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

import com.jayway.jsonpath.JsonPath;
import fr.cnes.regards.framework.gson.adapters.OffsetDateTimeAdapter;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.test.integration.RequestBuilderCustomizer;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.CollectionSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.FiltersByCollection;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.TimelineFiltersByCollection;
import fr.cnes.regards.modules.catalog.stac.plugin.it.AbstractStacIT;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.StacApiConstants;
import fr.cnes.regards.modules.dam.domain.entities.Dataset;
import fr.cnes.regards.modules.search.domain.plugin.SearchEngineConfiguration;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Timeline integration testing
 *
 * @author Marc SORDI
 */
@TestPropertySource(locations = { "classpath:test.properties" },
    properties = { "regards.tenant=timeline", "spring.jpa.properties.hibernate.default_schema=timeline" })
public class TimelineIT extends AbstractStacIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineIT.class);

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

    @Test
    public void unknown_collection_timeline() {
        // GIVEN
        FiltersByCollection.CollectionFilters collectionFilters = FiltersByCollection.CollectionFilters.builder()
                                                                                                       .collectionId(
                                                                                                           "unknown:collection")
                                                                                                       .build();
        TimelineFiltersByCollection body = TimelineFiltersByCollection.timelineCollectionFiltersBuilder()
                                                                      .collections(List.of(collectionFilters))
                                                                      .build();

        // THEN
        RequestBuilderCustomizer customizer = customizer().expectStatusBadRequest();

        // WHEN
        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH + StacApiConstants.COLLECTIONS_TIMELINE,
                           body,
                           customizer,
                           "Timeline retrieve failed");

    }

    @Test
    public void nominal_timeline() {
        // GIVEN
        Map<String, SearchBody.QueryObject> iq = HashMap.of("version",
                                                            SearchBody.StringQueryObject.builder().eq("012").build());
        CollectionSearchBody.CollectionItemSearchBody collectionItemSearchBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                                                                                                                              .query(
                                                                                                                                  iq)
                                                                                                                              .build();

        Dataset collection = this.getDatasets().get("SWOT_L2_HR_Raster_250m_timeline");
        FiltersByCollection.CollectionFilters collectionFilters = FiltersByCollection.CollectionFilters.builder()
                                                                                                       .collectionId("SWOT_L2_HR_Raster_250m_timeline")
                                                                                                       .filters(
                                                                                                           collectionItemSearchBody)
                                                                                                       .build();

        TimelineFiltersByCollection body = TimelineFiltersByCollection.timelineCollectionFiltersBuilder()
                                                                      .from("2020-04-11T11:21:39Z")
                                                                      .to("2020-04-13T11:21:43Z")
                                                                      .mode(TimelineFiltersByCollection.TimelineMode.HISTOGRAM_MAP)
                                                                      .collections(List.of(collectionFilters))
                                                                      .build();

        // THEN
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectToHaveSize("timelines", 1);
        customizer.expectToHaveToString("$.timelines[0].timeline", "{2020-04-11=1, 2020-04-12=1, 2020-04-13=1}");

        // WHEN
        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH + StacApiConstants.COLLECTIONS_TIMELINE,
                           body,
                           customizer,
                           "Timeline retrieve failed");

    }

    @Test
    public void overlap_timeline_bounds() {
        // GIVEN
        Map<String, SearchBody.QueryObject> iq = HashMap.of("version",
                                                            SearchBody.StringQueryObject.builder().eq("012").build());
        CollectionSearchBody.CollectionItemSearchBody collectionItemSearchBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                                                                                                                              .query(
                                                                                                                                  iq)
                                                                                                                              .build();

        Dataset collection = this.getDatasets().get("SWOT_L2_HR_Raster_250m_timeline");
        FiltersByCollection.CollectionFilters collectionFilters = FiltersByCollection.CollectionFilters.builder()
                                                                                                       .collectionId("SWOT_L2_HR_Raster_250m_timeline")
                                                                                                       .filters(
                                                                                                           collectionItemSearchBody)
                                                                                                       .build();

        TimelineFiltersByCollection body = TimelineFiltersByCollection.timelineCollectionFiltersBuilder()
                                                                      .from("2020-04-09T11:21:39Z")
                                                                      .to("2020-04-11T11:21:43Z")
                                                                      .mode(TimelineFiltersByCollection.TimelineMode.HISTOGRAM_MAP)
                                                                      .collections(List.of(collectionFilters))
                                                                      .build();
        // THEN
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectToHaveToString("$.timelines[0].timeline", "{2020-04-09=0, 2020-04-10=1, 2020-04-11=1}");

        // WHEN
        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH + StacApiConstants.COLLECTIONS_TIMELINE,
                           body,
                           customizer,
                           "Timeline retrieve failed");
    }

    @Test
    public void before_timeline_bounds() {
        // GIVEN
        Map<String, SearchBody.QueryObject> iq = HashMap.of("version",
                                                            SearchBody.StringQueryObject.builder().eq("012").build());
        CollectionSearchBody.CollectionItemSearchBody collectionItemSearchBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                                                                                                                              .query(
                                                                                                                                  iq)
                                                                                                                              .build();

        Dataset collection = this.getDatasets().get("SWOT_L2_HR_Raster_250m_timeline");
        FiltersByCollection.CollectionFilters collectionFilters = FiltersByCollection.CollectionFilters.builder()
                                                                                                       .collectionId("SWOT_L2_HR_Raster_250m_timeline")
                                                                                                       .filters(
                                                                                                           collectionItemSearchBody)
                                                                                                       .build();

        TimelineFiltersByCollection body = TimelineFiltersByCollection.timelineCollectionFiltersBuilder()
                                                                      .from("2020-04-20T11:21:39Z")
                                                                      .to("2020-04-22T11:21:43Z")
                                                                      .mode(TimelineFiltersByCollection.TimelineMode.HISTOGRAM_MAP)
                                                                      .collections(List.of(collectionFilters))
                                                                      .build();

        // THEN
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectToHaveSize("timelines", 1);
        customizer.expectToHaveToString("$.timelines[0].timeline", "{2020-04-20=0, 2020-04-21=0, 2020-04-22=0}");

        // WHEN
        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH + StacApiConstants.COLLECTIONS_TIMELINE,
                           body,
                           customizer,
                           "Timeline retrieve failed");
    }

    @Test
    public void two_items_timeline() {
        // GIVEN
        Map<String, SearchBody.QueryObject> iq = HashMap.of("version",
                                                            SearchBody.StringQueryObject.builder().eq("012").build());
        CollectionSearchBody.CollectionItemSearchBody collectionItemSearchBody = CollectionSearchBody.CollectionItemSearchBody.builder()
                                                                                                                              .query(
                                                                                                                                  iq)
                                                                                                                              .build();

        Dataset collection = this.getDatasets().get("SWOT_L2_HR_Raster_250m_timeline");
        FiltersByCollection.CollectionFilters collectionFilters = FiltersByCollection.CollectionFilters.builder()
                                                                                                       .collectionId("SWOT_L2_HR_Raster_250m_timeline")
                                                                                                       .filters(
                                                                                                           collectionItemSearchBody)
                                                                                                       .build();

        TimelineFiltersByCollection body = TimelineFiltersByCollection.timelineCollectionFiltersBuilder()
                                                                      .from("2020-04-11T11:21:39Z")
                                                                      .to("2020-04-19T11:21:43Z")
                                                                      .mode(TimelineFiltersByCollection.TimelineMode.HISTOGRAM_MAP)
                                                                      .collections(List.of(collectionFilters))
                                                                      .build();

        // THEN
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectToHaveSize("timelines", 1);
        customizer.expectToHaveToString("$.timelines[0].timeline",
                                        "{2020-04-11=1, 2020-04-12=1, 2020-04-13=1, 2020-04-14=1, 2020-04-15=1, 2020-04-16=0, 2020-04-17=0, 2020-04-18=1, 2020-04-19=1}");

        // WHEN
        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH + StacApiConstants.COLLECTIONS_TIMELINE,
                           body,
                           customizer,
                           "Timeline retrieve failed");

    }

    @Test
    public void test_timeline_consistency() {
        String start = "1990-01-01";
        String end = "1990-01-10";
        String datasetId = "SWOT_L2_HR_Raster_250m_timeline_consistency";

        // Prepare request
        // Data filenames are marked with its matching days (look at resources directory)
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        Dataset dataset = this.getDatasets().get(datasetId);
        FiltersByCollection.CollectionFilters collectionFilters = FiltersByCollection.CollectionFilters.builder()
                                                                                                       .collectionId(datasetId)
                                                                                                       .correlationId(
                                                                                                           UUID.randomUUID()
                                                                                                               .toString())
                                                                                                       .build();

        // Do request with BINARY result
        TimelineFiltersByCollection body = TimelineFiltersByCollection.timelineCollectionFiltersBuilder()
                                                                      .from(start)
                                                                      .to(end)
                                                                      .mode(TimelineFiltersByCollection.TimelineMode.BINARY_MAP)
                                                                      .collections(List.of(collectionFilters))
                                                                      .build();
        ResultActions resultActions = performDefaultPost(
            StacApiConstants.STAC_COLLECTION_SEARCH_PATH + StacApiConstants.COLLECTIONS_TIMELINE,
            body,
            customizer,
            "Timeline retrieve " + "failed");
        // Do assertion
        String json = payload(resultActions);
        java.util.Map<String, Integer> timeline = JsonPath.read(json, "$.timelines[0].timeline");
        Assert.assertEquals(1, (int) timeline.get("1990-01-01"));
        Assert.assertEquals(1, (int) timeline.get("1990-01-02"));
        Assert.assertEquals(1, (int) timeline.get("1990-01-03"));
        Assert.assertEquals(1, (int) timeline.get("1990-01-04"));
        Assert.assertEquals(1, (int) timeline.get("1990-01-05"));
        Assert.assertEquals(1, (int) timeline.get("1990-01-06"));
        Assert.assertEquals(1, (int) timeline.get("1990-01-07"));
        Assert.assertEquals(1, (int) timeline.get("1990-01-08"));
        Assert.assertEquals(1, (int) timeline.get("1990-01-09"));
        Assert.assertEquals(0, (int) timeline.get("1990-01-10"));

        // Do request with HISTOGRAM result
        body = TimelineFiltersByCollection.timelineCollectionFiltersBuilder()
                                          .from(start)
                                          .to(end)
                                          .mode(TimelineFiltersByCollection.TimelineMode.HISTOGRAM_MAP)
                                          .collections(List.of(collectionFilters))
                                          .build();
        resultActions = performDefaultPost(
            StacApiConstants.STAC_COLLECTION_SEARCH_PATH + StacApiConstants.COLLECTIONS_TIMELINE,
            body,
            customizer,
            "Timeline retrieve failed");
        // Do assertion
        json = payload(resultActions);
        timeline = JsonPath.read(json, "$.timelines[0].timeline");
        Assert.assertEquals(3, (int) timeline.get("1990-01-01"));
        Assert.assertEquals(3, (int) timeline.get("1990-01-02"));
        Assert.assertEquals(2, (int) timeline.get("1990-01-03"));
        Assert.assertEquals(3, (int) timeline.get("1990-01-04"));
        Assert.assertEquals(2, (int) timeline.get("1990-01-05"));
        Assert.assertEquals(1, (int) timeline.get("1990-01-06"));
        Assert.assertEquals(2, (int) timeline.get("1990-01-07"));
        Assert.assertEquals(2, (int) timeline.get("1990-01-08"));
        Assert.assertEquals(3, (int) timeline.get("1990-01-09"));
        Assert.assertEquals(0, (int) timeline.get("1990-01-10"));
    }

    @Test
    public void test_timeline_serialization_consistency() {
        String start = "1990-01-01";
        String end = "1990-01-10";
        String datasetId = "SWOT_L2_HR_Raster_250m_timeline_consistency";

        // Prepare request
        // Data filenames are marked with its matching days (look at resources directory)
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        Dataset dataset = this.getDatasets().get(datasetId);
        FiltersByCollection.CollectionFilters collectionFilters = FiltersByCollection.CollectionFilters.builder()
                                                                                                       .collectionId(datasetId)
                                                                                                       .correlationId(
                                                                                                           UUID.randomUUID()
                                                                                                               .toString())
                                                                                                       .build();

        // Do request with BINARY result
        TimelineFiltersByCollection body = TimelineFiltersByCollection.timelineCollectionFiltersBuilder()
                                                                      .from(start)
                                                                      .to(end)
                                                                      .mode(TimelineFiltersByCollection.TimelineMode.BINARY)
                                                                      .collections(List.of(collectionFilters))
                                                                      .build();
        ResultActions resultActions = performDefaultPost(
            StacApiConstants.STAC_COLLECTION_SEARCH_PATH + StacApiConstants.COLLECTIONS_TIMELINE,
            body,
            customizer,
            "Timeline retrieve " + "failed");
        // Do assertion
        String json = payload(resultActions);
        java.util.List<Integer> timeline = JsonPath.read(json, "$.timelines[0].timeline");
        Assert.assertEquals(1, (int) timeline.get(0));
        Assert.assertEquals(1, (int) timeline.get(1));
        Assert.assertEquals(1, (int) timeline.get(2));
        Assert.assertEquals(1, (int) timeline.get(3));
        Assert.assertEquals(1, (int) timeline.get(4));
        Assert.assertEquals(1, (int) timeline.get(5));
        Assert.assertEquals(1, (int) timeline.get(6));
        Assert.assertEquals(1, (int) timeline.get(7));
        Assert.assertEquals(1, (int) timeline.get(8));
        Assert.assertEquals(0, (int) timeline.get(9));

        // Do request with HISTOGRAM result
        body = TimelineFiltersByCollection.timelineCollectionFiltersBuilder()
                                          .from(start)
                                          .to(end)
                                          .mode(TimelineFiltersByCollection.TimelineMode.HISTOGRAM)
                                          .collections(List.of(collectionFilters))
                                          .build();
        resultActions = performDefaultPost(
            StacApiConstants.STAC_COLLECTION_SEARCH_PATH + StacApiConstants.COLLECTIONS_TIMELINE,
            body,
            customizer,
            "Timeline retrieve failed");
        // Do assertion
        json = payload(resultActions);
        timeline = JsonPath.read(json, "$.timelines[0].timeline");
        Assert.assertEquals(3, (int) timeline.get(0));
        Assert.assertEquals(3, (int) timeline.get(1));
        Assert.assertEquals(2, (int) timeline.get(2));
        Assert.assertEquals(3, (int) timeline.get(3));
        Assert.assertEquals(2, (int) timeline.get(4));
        Assert.assertEquals(1, (int) timeline.get(5));
        Assert.assertEquals(2, (int) timeline.get(6));
        Assert.assertEquals(2, (int) timeline.get(7));
        Assert.assertEquals(3, (int) timeline.get(8));
        Assert.assertEquals(0, (int) timeline.get(9));
    }

    @Test
    public void test_timeline_serialization_consistency_with_unique_values() {
        String start = "1990-01-01";
        String end = "1990-01-05";
        String datasetId = "SWOT_L2_HR_Raster_250m_timeline_ser_consistency";

        // Prepare request
        // Data filenames are marked with its matching days (look at resources directory)
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        Dataset dataset = this.getDatasets().get(datasetId);
        FiltersByCollection.CollectionFilters collectionFilters = FiltersByCollection.CollectionFilters.builder()
                                                                                                       .collectionId(datasetId)
                                                                                                       .correlationId(
                                                                                                           UUID.randomUUID()
                                                                                                               .toString())
                                                                                                       .build();

        // Do request with BINARY result
        TimelineFiltersByCollection body = TimelineFiltersByCollection.timelineCollectionFiltersBuilder()
                                                                      .from(start)
                                                                      .to(end)
                                                                      .mode(TimelineFiltersByCollection.TimelineMode.BINARY)
                                                                      .collections(List.of(collectionFilters))
                                                                      .build();
        ResultActions resultActions = performDefaultPost(
            StacApiConstants.STAC_COLLECTION_SEARCH_PATH + StacApiConstants.COLLECTIONS_TIMELINE,
            body,
            customizer,
            "Timeline retrieve " + "failed");
        // Do assertion
        String json = payload(resultActions);
        java.util.List<Integer> timeline = JsonPath.read(json, "$.timelines[0].timeline");
        Assert.assertEquals(1, (int) timeline.get(0));
        Assert.assertEquals(1, (int) timeline.get(1));
        Assert.assertEquals(1, (int) timeline.get(2));
        Assert.assertEquals(1, (int) timeline.get(3));
        Assert.assertEquals(0, (int) timeline.get(4));

        // Do request with HISTOGRAM result
        body = TimelineFiltersByCollection.timelineCollectionFiltersBuilder()
                                          .from(start)
                                          .to(end)
                                          .mode(TimelineFiltersByCollection.TimelineMode.HISTOGRAM)
                                          .collections(List.of(collectionFilters))
                                          .build();
        resultActions = performDefaultPost(
            StacApiConstants.STAC_COLLECTION_SEARCH_PATH + StacApiConstants.COLLECTIONS_TIMELINE,
            body,
            customizer,
            "Timeline retrieve failed");
        // Do assertion
        json = payload(resultActions);
        timeline = JsonPath.read(json, "$.timelines[0].timeline");
        Assert.assertEquals(4, (int) timeline.get(0));
        Assert.assertEquals(3, (int) timeline.get(1));
        Assert.assertEquals(2, (int) timeline.get(2));
        Assert.assertEquals(1, (int) timeline.get(3));
        Assert.assertEquals(0, (int) timeline.get(4));
    }

    @Test
    public void test_default_values() {
        String datasetId = "SWOT_L2_HR_Raster_250m_timeline_ser_consistency";

        // Prepare request
        // Data filenames are marked with its matching days (look at resources directory)
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        customizer.expectToHaveSize("timelines", 1);

        // Pass body as pure JSON
        String body = "{\"collections\":[{\"collectionId\":\"" + datasetId + "\",\"correlationId\":\"selection_01\"}]}";

        ResultActions resultActions = performDefaultPost(
            StacApiConstants.STAC_COLLECTION_SEARCH_PATH + StacApiConstants.COLLECTIONS_TIMELINE,
            body,
            customizer,
            "Timeline retrieve " + "failed");

        // Do assertion
        String json = payload(resultActions);
        // Part of the response that must only contain 4 one in a row
        Assert.assertTrue(json.contains("0,0,0,1,1,1,1,0,0,0"));
    }

    @Test
    public void single_day_performance_with_10000_items_per_day() {
        String start = "1990-01-01";
        String end = "1990-01-31";
        String datasetId = "SWOT_L2_HR_Raster_250m_timeline_perf-single-day";

        // BINARY test
        long duration = performance_test("single-day-data-template.json",
                                         10000,
                                         1000,
                                         datasetId,
                                         start,
                                         end,
                                         TimelineFiltersByCollection.TimelineMode.BINARY,
                                         true);
        Assert.assertTrue("Expected max response time exceeded!", duration < 15000);

        // HISTOGRAM test
        duration = performance_test(datasetId, start, end, TimelineFiltersByCollection.TimelineMode.HISTOGRAM);
        Assert.assertTrue("Expected max response time exceeded!", duration < 15000);
    }

    /**
     * Test timeline performance with following context:
     * - from 1990 to today
     * - one item per day during 30 years
     * - no filter
     */
    @Test
    public void thirty_years_performance_with_one_day_items() {

        String start = "1990-01-01";
        //String end = OffsetDateTimeAdapter.format(OffsetDateTime.now());
        String end = "1990-01-31";
        String datasetId = "SWOT_L2_HR_Raster_250m_timeline_perf-one-day";

        // Generate thirty years of data ... 1 data per day
        // BINARY test
        long duration = performance_test("one-day-data-template.json",
                                         365 * 40,
                                         1000,
                                         datasetId,
                                         start,
                                         end,
                                         TimelineFiltersByCollection.TimelineMode.BINARY,
                                         true);
        Assert.assertTrue(String.format("Expected max response time exceeded! %d > 15000",duration), duration < 15000);

        // HISTOGRAM test
        duration = performance_test("one-day-data-template.json",
                                    365 * 40,
                                    1000,
                                    datasetId,
                                    start,
                                    end,
                                    TimelineFiltersByCollection.TimelineMode.HISTOGRAM,
                                    false);
        Assert.assertTrue("Expected max response time exceeded!", duration < 15000);
    }

    @Test
    public void thirty_years_performance_with_thirty_days_items() {

        String start = "1990-01-01T00:00:00";
        String end = OffsetDateTimeAdapter.format(OffsetDateTime.now());
        String datasetId = "SWOT_L2_HR_Raster_250m_timeline_perf-thirty-days";

        // Generate thirty years of data ... 1 to 30 data per day
        // BINARY test
        long duration = performance_test("thirty-days-data-template.json",
                                         365 * 40,
                                         1000,
                                         datasetId,
                                         start,
                                         end,
                                         TimelineFiltersByCollection.TimelineMode.BINARY,
                                         true);
        Assert.assertTrue("Expected max response time exceeded!", duration < 15000);

        // HISTOGRAM test
        duration = performance_test("thirty-days-data-template.json",
                                    365 * 40,
                                    1000,
                                    datasetId,
                                    start,
                                    end,
                                    TimelineFiltersByCollection.TimelineMode.HISTOGRAM,
                                    false);
        Assert.assertTrue("Expected max response time exceeded!", duration < 15000);
    }

    @Test
    public void thirty_years_performance_with_thirty_years_items() {

        String start = "1990-01-01T00:00:00";
        String end = OffsetDateTimeAdapter.format(OffsetDateTime.now());
        String datasetId = "SWOT_L2_HR_Raster_250m_timeline_perf-thirty-years";

        // Generate thirty years of data ... 1 to 10950 data per day
        // BINARY test
        long duration = performance_test("thirty-years-data-template.json",
                                         365 * 40,
                                         1000,
                                         datasetId,
                                         start,
                                         end,
                                         TimelineFiltersByCollection.TimelineMode.BINARY,
                                         true);
        Assert.assertTrue("Expected max response time exceeded!", duration < 15000);

        // HISTOGRAM test
        // Too long and not relevant
        //        duration = performance_test("thirty-years-data-template.json", 365 * 40, 1000, datasetId, start, end,
        //                                         TimelineCollectionsFilters.TimelineMode.HISTOGRAM, false);
        //        Assert.assertTrue("Expected max response time exceeded!", duration < 30000);
    }

    /**
     * Build timeline on specified period without generation
     *
     * @param datasetId     feature id of the dataset (must exist in datasets folder)
     * @param startTimeline timeline start date
     * @param endTimeline   timeline end date
     * @return request duration in ms
     */
    private long performance_test(String datasetId,
                                  String startTimeline,
                                  String endTimeline,
                                  TimelineFiltersByCollection.TimelineMode timelineMode) {
        return performance_test(null, null, null, datasetId, startTimeline, endTimeline, timelineMode, false);
    }

    /**
     * Generate date and build timeline on specified period
     *
     * @param templateFileName path to the template (must exist in templates folder)
     * @param iterations       number of data to generate
     * @param bulkSize         number of data to save per bulk
     * @param datasetId        feature id of the dataset (must exist in datasets folder)
     * @param startTimeline    timeline start date
     * @param endTimeline      timeline end date
     * @param generate         flag to launch generation or not
     * @return request duration in ms
     */
    private long performance_test(String templateFileName,
                                  Integer iterations,
                                  Integer bulkSize,
                                  String datasetId,
                                  String startTimeline,
                                  String endTimeline,
                                  TimelineFiltersByCollection.TimelineMode timelineMode,
                                  boolean generate) {

        if (generate) {
            // Generate according to specified template
            generateAndSave(templateFileName, iterations, bulkSize, datasetId);
        }

        // Prepare timeline request
        RequestBuilderCustomizer customizer = customizer().expectStatusOk();
        Dataset dataset = this.getDatasets().get(datasetId);
        FiltersByCollection.CollectionFilters collectionFilters = FiltersByCollection.CollectionFilters.builder()
                                                                                                       .collectionId(datasetId)
                                                                                                       .correlationId(
                                                                                                           UUID.randomUUID()
                                                                                                               .toString())
                                                                                                       .build();
        TimelineFiltersByCollection body = TimelineFiltersByCollection.timelineCollectionFiltersBuilder()
                                                                      .from(startTimeline)
                                                                      .to(endTimeline)
                                                                      .mode(timelineMode)
                                                                      .collections(List.of(collectionFilters))
                                                                      .build();

        // Do request
        long requestStart = System.currentTimeMillis();
        performDefaultPost(StacApiConstants.STAC_COLLECTION_SEARCH_PATH + StacApiConstants.COLLECTIONS_TIMELINE,
                           body,
                           customizer,
                           "Timeline retrieve failed");
        long requestDuration = System.currentTimeMillis() - requestStart;
        LOGGER.info("Timeline built in {} ms", requestDuration);
        return requestDuration;
    }
}
