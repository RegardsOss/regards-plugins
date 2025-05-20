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

package fr.cnes.regards.modules.catalog.stac.domain.spec.common;

import java.net.URI;

/**
 * This object describes a relationship with another entity. Data providers are advised
 * to be liberal with the links section, to describe things like the catalog an item is in,
 * related items, parent or child items (modeled in different ways, like an 'acquisition' or derived data).
 * It is allowed to add additional fields such as a title and type.
 *
 * @see <a href="https://github.com/radiantearth/stac-spec/blob/v1.0.0/item-spec/item-spec.md#link-object">description</a>
 */
public record Link(URI href,
                   String rel,
                   String type,
                   String title) {

    public Link(URI href, Relation rel, String type, String title) {
        this(href, rel.getValue(), type, title);
    }
}
