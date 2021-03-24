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
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import io.vavr.control.Try;

import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.rel;

/**
 * Interface providing methods to build links for collections/items.
 */
public interface OGCFeatLinkCreator extends StacLinkCreator {
    Try<Link> createRootLink();

    Try<Link> createCollectionsLink();
    default Try<Link> createCollectionsLinkWithRel(String rel) {
        return createCollectionsLink().map(rel(rel));
    }

    Try<Link> createCollectionLink(String collectionId, String collectionTitle);
    default Try<Link> createCollectionLinkWithRel(String collectionId, String collectionTitle, String rel) {
        return createCollectionLink(collectionId, collectionTitle).map(rel(rel));
    }

    Try<Link> createCollectionItemsLink(String collectionId);
    default Try<Link> createCollectionItemsLinkWithRel(String collectionId, String rel) {
        return createCollectionItemsLink(collectionId).map(rel(rel));
    }
    Try<Link> createItemLink(String collectionId, String itemId);
    default Try<Link> createItemLinkWithRel(String collectionId, String itemId, String rel) {
        return createItemLink(collectionId, itemId).map(rel(rel));
    }

    Try<Link> createCollectionLink(Collection collection);
    default Try<Link> createCollectionLinkWithRel(Collection collection, String rel) {
        return createCollectionLink(collection).map(rel(rel));
    }

    Try<Link> createItemLink(Item item);
    default Try<Link> createItemLinkWithRel(Item item, String rel) {
        return createItemLink(item).map(rel(rel));
    }
}
