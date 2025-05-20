/*
 * Copyright 2017-2025 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
 * along with REGARDS. If not, see `<http://www.gnu.org/licenses/>`.
 */

package fr.cnes.regards.modules.catalog.stac.testutils.random;

import com.google.gson.JsonObject;
import org.jeasy.random.EasyRandomParameters;
import org.jeasy.random.api.Randomizer;
import org.jeasy.random.api.RandomizerRegistry;

import java.lang.reflect.Field;
import java.util.Random;

/**
 * Provide random additional fields for assets
 *
 * @author mnguyen0
 */
public class AdditionalFieldsRegistry implements RandomizerRegistry {

    private final Random random = new Random();

    @Override
    public void init(EasyRandomParameters easyRandomParameters) {
        // not useful here
    }

    @Override
    public Randomizer<?> getRandomizer(Field field) {
        if (field.getName().equals("additionalFields") && field.getType().equals(JsonObject.class)) {
            return (Randomizer<JsonObject>) this::makeRandomAdditionalFields;
        }
        return null;
    }

    private JsonObject makeRandomAdditionalFields() {
        JsonObject json = new JsonObject();
        int count = 2 + random.nextInt(4);
        for (int i = 0; i < count; i++) {
            String name = "additional_field_" + random.nextInt(1000);
            double value = Math.round(random.nextDouble() * 1000.0) / 10.0;
            json.addProperty(name, value);
        }

        JsonObject nestedJson = new JsonObject();
        nestedJson.addProperty("nested_field_" + random.nextInt(1000), Math.round(random.nextDouble() * 1000.0) / 10.0);

        json.add("additional_field_object", nestedJson);

        return json;
    }

    @Override
    public Randomizer<?> getRandomizer(Class<?> aClass) {
        return null;
    }
}
