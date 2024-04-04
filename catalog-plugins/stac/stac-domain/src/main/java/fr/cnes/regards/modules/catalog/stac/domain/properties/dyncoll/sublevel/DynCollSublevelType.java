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

package fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import io.vavr.collection.List;

/**
 * Sublevel types.
 */
public interface DynCollSublevelType {

    String name();

    boolean applicableTo(StacPropertyType propType);

    enum DatetimeBased implements DynCollSublevelType {
        YEAR, MONTH, DAY, HOUR, MINUTE;

        public static final DatetimeBased SMALLEST = MINUTE;

        @Override
        public boolean applicableTo(StacPropertyType propType) {
            return propType == StacPropertyType.DATETIME;
        }

        public List<DatetimeBased> allPrevious() {
            return List.of(values()).takeUntil(t -> t == this).append(this);
        }

        public static void main(String[] args) {
            System.out.println(HOUR.allPrevious());
        }
    }

    enum NumberBased implements DynCollSublevelType {
        RANGE;

        @Override
        public boolean applicableTo(StacPropertyType propType) {
            switch (propType) {
                case NUMBER:
                case LENGTH:
                case ANGLE:
                case PERCENTAGE:
                    return true;
                default:
                    return false;
            }
        }
    }

    enum StringBased implements DynCollSublevelType {
        PREFIX;

        @Override
        public boolean applicableTo(StacPropertyType propType) {
            return propType == StacPropertyType.STRING;
        }
    }

    enum Default implements DynCollSublevelType {
        EXACT;

        @Override
        public boolean applicableTo(StacPropertyType propType) {
            return true;
        }
    }

}
