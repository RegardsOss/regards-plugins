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

package fr.cnes.regards.modules.catalog.stac.service.link;

import fr.cnes.regards.framework.authentication.IAuthenticationResolver;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.security.role.DefaultRole;
import io.vavr.CheckedFunction1;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Map;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.URI_AUTH_PARAM_ADDING;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.URI_PARAM_ADDING;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;
import static java.lang.String.format;
import static org.springframework.web.util.UriComponentsBuilder.fromUri;

/**
 * Base implementation for {@link UriParamAdder}.
 */
@Component
public class UriParamAdderImpl implements UriParamAdder {

    private static final Logger LOGGER = LoggerFactory.getLogger(UriParamAdderImpl.class);

    private final IRuntimeTenantResolver runtimeTenantResolver;

    private final IAuthenticationResolver authenticationResolver;

    @Autowired
    public UriParamAdderImpl(IRuntimeTenantResolver runtimeTenantResolver,
                             IAuthenticationResolver authenticationResolver) {
        this.runtimeTenantResolver = runtimeTenantResolver;
        this.authenticationResolver = authenticationResolver;
    }

    @Override
    public CheckedFunction1<URI, Try<URI>> appendAuthParams() {
        return this.appendAuthParams(true);
    }

    @Override
    public CheckedFunction1<URI, Try<URI>> appendAuthParams(boolean appendAuthParams) {
        LOGGER.trace("Append authentication parameters >>> {}", appendAuthParams);
        if (appendAuthParams) {
            return uri -> {
                Tuple2<String, String> authParam = makeAuthParam();
                return trying(() -> fromUri(uri).queryParam(authParam._1, authParam._2).build().toUri()).mapFailure(
                    URI_AUTH_PARAM_ADDING,
                    () -> format("Failed to add auth params to URI %s", uri));
            };
        } else {
            return uri -> Try.of(() -> uri);
        }
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
        return uri -> trying(() -> fromUri(uri).queryParam("tinyurl", tinyUrlId).build().toUri()).mapFailure(
            URI_AUTH_PARAM_ADDING,
            () -> format("Failed to add tiny URL to URI %s", uri));
    }

    @Override
    public Tuple2<String, String> makeAuthParam() {
        String tenant = runtimeTenantResolver.getTenant();
        String role = authenticationResolver.getRole();
        return DefaultRole.PUBLIC.name().equals(role) ?
            Tuple.of("scope", tenant) :
            Tuple.of("token", authenticationResolver.getToken());
    }
}
