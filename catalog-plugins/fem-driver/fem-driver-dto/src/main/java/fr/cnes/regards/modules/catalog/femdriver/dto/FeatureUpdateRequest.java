/*
 * Copyright 2017-2020 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.femdriver.dto;

import java.util.Map;

import fr.cnes.regards.modules.model.dto.properties.IProperty;
import fr.cnes.regards.modules.search.domain.SearchRequest;

/**
 * DTO for update request parameters.
 *
 * @author Sébastien Binda
 *
 */
public class FeatureUpdateRequest {

    private SearchRequest searchRequest;

    private Map<String, IProperty<?>> values;

    public FeatureUpdateRequest() {
        super();
    }

    public static FeatureUpdateRequest build(SearchRequest searchRequest, Map<String, IProperty<?>> values) {
        FeatureUpdateRequest req = new FeatureUpdateRequest();
        req.searchRequest = searchRequest;
        req.values = values;
        return req;
    }

    /**
     * @return the searchRequest
     */
    public SearchRequest getSearchRequest() {
        return searchRequest;
    }

    /**
     * @param searchRequest the searchRequest to set
     */
    public void setSearchRequest(SearchRequest searchRequest) {
        this.searchRequest = searchRequest;
    }

    /**
     * @return the values
     */
    public Map<String, IProperty<?>> getValues() {
        return values;
    }

    /**
     * @param values the values to set
     */
    public void setValues(Map<String, IProperty<?>> values) {
        this.values = values;
    }

}