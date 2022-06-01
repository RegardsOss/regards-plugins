package fr.cnes.regards.modules.catalog.stac.plugin.configuration.mapping;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import fr.cnes.regards.framework.gson.adapters.OffsetDateTimeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.properties.RegardsPropertyAccessor;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.StacPropertyConfiguration;
import fr.cnes.regards.modules.catalog.stac.testutils.gson.GsonAwareTest;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModelBuilder;
import fr.cnes.regards.modules.model.domain.attributes.Fragment;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import fr.cnes.regards.modules.model.dto.properties.MarkdownURL;
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

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.info;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class RegardsPropertyAccessorFactoryTest implements GsonAwareTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegardsPropertyAccessorFactoryTest.class);

    // @formatter:off

    Gson gson = gson();
    OffsetDateTime now = OffsetDateTime.now(UTC);
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
                " \"someDate\": " + gson.toJson(OffsetDateTimeAdapter.format(now)) + " " +
                " } } }";

        info(LOGGER, "JSON is: {}", jsonObjectStr);

        JsonObject jsonObject = gson.fromJson(jsonObjectStr, JsonObject.class);

        Model model = new Model();
        model.setName("theModelName");
        dataObject = new DataObject(model, "theTenant", "theProviderId", "theLabel");
        dataObject.addProperty(IProperty.buildString("someStringProp", "someStringValue"));
        when(finder.findByName("someStringProp")).thenAnswer(i -> new AttributeModelBuilder("someStringProp", PropertyType.STRING, "").build());

        dataObject.addProperty(IProperty.buildBoolean("someBooleanProp", false));
        when(finder.findByName("someBooleanProp")).thenAnswer(i -> new AttributeModelBuilder("someBooleanProp", PropertyType.BOOLEAN, "").build());

        dataObject.addProperty(IProperty.buildDouble("someDoubleProp", 15d));
        when(finder.findByName("someDoubleProp")).thenAnswer(i -> new AttributeModelBuilder("someDoubleProp", PropertyType.DOUBLE, "").build());

        dataObject.addProperty(IProperty.buildUrl("someURLProp", "http://xkcd.com/1"));
        when(finder.findByName("someURLProp")).thenAnswer(i -> new AttributeModelBuilder("someURLProp", PropertyType.URL, "").build());

        dataObject.addProperty(IProperty.buildDate("someDateProp", now));
        when(finder.findByName("someDateProp")).thenAnswer(i -> new AttributeModelBuilder("someDateProp", PropertyType.DATE_ISO8601, "").build());

        dataObject.addProperty(IProperty.buildJson("someJsonObjectProp", jsonObject));
        when(finder.findByName("someJsonObjectProp")).thenAnswer(i -> new AttributeModelBuilder("someJsonObjectProp", PropertyType.JSON, "").build());

        dataObject.addProperty(IProperty.buildObject("anObject", IProperty.buildString("anObjectStringProp", "anObjectStringPropValue"),
                                                                 IProperty.buildString("anotherObjectStringProp", "anotherObjectStringPropValue")));
        when(finder.findByName("anObject.anObjectStringProp")).thenAnswer(i -> new AttributeModelBuilder("anObjectStringProp", PropertyType.STRING, "")
            .setFragment(Fragment.buildFragment("anObject", "")).build());
    }

    @Test
    public void test_makeJsonURLPropAccessor() throws MalformedURLException {
        // GIVEN
        StacPropertyType sPropType = StacPropertyType.URL;
        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                                      "someJsonObjectProp",
                                      "base.sub.someURL",
                                      null,
                                      null,
                                      "regards:someJsonURLProp",
                                      "regards",
                                      sPropType.name(),
                                      null,
                                      false,
                                      null,
                                      null
                              );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getRegardsAttributeName()).isEqualTo("someJsonObjectProp");
        assertThat(accessor.getValueType()).isEqualTo(URL.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.someJsonObjectProp");
        assertThat(accessor.<URL>getGenericExtractValueFn().apply(dataObject).get()).isEqualTo(new URL("http://xkcd.com/1"));
    }


    @Test
    public void test_makeJsonBooleanPropAccessor() throws MalformedURLException {
        // GIVEN
        StacPropertyType sPropType = StacPropertyType.BOOLEAN;
        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                                                                              "someJsonObjectProp",
                                                                              "base.sub.someBoolean",
                                                                              null,
                                                                              null,
                                                                              "regards:someJsonBooleanProp",
                                                                              "regards",
                                                                              sPropType.name(),
                                                                              null,
                                                                              false,
                                                                              null,
                                                                              null
                                                                      );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getRegardsAttributeName()).isEqualTo("someJsonObjectProp");
        assertThat(accessor.getValueType()).isEqualTo(Boolean.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.someJsonObjectProp");
        assertThat(accessor.<Boolean>getGenericExtractValueFn().apply(dataObject).get()).isEqualTo(false);
    }


    @Test
    public void test_makeJsonDoublePropAccessor() throws MalformedURLException {
        // GIVEN
        StacPropertyType sPropType = StacPropertyType.LENGTH;
        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                                                                              "someJsonObjectProp",
                                                                              "base.sub.someDouble",
                                                                              null,
                                                                              null,
                                                                              "regards:someJsonDoubleProp",
                                                                              "regards",
                                                                              sPropType.name(),
                                                                              null,
                                                                              false,
                                                                              null,
                                                                              null
                                                                      );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getRegardsAttributeName()).isEqualTo("someJsonObjectProp");
        assertThat(accessor.getValueType()).isEqualTo(Double.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.someJsonObjectProp");
        assertThat(accessor.<Double>getGenericExtractValueFn().apply(dataObject).get()).isEqualTo(15d);
    }

    @Test
    public void test_makeJsonDatePropAccessor() throws MalformedURLException {
        // GIVEN
        StacPropertyType sPropType = StacPropertyType.DATETIME;

        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                                                                              "someJsonObjectProp",
                                                                              "base.sub.someDate",
                                                                              null,
                                                                              null,
                                                                              "regards:someJsonDateProp",
                                                                              "regards",
                                                                              sPropType.name(),
                                                                              null,
                                                                              false,
                                                                              null,
                                                                              null
                                                                      );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getRegardsAttributeName()).isEqualTo("someJsonObjectProp");
        assertThat(accessor.getValueType()).isEqualTo(OffsetDateTime.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.someJsonObjectProp");
        assertThat(accessor.<OffsetDateTime>getGenericExtractValueFn().apply(dataObject).get()).isEqualTo(OffsetDateTimeAdapter.format(now));
    }

    @Test
    public void test_makeJsonStringPropAccessor() throws MalformedURLException {
        // GIVEN
        StacPropertyType sPropType = StacPropertyType.STRING;
        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                                                                              "someJsonObjectProp",
                                                                              "base.sub.someString",
                                                                              null,
                                                                              null,
                                                                              "regards:someJsonStringProp",
                                                                              "regards",
                                                                              sPropType.name(),
                                                                              null,
                                                                              false,
                                                                              null,
                                                                              null
                                                                      );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getRegardsAttributeName()).isEqualTo("someJsonObjectProp");
        assertThat(accessor.getValueType()).isEqualTo(String.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.someJsonObjectProp");
        assertThat(accessor.<String>getGenericExtractValueFn().apply(dataObject).get()).isEqualTo("someStringValue");
    }

    @Test
    public void test_makeDatePropAccessor() throws MalformedURLException {
        // GIVEN
        StacPropertyType sPropType = StacPropertyType.DATETIME;
        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                                                                              "someDateProp",
                                                                              null,
                                                                              null,
                                                                              null,
                                                                              "regards:someDateProp",
                                                                              "regards",
                                                                              sPropType.name(),
                                                                              null,
                                                                              false,
                                                                              null,
                                                                              null
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
                                                                              null,
                                                                              null,
                                                                              "regards:someURLProp",
                                                                              "regards",
                                                                              sPropType.name(),
                                                                              null,
                                                                              false,
                                                                              null,
                                                                              null
                                                                      );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getValueType()).isEqualTo(URL.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.someURLProp");
        assertThat(accessor.<MarkdownURL>getGenericExtractValueFn().apply(dataObject)).contains(MarkdownURL.build("http://xkcd.com/1"));
    }

    @Test
    public void test_makeStringPropAccessor() {
        // GIVEN
        StacPropertyType sPropType = StacPropertyType.STRING;
        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                                                                              "someStringProp",
                                                                              null,
                                                                              null,
                                                                              null,
                                                                              "regards:someStringProp",
                                                                              "regards",
                                                                              sPropType.name(),
                                                                              null,
                                                                              false,
                                                                              null,
                                                                              null
                                                                      );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getValueType()).isEqualTo(String.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.someStringProp");
        assertThat(accessor.<String>getGenericExtractValueFn().apply(dataObject)).contains("someStringValue");
    }

    @Test
    public void test_makeString2PropAccessor() {

        // GIVEN
        StacPropertyType sPropType = StacPropertyType.STRING;
        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                                                                              "anObject.anObjectStringProp",
                                                                              null,
                                                                              null,
                                                                              null,
                                                                              "anObject:anObjectStringProp",
                                                                              "anObject",
                                                                              sPropType.name(),
                                                                              null,
                                                                              false,
                                                                              null,
                                                                              null
                                                                      );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getValueType()).isEqualTo(String.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.anObject.anObjectStringProp");
        assertThat(accessor.<String>getGenericExtractValueFn().apply(dataObject)).contains("anObjectStringPropValue");
    }

    @Test
    public void test_makeBooleanPropAccessor() {
        // GIVEN
        StacPropertyType sPropType = StacPropertyType.BOOLEAN;
        StacPropertyConfiguration sPropConfig = new StacPropertyConfiguration(
                                                                              "someBooleanProp",
                                                                              null,
                                                                              null,
                                                                              null,
                                                                              "regards:someBooleanProp",
                                                                              "regards",
                                                                              sPropType.name(),
                                                                              null,
                                                                              false,
                                                                              null,
                                                                              null
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
                                                                              null,
                                                                              null,
                                                                              "regards:someDoubleProp",
                                                                              "regards",
                                                                              sPropType.name(),
                                                                              null,
                                                                              false,
                                                                              null,
                                                                              null
                                                                      );

        // WHEN
        RegardsPropertyAccessor accessor = factory.makeRegardsPropertyAccessor(sPropConfig, sPropType);

        // THEN
        assertThat(accessor.getValueType()).isEqualTo(Double.class);
        assertThat(accessor.getAttributeModel().getFullJsonPath()).isEqualTo("feature.properties.someDoubleProp");
        assertThat(accessor.<Double>getGenericExtractValueFn().apply(dataObject)).contains(15d);
    }

}