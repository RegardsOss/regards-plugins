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

import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Link;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Relation;
import jakarta.annotation.Nullable;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.util.Map;
import java.util.function.Function;

/**
 * Utilities to create STAC links from URIs.
 */
public interface StacLinkCreator {

    default Function<URI, Link> createLink(Relation rel,
                                           String title,
                                           String type,
                                           HttpMethod method,
                                           Map<String, String> headers,
                                           String body) {
        return uri -> createLink(uri, rel, title, type, method, headers, body);
    }

    default Link createLink(URI uri,
                            Relation rel,
                            String title,
                            String type,
                            HttpMethod method,
                            @Nullable Map<String, String> headers,
                            @Nullable String body) {
        return new Link(uri, rel, type, title, method, headers, body);
    }

}
