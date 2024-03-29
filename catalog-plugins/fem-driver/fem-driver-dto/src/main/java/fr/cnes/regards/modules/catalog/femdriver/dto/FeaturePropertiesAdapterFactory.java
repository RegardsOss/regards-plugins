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
package fr.cnes.regards.modules.catalog.femdriver.dto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import fr.cnes.regards.framework.gson.adapters.PolymorphicTypeAdapterFactory;
import fr.cnes.regards.framework.gson.annotation.GsonTypeAdapterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;

/**
 * Merge array of properties
 *
 * @author Marc Sordi
 */
@GsonTypeAdapterFactory
public class FeaturePropertiesAdapterFactory extends PolymorphicTypeAdapterFactory<FeatureProperties> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeaturePropertiesAdapterFactory.class);

    private static final String PROPERTIES_FIELD_NAME = "properties";

    public FeaturePropertiesAdapterFactory() {
        super(FeatureProperties.class, "type", false);
        this.registerSubtype(FeatureProperties.class, FeatureProperties.TYPE);
    }

    @Override
    protected JsonElement getOnReadDiscriminator(JsonElement jsonElement) {
        return new JsonPrimitive(FeatureProperties.TYPE);
    }

    @Override
    protected JsonElement beforeWrite(JsonElement jsonElement, Class<?> subType) {

        LOGGER.trace("Before write");

        JsonObject entity = jsonElement.getAsJsonObject();
        JsonElement attEl = entity.get(PROPERTIES_FIELD_NAME);
        if ((attEl != null) && !attEl.isJsonNull()) {
            if (attEl.isJsonArray()) {
                entity.add(PROPERTIES_FIELD_NAME, mergeArray(attEl.getAsJsonArray()));
            } else {
                String errorMessage = String.format("Unexpected JSON element %s. Array required.",
                                                    jsonElement.toString());
                LOGGER.error(errorMessage);
                throw new IllegalArgumentException(errorMessage);
            }
        }
        return entity;
    }

    @Override
    protected JsonElement beforeRead(JsonElement jsonElement, String discriminator, Class<?> subType) {
        LOGGER.trace("Before read");

        if (!jsonElement.isJsonObject()) {
            throw objectRequiredException(jsonElement);
        }

        JsonObject entity = jsonElement.getAsJsonObject();
        JsonElement attEl = entity.get(PROPERTIES_FIELD_NAME);
        if (attEl != null) {
            if (attEl.isJsonObject()) {
                entity.add(PROPERTIES_FIELD_NAME, restoreArray(attEl.getAsJsonObject()));
            } else {
                throw objectRequiredException(attEl);
            }
        }
        return entity;
    }

    /**
     * Merge {@link JsonArray} flattening elements in a single {@link JsonObject}
     *
     * @param jsonArray {@link JsonArray} to flatten
     * @return {@link JsonObject}
     */
    private JsonObject mergeArray(JsonArray jsonArray) {
        JsonObject mergedObject = new JsonObject();
        Iterator<JsonElement> nestedIter = jsonArray.iterator();
        while (nestedIter.hasNext()) {
            JsonElement nested = nestedIter.next();
            if (nested.isJsonObject()) {
                JsonObject nestedObject = nested.getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : nestedObject.entrySet()) {
                    mergedObject.add(e.getKey(), e.getValue());
                }
            } else {
                throw objectRequiredException(nested);
            }
        }
        return mergedObject;
    }

    /**
     * Restore {@link JsonArray} from flattened {@link JsonObject} elements (reverse merge)
     *
     * @param jsonObject {@link JsonObject} to transform
     * @return {@link JsonArray}
     */
    private JsonArray restoreArray(JsonObject jsonObject) {
        JsonArray restoredArray = new JsonArray();
        for (Map.Entry<String, JsonElement> nestedEntry : jsonObject.entrySet()) {
            JsonObject nestedObject = new JsonObject();
            nestedObject.add(nestedEntry.getKey(), nestedEntry.getValue());
            restoredArray.add(nestedObject);
        }
        return restoredArray;
    }

    private IllegalArgumentException objectRequiredException(JsonElement jsonElement) {
        String errorMessage = String.format("Unexpected JSON element %s. Object required.", jsonElement.toString());
        LOGGER.error(errorMessage);
        return new IllegalArgumentException(errorMessage);
    }
}
