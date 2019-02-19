package fr.cnes.regards.modules.dam.plugins.datasources.webservice;

import com.google.gson.Gson;
import fr.cnes.httpclient.HttpClient;
import fr.cnes.regards.framework.test.integration.AbstractRegardsServiceIT;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.configuration.ConversionConfiguration;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.configuration.WebserviceConfiguration;
import fr.cnes.regards.modules.dam.service.models.IModelAttrAssocService;
import fr.cnes.regards.modules.notification.client.INotificationClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.OffsetDateTime;
import java.util.HashMap;

@RunWith(SpringJUnit4ClassRunner.class)
@TestPropertySource(locations = {"classpath:test.properties"})
public class WebserviceDatasourcePluginTest extends AbstractRegardsServiceIT {

    @Autowired
    private IModelAttrAssocService modelAttrAssocService;

    @Autowired
    private INotificationClient notificationClient;

    @Autowired
    private HttpClient httpClient;

    @Autowired
    private Gson gson;

    // TODO correct or delete

    /**
     * Test failure behavior when data cannot be retrieved
     */
//    @Test(expected = DataSourceException.class)
//    public void testFailRetrieve() throws DataSourceException {
//        // TODO see with SVE for improved HTTP failure
//        WebserviceDatasourcePlugin pl = new WebserviceDatasourcePlugin(
//                new WebserviceConfiguration("http://localhost/idontexists", "pageIndex", "pageSize", "lastUpdateDate"),
//                new ConversionConfiguration("PATATO", new HashMap<>(), null, null, null), 90000,
//                modelAttrAssocService, notificationClient, httpClient, gson);
//        pl.findAll("testTenant", PageRequest.of(0, 20));
//    }

    /**
     * Test failure behavior when label value is missing in retrieved results
     */
//    @Test(expected = DataSourceException.class)
//    public void testSimpleConversionFailNoLabelValue() throws DataSourceException {
//        // TODO see with SVE for HTTP call
//        HashMap<String, String> attributeToJSonField = new HashMap<>();
//        attributeToJSonField.put(StaticProperties.FEATURE_LABEL, "idontexist");
//        attributeToJSonField.put(StaticProperties.FEATURE_PROVIDER_ID, "productIdentifier");
//        WebserviceDatasourcePlugin pl = new WebserviceDatasourcePlugin(
//                new WebserviceConfiguration("https://theia.cnes.fr/atdistrib/resto2/api/collections/LANDSAT/search.json", "pageIndex", "pageSize", "lastUpdateDate"),
//                new ConversionConfiguration("PATATO", attributeToJSonField, null, null, null), 90000,
//                modelAttrAssocService, notificationClient, httpClient, gson);
//        pl.findAll("testTenant", PageRequest.of(0, 20));
//    }

    /**
     * Test failure behavior when provider ID value is  cannot be retrieved
     */
//    @Test(expected = DataSourceException.class)
//    public void testSimpleConversionFailNoProviderIdValue() throws DataSourceException {
//        // TODO see with SVE for HTTP call
//        HashMap<String, String> attributeToJSonField = new HashMap<>();
//        attributeToJSonField.put(StaticProperties.FEATURE_LABEL, "title");
//        attributeToJSonField.put(StaticProperties.FEATURE_PROVIDER_ID, "idontexist");
//        WebserviceDatasourcePlugin pl = new WebserviceDatasourcePlugin(
//                new WebserviceConfiguration("https://theia.cnes.fr/atdistrib/resto2/api/collections/LANDSAT/search.json", "pageIndex", "pageSize", "lastUpdateDate"),
//                new ConversionConfiguration("PATATO", attributeToJSonField, null, null, null), 90000,
//                modelAttrAssocService, notificationClient, httpClient, gson);
//        pl.findAll("testTenant", PageRequest.of(0, 20));
//    }

    /**
     * Test simple successful conversion (only label and provider ID)
     */
//    @Test
//    public void testSimpleConversionSuccessful() throws DataSourceException {
//        // TODO see with SVE for HTTP call
//        HashMap<String, String> attributeToJSonField = new HashMap<>();
//        attributeToJSonField.put(StaticProperties.FEATURE_LABEL, "title");
//        attributeToJSonField.put(StaticProperties.FEATURE_PROVIDER_ID, "productIdentifier");
//        WebserviceDatasourcePlugin pl = new WebserviceDatasourcePlugin(
//                new WebserviceConfiguration("https://theia.cnes.fr/atdistrib/resto2/api/collections/LANDSAT/search.json", "pageIndex", "pageSize", "lastUpdateDate"),
//                new ConversionConfiguration("PATATO", attributeToJSonField, null, null, null), 90000,
//                modelAttrAssocService, notificationClient, httpClient, gson);
//        // TODO: expected results!
//        Page<DataObjectFeature> convertedPage = pl.findAll("testTenant", PageRequest.of(0, 20));
//    }


//    @Test
//    public void geoJSonClient() throws DataSourceException {
//        WebserviceDatasourcePlugin pl = new WebserviceDatasourcePlugin(
//                new WebserviceConfiguration("https://theia.cnes.fr/atdistrib/resto2/api/collections/LANDSAT/search.json", "pageIndex", "pageSize", "lastUpdateDate"),
//                new ConversionConfiguration("PATATO", new HashMap<>(), null, null, null), 90000,
//                modelAttrAssocService, notificationClient, httpClient, gson);
//
//        Page<DataObjectFeature> response;
//        Pageable page = PageRequest.of(0, 10000);
//        do {
//            response = pl.findAll("plop", page, OffsetDateTime.now().minusDays(10));
//            page = response.nextPageable();
//        } while ((response != null) && response.hasNext());
//
//        Assert.assertTrue(response.hasContent());
//    }

}
