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

package fr.cnes.regards.modules.catalog.stac.domain.properties.conversion;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import io.vavr.control.Try;
import org.apache.commons.lang3.NotImplementedException;

/**
 * REGARDS/STAC percentage converter, allowing to convert from ratio representation
 * (float value between 0 and 1) to percentage point representation (value from 0 to 100).
 */
public class PercentagePropertyConverter extends AbstractPropertyConverter<Double, Double> {

    public enum PercentageBase {
        /** Means that the percentage is expressed as a ratio: a number between 0 and 1 */
        ONE,
        /** Means that the percentage is expressed as percentage points: a number between 0 and 100 ; default value. */
        HUNDRED,
        ;

        public static PercentageBase parsePercentageBase(String format) {
            return Try.of(() -> PercentageBase.valueOf(format.trim())).getOrElse(PercentageBase.HUNDRED);
        }
    }

    private final PercentageBase stacBase;
    private final PercentageBase regardsBase;

    public PercentagePropertyConverter(PercentageBase stacBase, PercentageBase regardsBase) {
        super(StacPropertyType.PERCENTAGE);
        this.stacBase = stacBase;
        this.regardsBase = regardsBase;
    }

    @Override
    public Try<Double> convertRegardsToStac(Double regardsValue) {
        return convert(regardsValue, regardsBase, stacBase);
    }

    @Override
    public Try<Double> convertStacToRegards(Double stacValue) {
        return convert(stacValue, stacBase, regardsBase);
    }

    private Try<Double> convert(Double fromValue, PercentageBase fromBase, PercentageBase toBase) {
        return Try.of(() -> {
            switch (fromBase) {
                case ONE:
                    switch (toBase) {
                        case ONE:
                            return fromValue;
                        case HUNDRED:
                            return fromValue * 100d;
                        default:
                            throw new NotImplementedException("Missing case for PercentageBase value: " + fromBase);
                    }
                case HUNDRED:
                    switch (toBase) {
                        case ONE:
                            return fromValue / 100d;
                        case HUNDRED:
                            return fromValue;
                        default:
                            throw new NotImplementedException("Missing case for PercentageBase value: " + fromBase);
                    }
                default:
                    throw new NotImplementedException("Missing case for PercentageBase value: " + toBase);
            }
        });
    }

}
