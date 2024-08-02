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
package fr.cnes.regards.modules.catalog.services.plugins.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO to build csv header with basic and dynamic properties.
 *
 * @author Iliana Ghazali
 **/
public record CsvHeader(List<String> basicHeader,
                        List<String> dynamicHeader) {

    public String[] toArray() {
        List<String> concatHeader = new ArrayList<>(basicHeader.size() + dynamicHeader.size());
        concatHeader.addAll(basicHeader);
        concatHeader.addAll(dynamicHeader);
        return concatHeader.toArray(String[]::new);
    }

}
