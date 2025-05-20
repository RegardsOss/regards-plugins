/*
 * Copyright 2017-2025 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

/**
 * Available relations for links in STAC for both STAC and STAC API specifications.
 *
 * @author Marc SORDI
 */
public enum Relation {

    /**
     * Root relation for landing page
     */
    ROOT("root"),
    /**
     * Self relation (many uses)
     */
    SELF("self"),
    /**
     * Service description for landing page (see core specification)
     */
    SERVICE_DESC("service-desc"),
    /**
     * Service documentation for landing page (see core specification)
     */
    SERVICE_DOC("service-doc"),
    /**
     * Child relation (many uses)
     */
    CHILD("child"),
    /**
     * Items relation for landing page (see ogcapi-features specification)
     */
    ITEMS("items"),
    /**
     * Conformance relation for landing page (see ogcapi-features specification)
     */
    CONFORMANCE("conformance"),
    /**
     * Collections relation for landing page (see ogcapi-features specification)
     */
    DATA("data"),
    /**
     * Optional relation to reference the parent collection (see ogcapi-features specification)
     */
    PARENT("parent"),
    /**
     * Collection relation to reference the related collection on items endpoints (see ogcapi-features specification)
     */
    COLLECTION("collection"),
    /**
     * Pagination relation for next page (see ogcapi-features specification)
     */
    NEXT("next"),
    /**
     * Pagination relation for previous page (see ogcapi-features specification)
     */
    PREV("prev"),
    /**
     * Search relation for search endpoint on landing page (see item search specification)
     */
    SEARCH("search");

    private final String value;

    Relation(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
