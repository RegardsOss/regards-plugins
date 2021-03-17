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

package fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.dyncoll;

import com.google.gson.Gson;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.Base64Codec;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Base implementation for {@link RestDynCollValSerdeService}.
 */
@Service
public class RestDynCollValSerdeServiceImpl implements RestDynCollValSerdeService, Base64Codec {

    public static final String URN_PREFIX = "URN:DYNCOLL:";

    private final Gson gson;

    @Autowired
    public RestDynCollValSerdeServiceImpl(Gson gson) {
        this.gson = gson;
    }

    @Override
    public String serialize(RestDynCollVal values) {
        return URN_PREFIX + toBase64(gson.toJson(values));
    }

    @Override
    public Try<RestDynCollVal> deserialize(String repr) {
        return Try.of(() -> {
            String b64 = repr.replaceFirst(URN_PREFIX, "");
            String json = fromBase64(b64);
            return gson.fromJson(json, RestDynCollVal.class);
        });
    }

    @Override
    public boolean isListOfDynCollLevelValues(String urn) {
        return urn.startsWith(URN_PREFIX);
    }

}
