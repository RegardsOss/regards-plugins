package fr.cnes.regards.modules.dam.plugins.datasources.webservice;

import com.google.gson.Gson;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.notification.client.INotificationClient;
import fr.cnes.regards.framework.test.integration.AbstractRegardsServiceTransactionalIT;
import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.dam.domain.datasources.CrawlingCursor;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.configuration.ConversionConfiguration;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.configuration.WebserviceConfiguration;
import fr.cnes.regards.modules.indexer.domain.DataFile;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.model.domain.ModelAttrAssoc;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import fr.cnes.regards.modules.model.domain.attributes.Fragment;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import fr.cnes.regards.modules.model.dto.properties.ObjectProperty;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import fr.cnes.regards.modules.model.service.IModelAttrAssocService;
import org.apache.http.client.HttpClient;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@TestPropertySource(locations = { "classpath:test.properties" },
                    properties = { "spring.jpa.properties.hibernate.default_schema=public" })
public class WebserviceDatasourcePluginIT extends AbstractRegardsServiceTransactionalIT {

    @Autowired
    private INotificationClient notificationClient;

    @Autowired
    private HttpClient httpClient;

    @Autowired
    private Gson gson;

    private ModelAttrAssoc buildAttributeAssoc(String name, String fragmentName, PropertyType type, boolean optional) {
        ModelAttrAssoc modelAttrAssoc = new ModelAttrAssoc();

        Model model = new Model();
        model.setName("PATATO");
        model.setType(EntityType.DATA);
        modelAttrAssoc.setModel(model);

        AttributeModel attribute = new AttributeModel();
        attribute.setName(name);
        attribute.setType(type);
        attribute.setOptional(optional);
        if (fragmentName != null) {
            Fragment fragment = new Fragment();
            fragment.setName(fragmentName);
            attribute.setFragment(fragment);
        }
        modelAttrAssoc.setAttribute(attribute);
        return modelAttrAssoc;
    }

    /**
     * Tests theia like conversion. Nota: It is not a TU, but it helps here to know when the configuration does not work anymore
     */
    @Test
    @Ignore("Theia is actually inaccessible because of SSL issues")
    public void testTheiaLike() throws DataSourceException, ModuleException {
        // 1 - Mock returned model
        IModelAttrAssocService modelAttrAssocService = Mockito.mock(IModelAttrAssocService.class);
        Mockito.when(modelAttrAssocService.getModelAttrAssocs(Mockito.anyString()))
               .thenReturn(Arrays.asList(buildAttributeAssoc("start_date", null, PropertyType.DATE_ISO8601, false),
                                         buildAttributeAssoc("end_date", null, PropertyType.DATE_ISO8601, false),
                                         buildAttributeAssoc("product", null, PropertyType.STRING, true),
                                         buildAttributeAssoc("coordinates", null, PropertyType.INTEGER_ARRAY, true),
                                         buildAttributeAssoc("mission", null, PropertyType.STRING, false),
                                         buildAttributeAssoc("meas_instr", "measurement", PropertyType.STRING, true),
                                         buildAttributeAssoc("meas_resolution",
                                                             "measurement",
                                                             PropertyType.STRING,
                                                             true),
                                         buildAttributeAssoc("meas_sensor_mode",
                                                             "measurement",
                                                             PropertyType.STRING,
                                                             true)));

        // 2 - Create plugin configuration
        HashMap<String, String> attributeToJSonField = new HashMap<>();
        attributeToJSonField.put(StaticProperties.FEATURE_LABEL, "title");
        attributeToJSonField.put(StaticProperties.FEATURE_PROVIDER_ID, "productIdentifier");
        // dynamic model attributes
        attributeToJSonField.put("properties.start_date", "startDate");
        attributeToJSonField.put("properties.end_date", "completionDate");
        attributeToJSonField.put("properties.product", "productType");
        attributeToJSonField.put("properties.coordinates", "centroid.coordinates");
        attributeToJSonField.put("properties.mission", "collection");
        attributeToJSonField.put("properties.measurement.meas_instr", "instrument");
        attributeToJSonField.put("properties.measurement.meas_resolution", "resolution");
        attributeToJSonField.put("properties.measurement.meas_sensor_mode", "sensorMode");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("cloudCover", 30);
        WebserviceDatasourcePlugin pl = new WebserviceDatasourcePlugin(new WebserviceConfiguration(
            "https://theia.cnes.fr/atdistrib/resto2/api/collections/LANDSAT/search.json",
            "https://theia.cnes.fr/atdistrib/resto2/api/collections/describe.xml",
            "page",
            "maxRecords",
            "updated",
            1,
            500,
            parameters),
                                                                       new ConversionConfiguration("PATATO",
                                                                                                   attributeToJSonField,
                                                                                                   "thumbnail",
                                                                                                   "services.download.url",
                                                                                                   "quicklook",
                                                                                                   "totalResults",
                                                                                                   "itemsPerPage"),
                                                                       90000,
                                                                       modelAttrAssocService,
                                                                       notificationClient,
                                                                       httpClient,
                                                                       gson);
        pl.initialize();

        List<AttributeModel> expectedProps = modelAttrAssocService.getModelAttrAssocs("PATATO")
                                                                  .stream()
                                                                  .map(ModelAttrAssoc::getAttribute)
                                                                  .collect(Collectors.toList());
        DataType[] expectedFileTypes = new DataType[] { DataType.RAWDATA, DataType.QUICKLOOK_SD, DataType.THUMBNAIL };

        // Fetch all pages and check conversion is successful
        List<DataObjectFeature> result;
        CrawlingCursor cursor = new CrawlingCursor(0, 100);
        OffsetDateTime updateDate = OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        do {
            result = pl.findAll("myTenant", cursor, updateDate);
            // No error to report
            Assert.assertFalse("There should be no error in report", pl.getConverter().getReport().hasErrors());
            // Check that each feature has been fully converted
            for (DataObjectFeature f : result) {
                // Properties
                for (AttributeModel prop : expectedProps) {
                    IProperty<?> attr;
                    if (prop.hasFragment()) {
                        // in fragment prop
                        IProperty<?> fragment = f.getProperty(prop.getFragment().getName());
                        Assert.assertTrue("Fragment '"
                                          + prop.getFragment().getName()
                                          + "' should be present with the right type in feature "
                                          + f.getLabel(), fragment instanceof ObjectProperty);
                        Optional<IProperty<?>> optionalAttr = ((ObjectProperty) fragment).getValue()
                                                                                         .stream()
                                                                                         .filter(fragAttr -> fragAttr.getName()
                                                                                                                     .equals(
                                                                                                                         prop.getName()))
                                                                                         .findFirst();
                        Assert.assertTrue("Fragment '"
                                          + prop.getFragment().getName()
                                          + "' should contain property "
                                          + prop.getName()
                                          + " in feature "
                                          + f.getLabel(), optionalAttr.isPresent());
                        attr = optionalAttr.get();
                    } else {
                        // root prop
                        attr = f.getProperty(prop.getName());
                        Assert.assertNotNull("Property '" + "' should be present in feature " + f.getLabel(), attr);
                    }
                    Assert.assertNotNull("Property '" + "' value should not be null in feature " + f.getLabel(),
                                         attr.getValue());
                }
                // Files
                for (DataType type : expectedFileTypes) {
                    Collection<DataFile> dataFiles = f.getFiles().get(type);
                    Assert.assertEquals("There should be 1 data file for " + type + " in " + f.getProviderId(),
                                        1,
                                        dataFiles.size());
                    Assert.assertNotNull("Data file path for "
                                         + type
                                         + " in "
                                         + f.getProviderId()
                                         + " should not be null", dataFiles.iterator().next());
                }
            }

            // prepare for next page
            cursor.next();
        } while (cursor.hasNext());
    }

}
