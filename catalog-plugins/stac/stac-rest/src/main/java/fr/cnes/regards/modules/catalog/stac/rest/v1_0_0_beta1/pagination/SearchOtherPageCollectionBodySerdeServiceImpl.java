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

package fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.pagination;

import com.google.gson.Gson;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.CollectionSearchBody;
import fr.cnes.regards.modules.catalog.stac.service.utils.Base64Codec;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.COLLECTIONSEARCHBODY_PARSING;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;
import static java.lang.String.format;

/**
 * Default impl for {@link SearchOtherPageCollectionBodySerdeService}.
 */
@Service
public class SearchOtherPageCollectionBodySerdeServiceImpl
    implements SearchOtherPageCollectionBodySerdeService, Base64Codec {

    private final Gson gson;

    @Autowired
    public SearchOtherPageCollectionBodySerdeServiceImpl(Gson gson) {
        this.gson = gson;
    }

    @Override
    public Try<CollectionSearchBody> deserialize(String repr) {
        return trying(() -> gson.fromJson(fromBase64(repr), CollectionSearchBody.class)).mapFailure(
            COLLECTIONSEARCHBODY_PARSING,
            () -> format("Failed to deserialize collection search body representation: %s", repr));
    }
}
