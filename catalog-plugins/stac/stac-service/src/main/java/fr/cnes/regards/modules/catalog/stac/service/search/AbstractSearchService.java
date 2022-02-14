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
package fr.cnes.regards.modules.catalog.stac.service.search;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.RegardsPropertyAccessor;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Asset;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
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

    protected Pageable pageable(Integer limit, Integer stacApiPage, List<SearchBody.SortBy> sortBy,
            List<StacProperty> stacProperties) {
        int pageablePage = stacApiPage - 1; //stac-browser forces us to have "first page is 1"-policy in the REST API,
                                            // while the pageable given to ES has "first-page is 0"-policy
        return PageRequest.of(pageablePage, Option.of(limit).getOrElse(10), sort(sortBy, stacProperties));
    }

    private Sort sort(List<SearchBody.SortBy> sortBy, List<StacProperty> stacProperties) {
        return Option.of(sortBy).map(sbs -> sbs.map(sb -> order(stacProperties, sb)).toJavaList()).map(Sort::by)
                .getOrElse(Sort::unsorted);
    }

    private Sort.Order order(List<StacProperty> stacProperties, SearchBody.SortBy sb) {
        return sb.getDirection() == ASC ?
                asc(regardsPropName(sb.getField(), stacProperties)) :
                desc(regardsPropName(sb.getField(), stacProperties));
    }

    private String regardsPropName(String field, List<StacProperty> stacProperties) {
        return stacProperties.find(sp -> sp.getStacPropertyName().equals(field))
                .map(StacProperty::getRegardsPropertyAccessor)
                .map(RegardsPropertyAccessor::getRegardsAttributeName) // TODO: this does not work with internal JSON properties
                .getOrElse(field);
    }

    protected List<Link> extractLinks(SearchPageLinkCreator searchPageLinkCreator, FacetPage<?> page) {
        return List.of(extractSelfPage(searchPageLinkCreator), extractNextPage(searchPageLinkCreator, page),
                       extractPreviousPage(searchPageLinkCreator, page)).flatMap(l -> l);
    }

    private Option<Link> extractSelfPage(SearchPageLinkCreator searchPageLinkCreator) {
        return searchPageLinkCreator.createSelfPageLink()
                .map(uri -> new Link(uri, Link.Relations.SELF, Asset.MediaType.APPLICATION_JSON, "this search page"));
    }

    private Option<Link> extractNextPage(SearchPageLinkCreator searchPageLinkCreator, FacetPage<?> page) {
        if (page.getTotalElements() - (long) page.getNumber() * page.getSize() - page.getNumberOfElements() > 0) {
            return searchPageLinkCreator.createNextPageLink()
                    .map(uri -> new Link(uri, Link.Relations.NEXT, Asset.MediaType.APPLICATION_JSON,
                                         "next search page"));
        }
        return Option.none();
    }

    private Option<Link> extractPreviousPage(SearchPageLinkCreator searchPageLinkCreator, FacetPage<?> page) {
        if (page.getPageable().hasPrevious()) {
            return searchPageLinkCreator.createPrevPageLink()
                    .map(uri -> new Link(uri, Link.Relations.PREV, Asset.MediaType.APPLICATION_JSON,
                                         "prev search page"));
        }
        return Option.none();
    }
}
