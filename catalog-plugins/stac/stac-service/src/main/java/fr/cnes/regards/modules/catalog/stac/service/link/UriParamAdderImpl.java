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

import fr.cnes.regards.framework.security.role.DefaultRole;
import fr.cnes.regards.framework.security.utils.jwt.JWTAuthentication;
import fr.cnes.regards.framework.security.utils.jwt.JWTService;
import fr.cnes.regards.framework.security.utils.jwt.UserDetails;
import io.vavr.CheckedFunction1;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Map;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Base implementation for {@link UriParamAdder}.
 */
@Component
public class UriParamAdderImpl implements UriParamAdder {

    private static final Logger LOGGER = LoggerFactory.getLogger(UriParamAdderImpl.class);

    private final JWTService jwtService;

    @Autowired
    public UriParamAdderImpl(JWTService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public CheckedFunction1<URI, Try<URI>> appendAuthParams (JWTAuthentication auth) {
        return uri -> {
            Tuple2<String, String> authParam = makeAuthParam(auth);
            LOGGER.debug("URI before adding token: {}", uri);
            return Try.of(() ->
                    UriComponentsBuilder.fromUri(uri).queryParam(authParam._1, authParam._2).build().toUri()
            )
            .onSuccess(u -> LOGGER.debug("URI after  adding token: {}", u));
        };
    }

    @Override
    public CheckedFunction1<URI, Try<URI>> appendParams (Map<String, String> params) {
        return uri -> {
            LOGGER.debug("URI before adding token: {}", uri);
            return Try.of(() -> {
                UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUri(uri);
                return params.foldLeft(
                        uriBuilder,
                        (ub, kv) -> ub.queryParam(kv._1, kv._2)
                ).build().toUri();
            });
        };
    }

    @Override
    public Tuple2<String, String> makeAuthParam(
            JWTAuthentication auth
    ) {
        String tenant = auth.getTenant();
        UserDetails user = auth.getUser();
        String role = user.getRole();
        return DefaultRole.PUBLIC.name().equals(role)
                ? Tuple.of("scope", tenant)
                : Tuple.of("token", jwtService.generateToken(tenant, user.getLogin(), user.getEmail(), role));
    }

    @Override
    public Tuple2<String, String> makeAuthParam() {
        JWTAuthentication auth = (JWTAuthentication) SecurityContextHolder.getContext().getAuthentication();
        return makeAuthParam(auth);
    }
}
