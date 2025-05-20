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

import fr.cnes.regards.modules.catalog.stac.domain.spec.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.Item;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Link;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Relation;
import io.vavr.control.Option;

/**
 * Interface providing methods to build links for collections/items.
 */
public interface OGCFeatLinkCreator extends StacLinkCreator {

    Option<Link> createLandingPageLink(Relation rel);

    Option<Link> createConformanceLink(Relation rel);

    Option<Link> createCollectionsLink(Relation rel);

    Option<Link> createCollectionLink(Relation rel, String collectionId, String collectionTitle);

    Option<Link> createCollectionItemsLink(Relation rel, String collectionId);

    Option<Link> createCollectionLink(Relation rel, Collection collection);

    Option<Link> createItemLink(Relation rel, String collectionId, String itemId);

    Option<Link> createItemLink(Relation rel, Item item);

    Option<Link> createSearchLink(Relation rel);
}
