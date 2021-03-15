package fr.cnes.regards.modules.catalog.stac.plugin.configuration.mapping;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.RegardsPropertyAccessor;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.StacPropertyConfiguration;
import fr.cnes.regards.modules.catalog.stac.testutils.gson.GsonAwareTest;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModelBuilder;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import fr.cnes.regards.modules.opensearch.service.cache.attributemodel.IAttributeFinder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class RegardsPropertyAccessorFactoryTest implements GsonAwareTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegardsPropertyAccessorFactoryTest.class);
    
    // @formatter:off

    Gson gson = gson();
    OffsetDateTime now = OffsetDateTime.now(ZoneId.of("UTC"));
    DataObject dataObject;
    IAttributeFinder finder = Mockito.mock(IAttributeFinder.class);
    RegardsPropertyAccessorFactory factory = new RegardsPropertyAccessorFactory(finder, gson);

    @Before
    public void init() throws Exception {

        String jsonObjectStr = "{ \"base\" : { \"unused\": {}, \"sub\" : {" +
                " \"someString\": \"someStringValue\", " +
                " \"someURL\": \"http://xkcd.com/1\", " +
                " \"someDouble\": 15, " +
                " \"someBoolean\": false, " +
                " \"someDate\": " + gson.toJson(now) + " " +
                " } } }";

        LOGGER.info("JSON is: {}", jsonObjectStr);

        JsonObject jsonObject = gson.fromJson(jsonObjectStr, JsonObject.class);

        Model model = new Model();
        model.setName("theModelName");
        dataObject = new DataObject(model, "theTenant", "theProviderId", "theLabel");
        dataObject.getFeature().addProperty(IProperty.buildString("someStringProp", "someStringValue"));
        when(finder.findByName("someStringProp")).thenAnswer(i -> AttributeModelBuilder.build("someStringProp", PropertyType.STRING, "").get());

        dataObject.getFeature().addProperty(IProperty.buildBoolean("someBooleanProp", false));
        when(finder.findByName("someBooleanProp")).thenAnswer(i -> AttributeModelBuilder.build("someBooleanProp", PropertyType.BOOLEAN, "").get());

        dataObject.getFeature().addProperty(IProperty.buildDouble("someDoubleProp", 15d));
        when(finder.findByName("someDoubleProp")).thenAnswer(i -> AttributeModelBuilder.build("someDoubleProp", PropertyType.DOUBLE, "").get());

        dataObject.getFeature().addProperty(IProperty.buildUrl("someURLProp", "http://xkcd.com/1"));
        when(finder.findByName("someURLProp")).thenAnswer(i -> AttributeModelBuilder.build("someURLProp", PropertyType.URL, "").get());

        dataObject.getFeature().addProperty(IProperty.buildDate("someDateProp", now));
        when(finder.findByName("someDateProp")).thenAnswer(i -> AttributeModelBuilder.build("someDateProp", PropertyType.DATE_ISO8601, "").get());

        dataObject.getFeature().addProperty(IProperty.buildJson("someJsonObjectProp", jsonObject));
        when(finder.findByName("someJsonObjectProp")).thenAnswer(i -> AttributeModelBuilder.build("someJsonObjectProp", PropertyType.JSON, "").get());

    }

    @Test
    public void test_makeJsonURLPropAccessor() throws MalformedURLException {
        // GIVEN
        StacPropertyType sPropType = StacPropertyType.URL;
        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                "someJsonObjectProp",
                "base.sub.someURL",
                "regards:someJsonURLProp",
                "regards",
                false,
                null,
                sPropType.name(),
                null, null
        );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getRegardsAttributeName()).isEqualTo("someJsonObjectProp");
        assertThat(accessor.getValueType()).isEqualTo(URL.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.someJsonObjectProp.base.sub.someURL");
        assertThat(accessor.<URL>getGenericExtractValueFn().apply(dataObject).get()).isEqualTo(new URL("http://xkcd.com/1"));
    }


    @Test
    public void test_makeJsonBooleanPropAccessor() throws MalformedURLException {
        // GIVEN
        StacPropertyType sPropType = StacPropertyType.BOOLEAN;
        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                "someJsonObjectProp",
                "base.sub.someBoolean",
                "regards:someJsonBooleanProp",
                "regards",
                false,
                null,
                sPropType.name(),
                null, null
        );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getRegardsAttributeName()).isEqualTo("someJsonObjectProp");
        assertThat(accessor.getValueType()).isEqualTo(Boolean.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.someJsonObjectProp.base.sub.someBoolean");
        assertThat(accessor.<Boolean>getGenericExtractValueFn().apply(dataObject).get()).isEqualTo(false);
    }


    @Test
    public void test_makeJsonDoublePropAccessor() throws MalformedURLException {
        // GIVEN
        StacPropertyType sPropType = StacPropertyType.LENGTH;
        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                "someJsonObjectProp",
                "base.sub.someDouble",
                "regards:someJsonDoubleProp",
                "regards",
                false,
                null,
                sPropType.name(),
                null, null
        );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getRegardsAttributeName()).isEqualTo("someJsonObjectProp");
        assertThat(accessor.getValueType()).isEqualTo(Double.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.someJsonObjectProp.base.sub.someDouble");
        assertThat(accessor.<Double>getGenericExtractValueFn().apply(dataObject).get()).isEqualTo(15d);
    }

    @Test
    public void test_makeJsonDatePropAccessor() throws MalformedURLException {
        // GIVEN
        StacPropertyType sPropType = StacPropertyType.DATETIME;
        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                "someJsonObjectProp",
                "base.sub.someDate",
                "regards:someJsonDateProp",
                "regards",
                false,
                null,
                sPropType.name(),
                null, null
        );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getRegardsAttributeName()).isEqualTo("someJsonObjectProp");
        assertThat(accessor.getValueType()).isEqualTo(OffsetDateTime.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.someJsonObjectProp.base.sub.someDate");
        assertThat(accessor.<OffsetDateTime>getGenericExtractValueFn().apply(dataObject).get()).isEqualTo(now);
    }

    @Test
    public void test_makeJsonStringPropAccessor() throws MalformedURLException {
        // GIVEN
        StacPropertyType sPropType = StacPropertyType.STRING;
        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                "someJsonObjectProp",
                "base.sub.someString",
                "regards:someJsonStringProp",
                "regards",
                false,
                null,
                sPropType.name(),
                null, null
        );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getRegardsAttributeName()).isEqualTo("someJsonObjectProp");
        assertThat(accessor.getValueType()).isEqualTo(String.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.someJsonObjectProp.base.sub.someString");
        assertThat(accessor.<String>getGenericExtractValueFn().apply(dataObject).get()).isEqualTo("someStringValue");
    }

    @Test
    public void test_makeDatePropAccessor() throws MalformedURLException {
        // GIVEN
        StacPropertyType sPropType = StacPropertyType.DATETIME;
        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                "someDateProp",
                null,
                "regards:someDateProp",
                "regards",
                false,
                null,
                sPropType.name(),
                null, null
        );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getValueType()).isEqualTo(OffsetDateTime.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.someDateProp");
        assertThat(accessor.<OffsetDateTime>getGenericExtractValueFn().apply(dataObject)).contains(now);
    }

    @Test
    public void test_makeURLPropAccessor() throws MalformedURLException {
        // GIVEN
        StacPropertyType sPropType = StacPropertyType.URL;
        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                "someURLProp",
                null,
                "regards:someURLProp",
                "regards",
                false,
                null,
                sPropType.name(),
                null, null
        );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getValueType()).isEqualTo(URL.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.someURLProp");
        assertThat(accessor.<URL>getGenericExtractValueFn().apply(dataObject)).contains(new URL("http://xkcd.com/1"));
    }

    @Test
    public void test_makeStringPropAccessor() {
        // GIVEN
        StacPropertyType sPropType = StacPropertyType.STRING;
        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                "someStringProp",
                null,
                "regards:someStringProp",
                "regards",
                false,
                null,
                sPropType.name(),
                null, null
        );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getValueType()).isEqualTo(String.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.someStringProp");
        assertThat(accessor.<String>getGenericExtractValueFn().apply(dataObject)).contains("someStringValue");
    }

    @Test
    public void test_makeBooleanPropAccessor() {
        // GIVEN
        StacPropertyType sPropType = StacPropertyType.BOOLEAN;
        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                "someBooleanProp",
                null,
                "regards:someBooleanProp",
                "regards",
                false,
                null,
                sPropType.name(),
                null, null
        );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getValueType()).isEqualTo(Boolean.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.someBooleanProp");
        assertThat(accessor.<Boolean>getGenericExtractValueFn().apply(dataObject)).contains(false);
    }

    @Test
    public void test_makeDoublePropAccessor() {
        // GIVEN
        StacPropertyType sPropType = StacPropertyType.PERCENTAGE;
        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                "someDoubleProp",
                null,
                "regards:someDoubleProp",
                "regards",
                false,
                null,
                sPropType.name(),
                null, null
        );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getValueType()).isEqualTo(Double.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.someDoubleProp");
        assertThat(accessor.<Double>getGenericExtractValueFn().apply(dataObject)).contains(15d);
    }

}