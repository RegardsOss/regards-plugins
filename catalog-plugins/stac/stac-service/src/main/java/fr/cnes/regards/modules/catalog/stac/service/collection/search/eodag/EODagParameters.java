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
package fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;

import java.util.HashMap;
import java.util.Map;

/**
 * EODag parameter for a single collection (i.e. product type)
 */
@JsonPropertyOrder({ "productType", "start", "end", "geom" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EODagParameters {

    /**
     * Collection identifier : URN
     */
    private final String productType;

    /**
     * Extra query parameters
     */
    private Map<String, Object> extras;

    /**
     * Start period : ISO8601 date
     */
    private String start;

    /**
     * End period : ISO8601 date
     */
    private String end;

    /**
     * Geometry as WKT
     */
    private String geom;

    public EODagParameters(String productType) {
        this.productType = productType;
    }

    public String getProductType() {
        return productType;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    public String getGeom() {
        return geom;
    }

    public void setGeom(String geom) {
        this.geom = geom;
    }

    public void addExtras(String param, PropertyType type, Object value) {
        if (extras == null) {
            extras = new HashMap<>();
        }
        switch (type) {
            case BOOLEAN:
                extras.put(param, (Boolean) value ? "True" : "False");
                break;
            default:
                extras.put(param, value);
        }
    }

    public boolean hasExtras() {
        return extras != null && !extras.isEmpty();
    }

    @JsonAnyGetter
    public Map<String, Object> getExtras() {
        return extras;
    }
}
