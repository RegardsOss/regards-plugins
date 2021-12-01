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

package fr.cnes.regards.modules.catalog.stac.service.link;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.URI_AUTH_PARAM_ADDING;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.URI_PARAM_ADDING;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;
import static java.lang.String.format;
import static org.springframework.web.util.UriComponentsBuilder.fromUri;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import fr.cnes.regards.framework.security.role.DefaultRole;
import fr.cnes.regards.framework.security.utils.jwt.JWTAuthentication;
import fr.cnes.regards.framework.security.utils.jwt.JWTService;
import fr.cnes.regards.framework.security.utils.jwt.UserDetails;
import io.vavr.CheckedFunction1;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Map;
import io.vavr.control.Try;

/**
 * Base implementation for {@link UriParamAdder}.
 */
@Component
public class UriParamAdderImpl implements UriParamAdder {

    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(UriParamAdderImpl.class);

    private final JWTService jwtService;

    @Autowired
    public UriParamAdderImpl(JWTService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public CheckedFunction1<URI, Try<URI>> appendAuthParams(JWTAuthentication auth) {
        if (auth == null) {
            return Try::success;
        }
        return uri -> {
            Tuple2<String, String> authParam = makeAuthParam(auth);
            return trying(() -> fromUri(uri).queryParam(authParam._1, authParam._2).build().toUri())
                    .mapFailure(URI_AUTH_PARAM_ADDING, () -> format("Failed to add auth params to URI %s", uri));
        };
    }

    @Override
    public CheckedFunction1<URI, Try<URI>> appendParams(Map<String, String> params) {
        return uri -> trying(() -> {
            UriComponentsBuilder uriBuilder = fromUri(uri);
            return params.foldLeft(uriBuilder, (ub, kv) -> ub.queryParam(kv._1, kv._2)).build().toUri();
        }).mapFailure(URI_PARAM_ADDING, () -> format("Failed to add params %s to URI %s", params, uri));
    }

    @Override
    public CheckedFunction1<URI, Try<URI>> appendTinyUrl(String tinyUrlId) {
        return uri -> {
            return trying(() -> fromUri(uri).queryParam("tinyurl", tinyUrlId).build().toUri())
                    .mapFailure(URI_AUTH_PARAM_ADDING, () -> format("Failed to add tiny URL to URI %s", uri));
        };
    }

    @Override
    public Tuple2<String, String> makeAuthParam(JWTAuthentication auth) {
        String tenant = auth.getTenant();
        UserDetails user = auth.getUser();
        String role = user.getRole();
        return DefaultRole.PUBLIC.name().equals(role) ? Tuple.of("scope", tenant)
                : Tuple.of("token", jwtService.generateToken(tenant, user.getLogin(), user.getEmail(), role));
    }

    @Override
    public Tuple2<String, String> makeAuthParam() {
        JWTAuthentication auth = (JWTAuthentication) SecurityContextHolder.getContext().getAuthentication();
        return makeAuthParam(auth);
    }
}
