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

package fr.cnes.regards.modules.catalog.stac.domain.common;

import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import io.vavr.collection.List;
import io.vavr.collection.Stream;

/**
 * Interface providing utilities to work with links.
 * <p>
 * As many of the STAC entities have links, this interface helps
 * adding some links easily.
 */
public interface LinkCollection<T extends LinkCollection<T>> {

    List<Link> getLinks();

    T withLinks(List<Link> links);

    default T addLinks(Link... links) {
        return withLinks(getLinks().appendAll(Stream.of(links)));
    }

}
