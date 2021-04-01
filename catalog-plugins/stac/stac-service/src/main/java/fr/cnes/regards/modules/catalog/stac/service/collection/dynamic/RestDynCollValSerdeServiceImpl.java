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

package fr.cnes.regards.modules.catalog.stac.service.collection.dynamic;

import com.google.gson.Gson;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollVal;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.DynCollLevelVal;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.rest.RestDynCollLevelVal;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.rest.RestDynCollVal;
import fr.cnes.regards.modules.catalog.stac.service.utils.Base64Codec;
import io.vavr.collection.Seq;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.RESTDYNCOLLVAL_PARSING;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;
import static java.lang.String.format;

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
    public String toUrn(RestDynCollVal values) {
        return URN_PREFIX + toBase64(gson.toJson(values));
    }

    @Override
    public Try<RestDynCollVal> fromUrn(String urn) {
        return trying(() -> {
            String b64 = urn.replaceFirst(URN_PREFIX, "");
            String json = fromBase64(b64);
            return gson.fromJson(json, RestDynCollVal.class);
        })
        .mapFailure(
            RESTDYNCOLLVAL_PARSING,
            () -> format("Failed to parse REST dynamic collection value from %s", urn)
        );
    }

    @Override
    public boolean isListOfDynCollLevelValues(String urn) {
        return urn.startsWith(URN_PREFIX);
    }

    @Override
    public RestDynCollVal fromDomain(DynCollVal domain) {
        return new RestDynCollVal(domain.getLevels().map(this::serializeLevels));
    }

    private RestDynCollLevelVal serializeLevels(DynCollLevelVal dynCollLevelVal) {
        return new RestDynCollLevelVal(
            dynCollLevelVal.getDefinition().getStacProperty().getStacPropertyName(),
            dynCollLevelVal.getDefinition().renderValue(dynCollLevelVal)
        );
    }

    @Override
    public Try<DynCollVal> toDomain(DynCollDef def, RestDynCollVal rest) {
        return Try.sequence(rest.getLevels().map(l -> deserializeLevels(def, l)))
            .map(Seq::toList)
            .map(levels -> new DynCollVal(def, levels));
    }

    private Try<DynCollLevelVal> deserializeLevels(DynCollDef def, RestDynCollLevelVal restDynCollLevelVal) {
        String propertyName = restDynCollLevelVal.getPropertyName();
        return def.getLevels()
            .find(ld -> ld.getStacProperty().getStacPropertyName().equals(propertyName))
            .toTry()
            .map(ld -> ld.parseValues(restDynCollLevelVal.getValue()));
    }

}
