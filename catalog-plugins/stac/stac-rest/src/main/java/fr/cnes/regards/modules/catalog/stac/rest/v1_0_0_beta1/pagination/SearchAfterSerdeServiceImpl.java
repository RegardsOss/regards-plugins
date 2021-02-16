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

package fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.pagination;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Base implementation for {@link SearchAfterSerdeService}.
 */
@Service
public class SearchAfterSerdeServiceImpl implements SearchAfterSerdeService, Base64Codec {

    private final Gson gson;

    @Autowired
    public SearchAfterSerdeServiceImpl(Gson gson) {
        this.gson = gson;
    }

    @Override
    public String serialize(List<Object> searchAfter) {
        return toBase64(gson.toJson(searchAfter));
    }

    @Override
    public Try<List<Object>> deserialize(String repr) {
        return Try.of(() -> gson.fromJson(fromBase64(repr), new TypeToken<List<Object>>(){}.getType()));
    }
}
