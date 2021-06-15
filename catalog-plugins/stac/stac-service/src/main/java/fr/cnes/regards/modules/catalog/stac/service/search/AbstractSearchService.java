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
package fr.cnes.regards.modules.catalog.stac.service.search;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemCollectionResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.RegardsPropertyAccessor;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Asset;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody.SortBy.Direction.ASC;
import static org.springframework.data.domain.Sort.Order.asc;
import static org.springframework.data.domain.Sort.Order.desc;

/**
 * Common service for both collection and item search services
 */
public abstract class AbstractSearchService {

    protected Pageable pageable(Integer limit, Integer page, List<SearchBody.SortBy> sortBy, List<StacProperty> stacProperties) {
        return PageRequest.of(page, Option.of(limit).getOrElse(10), sort(sortBy, stacProperties));
    }

    private Sort sort(List<SearchBody.SortBy> sortBy, List<StacProperty> stacProperties) {
        return Option.of(sortBy)
                .map(sbs -> sbs.map(sb -> order(stacProperties, sb)).toJavaList())
                .map(Sort::by)
                .getOrElse(Sort::unsorted);
    }

    private Sort.Order order(List<StacProperty> stacProperties, SearchBody.SortBy sb) {
        return sb.getDirection() == ASC
                ? asc(regardsPropName(sb.getField(), stacProperties))
                : desc(regardsPropName(sb.getField(), stacProperties));
    }

    private String regardsPropName(String field, List<StacProperty> stacProperties) {
        return stacProperties.find(sp -> sp.getStacPropertyName().equals(field))
                .map(StacProperty::getRegardsPropertyAccessor)
                .map(RegardsPropertyAccessor::getRegardsAttributeName) // TODO: this does not work with internal JSON properties
                .getOrElse(field);
    }

    protected List<Link> extractLinks(SearchPageLinkCreator searchPageLinkCreator) {
        return List.of(
                searchPageLinkCreator.createSelfPageLink()
                        .map(uri -> new Link(uri, Link.Relations.SELF, Asset.MediaType.APPLICATION_JSON, "this search page")),
                searchPageLinkCreator.createNextPageLink()
                        .map(uri -> new Link(uri, Link.Relations.NEXT, Asset.MediaType.APPLICATION_JSON, "next search page")),
                searchPageLinkCreator.createPrevPageLink()
                        .map(uri -> new Link(uri, Link.Relations.PREV, Asset.MediaType.APPLICATION_JSON, "prev search page"))
        )
                .flatMap(l -> l);
    }
}
