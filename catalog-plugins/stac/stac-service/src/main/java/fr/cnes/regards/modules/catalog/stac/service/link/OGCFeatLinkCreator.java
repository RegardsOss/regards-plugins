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

import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Item;
import io.vavr.control.Try;

import java.net.URI;

/**
 * Interface providing methods to build links for collections/items.
 */
public interface OGCFeatLinkCreator {
    Try<URI> createRootLink();

    Try<URI> createCollectionsLink();

    Try<URI> createCollectionLink(String collectionId);
    Try<URI> createItemLink(String collectionId, String itemId);

    Try<URI> createCollectionLink(Collection collection);
    Try<URI> createItemLink(Item item);
}
