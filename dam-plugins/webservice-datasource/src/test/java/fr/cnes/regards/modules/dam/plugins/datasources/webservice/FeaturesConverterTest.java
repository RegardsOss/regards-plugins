package fr.cnes.regards.modules.dam.plugins.datasources.webservice;

import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import fr.cnes.regards.framework.geojson.FeatureWithPropertiesCollection;
import fr.cnes.regards.framework.jpa.utils.RegardsTransactional;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.oais.urn.DataType;
import fr.cnes.regards.framework.test.integration.AbstractRegardsServiceTransactionalIT;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;
import fr.cnes.regards.modules.dam.domain.entities.attribute.AbstractAttribute;
import fr.cnes.regards.modules.dam.domain.entities.attribute.ObjectAttribute;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.dam.domain.models.ModelAttrAssoc;
import fr.cnes.regards.modules.dam.domain.models.attributes.AttributeModel;
import fr.cnes.regards.modules.dam.domain.models.attributes.AttributeType;
import fr.cnes.regards.modules.dam.domain.models.attributes.Fragment;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.configuration.ConversionConfiguration;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.reports.ConversionReport;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.reports.FeatureErrors;
import fr.cnes.regards.modules.indexer.domain.DataFile;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Tests features conversion with results files
 */
@TestPropertySource(locations = {"classpath:test.properties"},
        properties = {"spring.jpa.properties.hibernate.default_schema=public"})
@RegardsTransactional
public class FeaturesConverterTest extends AbstractRegardsServiceTransactionalIT {

    @Autowired
    private Gson gson;

    private static AttributeModel mockAttributeModel(String path, AttributeType type, boolean optional) {
        AttributeModel attributeModel = new AttributeModel();
        List<String> pathElements = Arrays.asList(path.split("\\."));
        attributeModel.setName(pathElements.get(pathElements.size() - 1));
        attributeModel.setType(type);
        attributeModel.setOptional(optional);

        if (pathElements.size() > 1) {
            Fragment f = new Fragment();
            f.setName(pathElements.get(0));
            attributeModel.setFragment(f);
        }
        attributeModel.setJsonPath(path); // overwrites what the attribute built internally
        return attributeModel;
    }

    private static ModelAttrAssoc mockModelAttrAssoc(String path, AttributeType type, boolean optional) {
        ModelAttrAssoc assoc = new ModelAttrAssoc();
        assoc.setAttribute(mockAttributeModel(path, type, optional));
        return assoc;
    }

    private FeatureWithPropertiesCollection getFeatureCollection(String fileName) throws IOException {
        return gson.fromJson(new FileReader(new ClassPathResource(fileName).getFile()),
                FeatureWithPropertiesCollection.class);
    }

    /**
     * Test initialization fails when some attributes are not retrieved
     */
    @Test(expected = ModuleException.class)
    public void testInitFailsWithMissingAttributes() throws ModuleException {
        Map<String, String> attrMap = new HashMap<>();
        attrMap.put(StaticProperties.FEATURE_LABEL, "myLabel");
        attrMap.put(StaticProperties.FEATURE_PROVIDER_ID, "myProviderId");
        attrMap.put("fragment1.attr1", "myAttr1");
        attrMap.put("fragment1.attr2", "myAttr2");
        ConversionConfiguration conversionConfiguration = new ConversionConfiguration("some-model", attrMap,
                null, null, null, "totalResultsNotFound", "myPageSize");
        // provide corresponding model with only attr2
        new FeaturesConverter(conversionConfiguration, Collections.singletonList(
                mockModelAttrAssoc("fragment1.attr2", AttributeType.BOOLEAN, false)));
    }

    /**
     * Test initialization performs successfully when all attributes are retrieved
     */
    @Test
    public void testInitSuccessfullyWithAllAttributes() throws ModuleException {
        Map<String, String> attrMap = new HashMap<>();
        attrMap.put(StaticProperties.FEATURE_LABEL, "myLabel");
        attrMap.put(StaticProperties.FEATURE_PROVIDER_ID, "myProviderId");
        attrMap.put("fragment1.attr1", "myAttr1");
        attrMap.put("fragment1.attr2", "myAttr2");
        ConversionConfiguration conversionConfiguration = new ConversionConfiguration("some-model", attrMap,
                null, null, null, "totalResultsNotFound", "myPageSize");
        // provide all attributes models assoc
        new FeaturesConverter(conversionConfiguration, Arrays.asList(
                mockModelAttrAssoc("fragment1.attr1", AttributeType.INTEGER_ARRAY, false),
                mockModelAttrAssoc("fragment1.attr2", AttributeType.BOOLEAN, true)));
    }

    /**
     * Test conversion fails when total results cannot be retrieved
     */
    @Test(expected = DataSourceException.class)
    public void testFailsWithoutTotalCount() throws IOException, ModuleException, DataSourceException {
        ConversionConfiguration conversionConfiguration = new ConversionConfiguration("some-model", new HashMap<>(),
                null, null, null, "totalResultsNotFound", "myPageSize");

        // Conversion should throw error as page will not be found
        FeaturesConverter converter = new FeaturesConverter(conversionConfiguration, new ArrayList<>());
        converter.convert("any", PageRequest.of(0, 20),
                getFeatureCollection("dumps/test-0-feature-conversion.json"));
    }

    /**
     * Test conversion fails when page size cannot be retrieved
     */
    @Test(expected = DataSourceException.class)
    public void testFailsWithoutPageSize() throws IOException, ModuleException, DataSourceException {
        ConversionConfiguration conversionConfiguration = new ConversionConfiguration("some-model", new HashMap<>(),
                null, null, null, "myTotalResults", "pageSizeNotFound");


        // Conversion should throw error as page will not be found
        FeaturesConverter converter = new FeaturesConverter(conversionConfiguration, new ArrayList<>());
        converter.convert("any", PageRequest.of(0, 20),
                getFeatureCollection("dumps/test-0-feature-conversion.json"));
    }

    /**
     * Test conversion is OK when both page size and total results are found (no element to convert, conversion itself is tested later).
     * Check that final total count is 0 (ignore server value when returned value is lower)
     */
    @Test
    public void testRetrievesMetadataUpdatingTotalCountSuccessfully() throws IOException, ModuleException, DataSourceException {
        ConversionConfiguration conversionConfiguration = new ConversionConfiguration("some-model", new HashMap<>(),
                null, null, null, "myTotalResults", "myPageSize");

        // Conversion should throw no error as page and total can be found
        FeaturesConverter converter = new FeaturesConverter(conversionConfiguration, new ArrayList<>());
        converter.convert("any", PageRequest.of(0, 20),
                getFeatureCollection("dumps/test-0-feature-conversion.json"));

        Page<DataObjectFeature> convertedPage = converter.getConvertedPage();
        Assert.assertTrue("No element should have been converted", convertedPage.getContent().isEmpty());
        Assert.assertEquals("Total results should have been correctly retrieved and cut down to 0 as there were no feature", 0, convertedPage.getTotalElements());
        Assert.assertEquals("Page size should have been correctly retrieved", 45, convertedPage.getSize());
    }

    /**
     * Test conversion simple conversion (label and provider ID only)
     */
    @Test
    public void testSimpleConversion() throws IOException, ModuleException, DataSourceException {
        Map<String, String> attrMap = new HashMap<>();
        attrMap.put(StaticProperties.FEATURE_LABEL, "label");
        attrMap.put(StaticProperties.FEATURE_PROVIDER_ID, "productIdentifier");
        ConversionConfiguration conversionConfiguration = new ConversionConfiguration("some-model", attrMap,
                null, null, null, "totalResults", "itemsPerPage");

        FeaturesConverter converter = new FeaturesConverter(conversionConfiguration, new ArrayList<>());
        converter.convert("any", PageRequest.of(0, 20),
                getFeatureCollection("dumps/test-2-features-conversion.json"));
        // Check no error was reported
        ConversionReport report = converter.getReport();
        Assert.assertFalse("No blocking error should have occurred", report.hasBlockingErrors());
        Assert.assertFalse("No non blocking error should have occurred", report.hasNonBlockingErrors());
        // Check converted page
        Page<DataObjectFeature> convertedPage = converter.getConvertedPage();
        Assert.assertEquals("Total results should have been correctly retrieved", 50, convertedPage.getTotalElements());
        Assert.assertEquals("Page size should have been correctly retrieved", 50, convertedPage.getSize());

        List<DataObjectFeature> content = convertedPage.getContent();
        Assert.assertEquals("2 features should have been converted", 2, content.size());
        // Check feature 1
        Assert.assertEquals(content.get(0).getLabel(), "Feature 1");
        Assert.assertEquals(content.get(0).getProviderId(), "ProviderId1");
        // Check feature 2
        Assert.assertEquals(content.get(1).getLabel(), "Feature 2");
        Assert.assertEquals(content.get(1).getProviderId(), "ProviderId2");
    }

    /**
     * Same test than before with all possible errors:
     * - missing label
     * - missing providerId
     * - missing mandatory attribute
     * - non convertible mandatory attribute
     * - mandatory attribute where one parent in path is not an object
     * - non convertible optional attribute
     * - option attribute where one parent in path is not an object
     * - invalid URL raw data
     * - non explicit MIME type thumbnail
     * - invalid MIME type for picture Quicklook
     * - One working feature just for fun xD
     */
    @Test
    public void testAllConversionErrors() throws IOException, ModuleException, DataSourceException {
        Map<String, String> attrMap = new HashMap<>();
        attrMap.put(StaticProperties.FEATURE_LABEL, "wrongLabel");
        attrMap.put(StaticProperties.FEATURE_PROVIDER_ID, "wrongProductIdentifier");
        attrMap.put("notFoundMandatoryAttr", "notFoundMandatoryAttr1");
        attrMap.put("notConvertibleMandatoryAttr", "someIntValue");
        attrMap.put("invalidPathValueMandatoryAttr", "someIntValue.x");
        attrMap.put("notConvertibleOptionalAttr", "someIntValue");
        attrMap.put("invalidPathValueOptionalAttr", "someIntValue.y");

        attrMap.put("workingAttr", "someIntValue");
        ConversionConfiguration conversionConfiguration = new ConversionConfiguration("some-model", attrMap,
                "files.invalid.thumbnail", "files.invalid.rawdata", "files.invalid.quicklook",
                "totalResults", "itemsPerPage");

        FeaturesConverter converter = new FeaturesConverter(conversionConfiguration,
                Arrays.asList(
                        mockModelAttrAssoc("notFoundMandatoryAttr", AttributeType.STRING, false),
                        mockModelAttrAssoc("notConvertibleMandatoryAttr", AttributeType.STRING_ARRAY, false),
                        mockModelAttrAssoc("invalidPathValueMandatoryAttr", AttributeType.DATE_ISO8601, false),
                        mockModelAttrAssoc("notConvertibleOptionalAttr", AttributeType.BOOLEAN, true),
                        mockModelAttrAssoc("invalidPathValueOptionalAttr", AttributeType.INTEGER_ARRAY, true),
                        mockModelAttrAssoc("workingAttr", AttributeType.INTEGER, false)));
        converter.convert("any", PageRequest.of(0, 20),
                getFeatureCollection("dumps/test-2-features-conversion.json"));
        // Check no feature could be converted
        Assert.assertTrue("No feature should have been converted", converter.getConvertedPage().getContent().isEmpty());

        // Check that there are 2 features in errors (both mandatory and optional)
        ConversionReport report = converter.getReport();
        // check blocking errors
        SortedMap<Integer, FeatureErrors> blockingErrors = report.getBlockingErrors();
        Assert.assertEquals("There should be 2 features blocking errors groups in report", 2, blockingErrors.size());
        for (FeatureErrors f : blockingErrors.values()) {
            Assert.assertEquals("There should be 5 blocking errors for the feature", 5, f.getErrors().size());
            // Test each error is present (using parts of the error message)
            Assert.assertEquals("There should be error for label", 1,
                    f.getErrors().stream().filter(err -> err.getMessage().contains("mandatory attribute 'label' value was not found")).count());
            Assert.assertEquals("There should be error for providerId", 1,
                    f.getErrors().stream().filter(err -> err.getMessage().contains("mandatory attribute 'providerId' value was not found")).count());
            Assert.assertEquals("There should be error for invalidPathValueMandatoryAttr", 1,
                    f.getErrors().stream().filter(err -> err.getMessage().contains("mandatory attribute 'invalidPathValueMandatoryAttr' value could not be retrieved")).count());
            Assert.assertEquals("There should be error for notFoundMandatoryAttr", 1,
                    f.getErrors().stream().filter(err -> err.getMessage().contains("mandatory attribute 'notFoundMandatoryAttr' value was not found")).count());
            Assert.assertEquals("There should be error for notConvertibleMandatoryAttr", 1,
                    f.getErrors().stream().filter(err -> err.getMessage().contains("mandatory attribute 'notConvertibleMandatoryAttr' value")).count());
        }

        // check non blocking errors
        SortedMap<Integer, FeatureErrors> nonBlockingErrors = report.getNonBlockingErrors();
        Assert.assertEquals("There should be 2 features non blocking errors groups in report", 2, nonBlockingErrors.size());
        for (FeatureErrors f : nonBlockingErrors.values()) {
            Assert.assertEquals("There should be 5 non blocking errors for the feature", 5, f.getErrors().size());
            // Test each error is present (using parts of the error message)
            Assert.assertEquals("There should be error for invalidPathValueOptionalAttr", 1,
                    f.getErrors().stream().filter(err -> err.getMessage().contains("optional attribute 'invalidPathValueOptionalAttr' value could not be retrieved")).count());
            Assert.assertEquals("There should be error for notConvertibleOptionalAttr", 1,
                    f.getErrors().stream().filter(err -> err.getMessage().contains("optional attribute 'notConvertibleOptionalAttr' value")).count());
            Assert.assertEquals("There should be error for QUICKLOOK_SD", 1,
                    f.getErrors().stream().filter(err -> err.getMessage().contains("QUICKLOOK_SD file MIME type, 'application/pdf', is not a supported")).count());
            Assert.assertEquals("There should be error for RAWDATA", 1,
                    f.getErrors().stream().filter(err -> err.getMessage().contains("RAWDATA file URL,")).count());
            Assert.assertEquals("There should be error for THUMBNAIL", 1,
                    f.getErrors().stream().filter(err -> err.getMessage().contains("THUMBNAIL file MIME type could not be inferred")).count());
        }
    }

    /**
     * Tests a conversion with fragment and files:
     * - label <- label
     * - providerId <- productIdentifier
     * - (*) intAttr <- someIntValue
     * - (*) f1.attrA <- frag.val1
     * - (*) f1.attrB <- frag.val2
     * - (OPT) attrC <- frag.subfrag.val3
     * - rawdata <- files.valid.rawdata
     * - thumbnail <- files.valid.thumbnail
     * - quicklook <- files.valid.quicklook
     */
    @Test
    public void testFullConversion() throws IOException, ModuleException, DataSourceException {
        Map<String, String> attrMap = new HashMap<>();
        attrMap.put(StaticProperties.FEATURE_LABEL, "label");
        attrMap.put(StaticProperties.FEATURE_PROVIDER_ID, "productIdentifier");
        attrMap.put("intAttr", "#255"); // constant input value
        attrMap.put("f1.attrA", "frag.val1");
        attrMap.put("f1.attrB", "frag.val2");
        attrMap.put("attrC", "frag.subfrag.val3");
        ConversionConfiguration conversionConfiguration = new ConversionConfiguration("some-model", attrMap,
                "files.valid.thumbnail", "files.valid.rawdata", "files.valid.quicklook", "totalResults", "itemsPerPage");

        FeaturesConverter converter = new FeaturesConverter(conversionConfiguration, Arrays.asList(
                mockModelAttrAssoc("intAttr", AttributeType.INTEGER, false),
                mockModelAttrAssoc("f1.attrA", AttributeType.BOOLEAN, false),
                mockModelAttrAssoc("f1.attrB", AttributeType.STRING, false),
                mockModelAttrAssoc("attrC", AttributeType.INTEGER_ARRAY, true)));

        converter.convert("any", PageRequest.of(0, 20),
                getFeatureCollection("dumps/test-2-features-conversion.json"));
        // Check no error occurred
        ConversionReport report = converter.getReport();
        Assert.assertFalse("No blocking error should have occurred", report.hasBlockingErrors());
        Assert.assertFalse("No non blocking error should have occurred", report.hasNonBlockingErrors());
        // Check features content
        Page<DataObjectFeature> convertedPage = converter.getConvertedPage();
        Assert.assertEquals(50, convertedPage.getSize());
        Assert.assertEquals(50, convertedPage.getTotalElements());
        List<DataObjectFeature> features = convertedPage.getContent();
        Assert.assertEquals("There should be 2 converted features", 2, features.size());

        // First feature
        DataObjectFeature feat1 = features.get(0);
        Assert.assertEquals("Feat1: label should be correctly converted", "Feature 1", feat1.getLabel());
        Assert.assertEquals("Feat1: provider ID should be correctly converted", "ProviderId1", feat1.getProviderId());

        Assert.assertNotNull("Feat1: intAttr should be present", feat1.getProperty("intAttr"));
        Assert.assertEquals("Feat1: intAttr should be correctly converted (constant)", 255, feat1.getProperty("intAttr").getValue());

        ObjectAttribute f1 = (ObjectAttribute) feat1.getProperty("f1");
        Assert.assertNotNull("Feat1: Fragment f1 should have been created", f1);

        Optional<AbstractAttribute<?>> f1AttrA = f1.getValue().stream().filter(p -> p.getName().equals("attrA")).findFirst();
        Assert.assertTrue("Feat1: f1.AttrA should be present", f1AttrA.isPresent());
        Assert.assertEquals("Feat1: f1.AttrA value should be correctly converted", true, f1AttrA.get().getValue());

        Optional<AbstractAttribute<?>> f1AttrB = f1.getValue().stream().filter(p -> p.getName().equals("attrB")).findFirst();
        Assert.assertTrue("Feat1: f1.AttrB should be present", f1AttrB.isPresent());
        Assert.assertEquals("Feat1: f1.AttrB value should be correctly converted", "anyVal1", f1AttrB.get().getValue());


        Assert.assertNotNull("Feat1: attrC should be present", feat1.getProperty("attrC"));
        Object attrCValue = feat1.getProperty("attrC").getValue();
        Assert.assertNotNull("Feat1: attrC value should be present", attrCValue);
        Assert.assertTrue("Feat1: attrC value should be an array", attrCValue.getClass().isArray());
        Assert.assertEquals("Feat1: attrC value should be correctly converted", Arrays.asList(1, 2, 3), Arrays.asList((Object[]) attrCValue));


        Collection<DataFile> files = feat1.getFiles().get(DataType.RAWDATA);
        Assert.assertEquals("Feat1: 1 raw data file should have been retrieved", 1, files.size());
        Assert.assertEquals("Feat1: Raw data file path should have been correctly set", 1, files.stream().filter(f -> "http://valid.com/rawdata.pdf".equals(f.getUri())).count());
        files = feat1.getFiles().get(DataType.QUICKLOOK_SD);
        Assert.assertEquals("Feat1: 1 quicklook file should have been retrieved", 1, files.size());
        Assert.assertEquals("Feat1: Quicklook file path should have been correctly set", 1, files.stream().filter(f -> "http://valid.com/quicklook.jpg".equals(f.getUri())).count());
        files = feat1.getFiles().get(DataType.THUMBNAIL);
        Assert.assertEquals("Feat1: 1 thumbnail file should have been retrieved", 1, files.size());
        Assert.assertEquals("Feat1: Thumnail file path should have been correctly set", 1, files.stream().filter(f -> "http://valid.com/thumbnail.png".equals(f.getUri())).count());

        // Second feature
        DataObjectFeature feat2 = features.get(1);
        Assert.assertEquals("Feat2: label should be correctly converted", "Feature 2", feat2.getLabel());
        Assert.assertEquals("Feat2: provider ID should be correctly converted", "ProviderId2", feat2.getProviderId());

        Assert.assertNotNull("Feat2: intAttr should be present", feat2.getProperty("intAttr"));
        Assert.assertEquals("Feat2: intAttr should be correctly converted (constant)", 255, feat2.getProperty("intAttr").getValue());

        f1 = (ObjectAttribute) feat2.getProperty("f1");
        Assert.assertNotNull("Feat2: Fragment f1 should have been created", f1);

        f1AttrA = f1.getValue().stream().filter(p -> p.getName().equals("attrA")).findFirst();
        Assert.assertTrue("Feat2: f1.AttrA should be present", f1AttrA.isPresent());
        Assert.assertEquals("Feat2: f1.AttrA value should be correctly converted", false, f1AttrA.get().getValue());

        f1AttrB = f1.getValue().stream().filter(p -> p.getName().equals("attrB")).findFirst();
        Assert.assertTrue("Feat2: f1.AttrB should be present", f1AttrB.isPresent());
        Assert.assertEquals("Feat2: f1.AttrB value should be correctly converted", "anyVal2", f1AttrB.get().getValue());

        AbstractAttribute<?> attrC = feat2.getProperty("attrC");
        Assert.assertNotNull("Feat2: attrC should be present", attrC);
        attrCValue = attrC.getValue();
        Assert.assertNotNull("Feat2: attrC value should be present", attrCValue);
        Assert.assertTrue("Feat2: attrC value should be an array", attrCValue.getClass().isArray());
        Assert.assertEquals("Feat2: attrC value should be empty", Collections.emptyList(), Arrays.asList((Object[]) attrCValue));

        files = feat2.getFiles().get(DataType.RAWDATA);
        Assert.assertEquals("Feat2: 1 raw data file should have been retrieved", 1, files.size());
        Assert.assertEquals("Feat2: Raw data file path should have been correctly set", 1, files.stream().filter(f -> "http://valid.com/rawdata2.xls".equals(f.getUri())).count());
        files = feat2.getFiles().get(DataType.QUICKLOOK_SD);
        Assert.assertEquals("Feat2: 1 quicklook file should have been retrieved", 1, files.size());
        Assert.assertEquals("Feat2: Quicklook file path should have been correctly set", 1, files.stream().filter(f -> "http://valid.com/quicklook2.jpg".equals(f.getUri())).count());
        files = feat2.getFiles().get(DataType.THUMBNAIL);
        Assert.assertEquals("Feat2: 1 thumbnail file should have been retrieved", 1, files.size());
        Assert.assertEquals("Feat2: Thumnail file path should have been correctly set", 1, files.stream().filter(f -> "http://valid.com/thumbnail2.gif".equals(f.getUri())).count());
    }


    /**
     * Tests complex conversion with THEIA dump:
     * - (geometry <- geometry)
     * - label <- productIdentifier
     * - providerId <- productIdentifier
     * - period.startDate <- startDate
     * - period.endDate <- completionDate
     * - columns <- nb_cols
     * - rows <- nb_rows
     * - thumbnail <- thumbnail
     * - raw data <- services.download.url
     * - quicklook <- quicklook
     */
    @Test
    public void testTHEIAConversion() throws IOException, ModuleException, DataSourceException {
        Map<String, String> attrMap = new HashMap<>();
        attrMap.put(StaticProperties.FEATURE_LABEL, "productIdentifier");
        attrMap.put(StaticProperties.FEATURE_PROVIDER_ID, "productIdentifier");
        attrMap.put("period.startDate", "startDate");
        attrMap.put("period.endDate", "completionDate");
        attrMap.put("column", "nb_cols");
        attrMap.put("rows", "nb_rows");
        ConversionConfiguration conversionConfiguration = new ConversionConfiguration("some-model", attrMap,
                "thumbnail", "services.download.url", "quicklook", "totalResults", "itemsPerPage");

        FeaturesConverter converter = new FeaturesConverter(conversionConfiguration, Arrays.asList(
                mockModelAttrAssoc("period.startDate", AttributeType.DATE_ISO8601, false),
                mockModelAttrAssoc("period.endDate", AttributeType.DATE_ISO8601, false),
                mockModelAttrAssoc("column", AttributeType.LONG, false),
                mockModelAttrAssoc("rows", AttributeType.LONG, false)));

        converter.convert("any", PageRequest.of(0, 20),
                getFeatureCollection("dumps/theia-dump.json"));
        // Check no error occurred
        ConversionReport report = converter.getReport();
        Assert.assertFalse("No blocking error should have occurred", report.hasBlockingErrors());
        Assert.assertFalse("No non blocking error should have occurred", report.hasNonBlockingErrors());
        // Check page was correctly parsed (total count should not be reduced as page is complete)
        Page<DataObjectFeature> convertedPage = converter.getConvertedPage();
        Assert.assertEquals("Page size should be correctly converted", 20, convertedPage.getSize());
        Assert.assertEquals("Total size should be correctly converted and not reduced", 5884, convertedPage.getTotalElements());
        List<DataObjectFeature> features = convertedPage.getContent();
        Assert.assertEquals("All elements should have been converted", 20, features.size());

        // Check each feature has the right attributes set
        for (DataObjectFeature feat : features) {
            // check geomerty is set
            Assert.assertNotNull(feat.getGeometry());
            // check all attributes are set
            for (String attrPath : attrMap.keySet()) {
                if (attrPath.equals(StaticProperties.FEATURE_LABEL)) {
                    Assert.assertNotNull("Label should not be null ", feat.getLabel());
                } else if (attrPath.equals(StaticProperties.FEATURE_PROVIDER_ID)) {
                    Assert.assertNotNull("Provider ID should not be null in feature " + feat.getLabel(), feat.getProviderId());
                } else {
                    List<String> pathElements = Arrays.asList(attrPath.split("\\."));
                    String name = pathElements.get(pathElements.size() - 1);
                    if (pathElements.size() > 1) {
                        // check attribute in fragment
                        String fragmentName = pathElements.get(0);
                        ObjectAttribute fragment = (ObjectAttribute) feat.getProperty(fragmentName);
                        Assert.assertNotNull("There should be fragment " + fragmentName + " in feature " + feat.getLabel(), fragment);
                        Optional<AbstractAttribute<?>> optAttr = fragment.getValue().stream().filter(attr -> attr.getName().equals(name)).findFirst();
                        Assert.assertTrue("There should be attr " + name + " in fragment " + fragmentName + "(" + feat.getLabel() + ")", optAttr.isPresent());
                        Assert.assertNotNull("Attr " + name + " should not be null in fragment " + fragmentName + "(" + feat.getLabel() + ")", optAttr.get().getValue());
                    } else {
                        // check root attribute
                        AbstractAttribute<?> attribute = feat.getProperty(name);
                        Assert.assertNotNull("There should be attribute " + name + " in feature " + feat.getLabel(), attribute);
                        Assert.assertNotNull("Attribute " + name + " value should not be null in feature " + feat.getLabel(), attribute.getValue());
                    }
                }
            }
            Multimap<DataType, DataFile> files = feat.getFiles();
            Assert.assertNotNull(files.get(DataType.THUMBNAIL));
            Assert.assertNotNull(files.get(DataType.RAWDATA));
            Assert.assertNotNull(files.get(DataType.QUICKLOOK_SD));
        }
    }


}
