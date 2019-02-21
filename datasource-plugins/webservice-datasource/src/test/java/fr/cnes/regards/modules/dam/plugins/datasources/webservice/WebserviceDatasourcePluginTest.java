package fr.cnes.regards.modules.dam.plugins.datasources.webservice;

import com.google.gson.Gson;
import fr.cnes.httpclient.HttpClient;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
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

    @Test
    public void doNothing(){
        // TODO delete this method (only for maven to compile with tests, temporary)
    }

    /**
     * Test simple successful conversion (only label and provider ID)
     */
    @Test
    public void testSimpleConversionSuccessful() throws DataSourceException, ModuleException {
        HashMap<String, String> attributeToJSonField = new HashMap<>();
        attributeToJSonField.put(StaticProperties.FEATURE_LABEL, "title");
        attributeToJSonField.put(StaticProperties.FEATURE_PROVIDER_ID, "productIdentifier");
        WebserviceDatasourcePlugin pl = new WebserviceDatasourcePlugin(
                new WebserviceConfiguration("https://theia.cnes.fr/atdistrib/resto2/api/collections/LANDSAT/search.json", "page", "maxRecords", null, 1, 20),
                new ConversionConfiguration("PATATO", attributeToJSonField, null, null, null, "totalResults", "itemsPerPage"), 90000,
                modelAttrAssocService, notificationClient, httpClient, gson);
        pl.initialize();
        // TODO: expected results!
        Page<DataObjectFeature> convertedPage = pl.findAll("testTenant", PageRequest.of(0, 20));
    }


}
