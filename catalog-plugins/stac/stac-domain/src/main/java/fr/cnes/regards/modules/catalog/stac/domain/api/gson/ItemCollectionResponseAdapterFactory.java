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

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.Streams;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import fr.cnes.regards.framework.gson.annotation.GsonTypeAdapterFactory;
import fr.cnes.regards.modules.catalog.stac.domain.api.ItemCollectionResponse;

import java.io.IOException;

/**
 * Type adapter for item collection response to serialize null value for geometry
 */
@GsonTypeAdapterFactory
public class ItemCollectionResponseAdapterFactory implements TypeAdapterFactory {

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        final Class<? super T> requestedType = type.getRawType();
        if (!ItemCollectionResponse.class.isAssignableFrom(requestedType)) {
            return null;
        }

        // Get delegate
        TypeAdapter<T> typeAdapter = gson.getDelegateAdapter(this, type);

        return new TypeAdapter<T>() {

            @Override
            public void write(JsonWriter out, T value) throws IOException {
                out.setSerializeNulls(true);
                // Delegate item serialization
                Streams.write(typeAdapter.toJsonTree(value), out);
                out.setSerializeNulls(false);
            }

            @Override
            public T read(JsonReader in) throws IOException {
                // Nothing to do : only serialization
                return null;
            }
        };
    }
}
