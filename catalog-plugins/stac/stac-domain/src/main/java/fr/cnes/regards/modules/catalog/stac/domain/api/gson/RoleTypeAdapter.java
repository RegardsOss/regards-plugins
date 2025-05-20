/*
 * Copyright 2017-2024 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.stac.domain.api.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.vavr.collection.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;

import static com.google.gson.stream.JsonToken.NULL;

/**
 * Gson type adapter for asset roles
 */
public class RoleTypeAdapter extends TypeAdapter<Set<String>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoleTypeAdapter.class);

    @Override
    public void write(JsonWriter out, Set<String> value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.beginArray();
        value.forEach(v -> {
            try {
                out.value(v);
            } catch (IOException e) {
                LOGGER.error("Error writing asset roles", e);
            }
        });
        out.endArray();
    }

    @Override
    public Set<String> read(JsonReader in) throws IOException {
        if (in.peek() == NULL) {
            in.nextNull();
            return null;
        }
        java.util.Set roles = new HashSet();
        in.beginArray();
        while (in.hasNext()) {
            roles.add(in.nextString());
        }
        in.endArray();
        return io.vavr.collection.HashSet.ofAll(roles);
    }
}
