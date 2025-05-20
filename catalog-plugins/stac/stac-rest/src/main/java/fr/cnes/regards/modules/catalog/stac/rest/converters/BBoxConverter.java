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
package fr.cnes.regards.modules.catalog.stac.rest.converters;

import com.google.gson.Gson;
import fr.cnes.regards.modules.catalog.stac.domain.spec.geo.BBox;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Convert REST API GET bbox parameter from string to bbox
 *
 * @author Marc SORDI
 */
@Component
public class BBoxConverter implements Converter<String, BBox> {

    private static final String OPENING_BRACKET = "[";

    private static final String CLOSING_BRACKET = "]";

    private final Gson gson;

    public BBoxConverter(Gson gson) {
        this.gson = gson;
    }

    @Override
    public BBox convert(String source) {
        // Decorate source with array brackets if necessary
        String sourceToParse = source;
        if (!source.startsWith(OPENING_BRACKET)) {
            sourceToParse = OPENING_BRACKET + source + CLOSING_BRACKET;
        }
        return gson.fromJson(sourceToParse, BBox.class);

    }
}
