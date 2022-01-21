/*
 * Copyright 2017-2022 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

import fr.cnes.regards.framework.security.utils.jwt.JWTAuthentication;
import io.vavr.CheckedFunction1;
import io.vavr.Tuple2;
import io.vavr.collection.Map;
import io.vavr.control.Try;

import java.net.URI;

/**
 * Provides helper methods to add parameters to URIs.
 */
public interface UriParamAdder {

    CheckedFunction1<URI, Try<URI>> appendAuthParams(JWTAuthentication auth);

    CheckedFunction1<URI, Try<URI>> appendParams(Map<String, String> params);

    CheckedFunction1<URI, Try<URI>> appendTinyUrl(String tinyUrlId);

    Tuple2<String, String> makeAuthParam(JWTAuthentication auth);

    Tuple2<String, String> makeAuthParam();

}
