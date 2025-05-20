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

/**
 * Gson type adapter for Asset
 */
package fr.cnes.regards.modules.catalog.stac.domain.api.gson;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import fr.cnes.regards.framework.gson.annotation.GsonTypeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Asset;
import io.vavr.collection.Set;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * Gson type adapter for Assets
 *
 * @author mnguyen0
 */
@GsonTypeAdapter(adapted = Asset.class)
public class AssetAdapter extends TypeAdapter<Asset> {

    private final RoleTypeAdapter roleAdapter = new RoleTypeAdapter();

    private static final String PREFIX = "file:";

    @Override
    public void write(JsonWriter out, Asset asset) throws IOException {
        out.beginObject();

        if (asset != null) {
            if (asset.getChecksum() != null) {
                out.name(PREFIX + "checksum").value(asset.getChecksum());
            }
            out.name(PREFIX + "size").value(asset.getSize());
            if (asset.getHref() != null) {
                out.name("href").value(asset.getHref().toString());
            }
            if (asset.getTitle() != null) {
                out.name("title").value(asset.getTitle());
            }
            if (asset.getDescription() != null) {
                out.name("description").value(asset.getDescription());
            }
            if (asset.getType() != null) {
                out.name("type").value(asset.getType());
            }

            if (asset.getRoles() != null) {
                out.name("roles");
                roleAdapter.write(out, asset.getRoles());
            }

            //There is no "additionalField" field in the returned JSON, content of this Asset attribute must appear
            // directly embedded in the serialized JSON asset
            if (asset.getAdditionalFields() != null) {
                for (Map.Entry<String, JsonElement> entry : asset.getAdditionalFields().entrySet()) {
                    out.name(entry.getKey());
                    Streams.write(entry.getValue(), out);
                }
            }
        }

        out.endObject();
    }

    @Override
    public Asset read(JsonReader in) throws IOException {
        String multiHashChecksum = null;
        Long size = null;
        URI href = null;
        String title = null;
        String description = null;
        String type = null;
        Set<String> roles = null;

        JsonObject additionalFields = new JsonObject();
        in.beginObject();

        //Fields that are not among the following must end into the additionalFields JsonObject
        while (in.hasNext()) {
            String name = in.nextName();
            switch (name) {
                case PREFIX + "checksum" -> multiHashChecksum = in.nextString();
                case PREFIX + "size" -> size = Long.valueOf(in.nextString());
                case "href" -> href = URI.create(in.nextString());
                case "title" -> title = in.nextString();
                case "description" -> description = in.nextString();
                case "type" -> type = in.nextString();
                case "roles" -> roles = roleAdapter.read(in);
                default -> {
                    JsonElement value = Streams.parse(in);
                    additionalFields.add(name, value);
                }
            }
        }
        in.endObject();

        return Asset.fromMultihash(multiHashChecksum, size, href, title, description, type, roles, additionalFields);
    }
}
