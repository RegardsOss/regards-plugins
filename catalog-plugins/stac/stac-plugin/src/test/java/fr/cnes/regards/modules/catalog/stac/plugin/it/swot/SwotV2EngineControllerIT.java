package fr.cnes.regards.modules.catalog.stac.plugin.it.swot;

import fr.cnes.regards.framework.jpa.multitenant.transactional.MultitenantTransactional;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.test.integration.RequestBuilderCustomizer;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.CollectionSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.StacApiConstants;
import fr.cnes.regards.modules.search.domain.plugin.SearchEngineConfiguration;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;

/**
 * Cross layer integration test : from RESTful API to Elasticsearch index
 *
 * @author Marc SORDI
 */
@TestPropertySource(locations = { "classpath:test.properties" },
        properties = { "regards.tenant=swotv2", "spring.jpa.properties.hibernate.default_schema=swotv2" })
@MultitenantTransactional
public class SwotV2EngineControllerIT extends AbstractSwotIT {

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
}
