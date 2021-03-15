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

package fr.cnes.regards.modules.catalog.stac.plugin.configuration.mapping;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import fr.cnes.regards.framework.gson.adapters.OffsetDateTimeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.RegardsPropertyAccessor;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.StacPropertyConfiguration;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import fr.cnes.regards.modules.model.dto.properties.MarkdownURL;
import fr.cnes.regards.modules.opensearch.service.cache.attributemodel.IAttributeFinder;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Function;

/**
 * Allows to create {@link RegardsPropertyAccessor} instances from configured String representations of fields.
 */
@Component
public class RegardsPropertyAccessorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegardsPropertyAccessorFactory.class);

    private final IAttributeFinder finder;

    private final ParseContext jsonPathParseContext;

    @Autowired
    public RegardsPropertyAccessorFactory(IAttributeFinder finder, Gson gson) {
        this.finder = finder;
        this.jsonPathParseContext = JsonPath.using(jsonPathConfig(gson));
    }

    public RegardsPropertyAccessor makeRegardsPropertyAccessor(
            StacPropertyConfiguration sPropConfig,
            StacPropertyType sPropType
    ) {
        String attrName = sPropConfig.getModelPropertyName();
        AttributeModel attr = loadAttribute(attrName, sPropConfig, sPropType);
        Option<String> jsonPath = Option.of(sPropConfig.getModelPropertyJSONPath())
                .filter(StringUtils::isNotBlank);

        Tuple2<Class<?>, Function<DataObject, Try<?>>> classFunctionTuple2 =
                makeValueTypeAndExtractionFn(sPropType, attrName, jsonPath);

        return new RegardsPropertyAccessor(
                sPropConfig.getModelPropertyName(),
                attr,
                classFunctionTuple2._2,
                classFunctionTuple2._1
        );
    }

    private AttributeModel loadAttribute(String attrName, StacPropertyConfiguration sPropConfig, StacPropertyType sPropType) {
        AttributeModel attribute = Try
            .of(() -> finder.findByName(attrName))
            .getOrElseGet(t -> {
                AttributeModel result = new AttributeModel();
                result.setType(sPropType.getPropertyType());
                result.setAlterable(false);
                return result;
            });
        String realAttrName = Option.of(sPropConfig.getModelPropertyJSONPath())
            .map(path -> attrName + "." + path)
            .getOrElse(attrName);
        attribute.setName(realAttrName);
        attribute.setJsonPath(sPropConfig.getModelPropertyJSONPath());
        return attribute;
    }

    private Tuple2<Class<?>, Function<DataObject, Try<?>>> makeValueTypeAndExtractionFn(
            StacPropertyType sPropType,
            String attrName,
            Option<String> jsonPath
    ) {
        Class<?> valueType = sPropType.getValueType();

        Function<DataObject, Try<?>> extractFn = jsonPath
                .map(jp -> makeJsonExtractFn(sPropType, attrName, jp))
                .getOrElse(() -> makeExtractFn(sPropType, attrName));

        return Tuple.of(valueType, extractFn);
    }

    private Function<DataObject, Try<?>> makeExtractFn(
            StacPropertyType sPropType,
            String attrName
    ) {
        return dataObject ->
            Try.of(() ->
                extractValue(sPropType.getValueType(), sPropType, dataObject.getFeature().getProperty(attrName).getValue())
            )
            .onFailure(t -> LOGGER.error(t.getMessage(), t));
    }

    private static <T> T extractValue(Class<T> valueType, StacPropertyType sPropType, Object value) {
        switch (sPropType) {
            case URL: return (T)((MarkdownURL)value).getUrl();
            default: return valueType.cast(value);
        }
    }

    private Function<DataObject, Try<?>> makeJsonExtractFn(
            StacPropertyType sPropType, String attrName,
            String jsonPath
    ) {
        return dataObject -> Try.of(() -> JsonObject.class.cast(dataObject.getFeature().getProperty(attrName).getValue()))
                .mapTry(jsonObject -> jsonPathParseContext.parse(jsonObject).read(jsonPath, JsonElement.class))
                .mapTry(value -> extractJsonValue(sPropType, (JsonPrimitive)value))
                .onFailure(t -> LOGGER.error(t.getMessage(), t));
    }

    @SuppressWarnings("unchecked")
    private <T> T extractJsonValue(StacPropertyType sPropType, JsonPrimitive value) {
        switch(sPropType) {
            case NUMBER: case ANGLE: case LENGTH: case PERCENTAGE: return (T) Double.valueOf(value.getAsDouble());
            case BOOLEAN: return (T) Boolean.valueOf(value.getAsBoolean());
            case URL: return (T)url(value.getAsString());
            case DATETIME: return (T)OffsetDateTimeAdapter.parse(value.getAsString());
            case STRING: default: return (T) value.getAsString();
        }
    }

    private URL url(String repr) {
        try { return new URL(repr); }
        catch (MalformedURLException e) { throw new RuntimeException(e); }
    }

    private static Configuration jsonPathConfig(Gson gson) {
        return Configuration.builder().jsonProvider(new GsonJsonProvider(gson)).options().build();
    }

}
