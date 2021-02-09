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

package fr.cnes.regards.modules.catalog.stac.service.criterion.query.number;

import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;

import static fr.cnes.regards.modules.catalog.stac.service.criterion.query.number.DoubleIntervalBoundary.*;

/**
 * Defines an interval of doubles.
 */
public class DoubleInterval {
    private final DoubleIntervalBoundary min;
    private final DoubleIntervalBoundary max;

    public DoubleInterval(DoubleIntervalBoundary min, DoubleIntervalBoundary max) {
        this.min = min;
        this.max = max;
    }

    public static DoubleInterval lt(double x) {
        return new DoubleInterval(LOWEST, open(x));
    }

    public static DoubleInterval lte(double x) {
        return new DoubleInterval(LOWEST, closed(x));
    }

    public static DoubleInterval gt(double x) {
        return new DoubleInterval(open(x), HIGHEST);
    }

    public static DoubleInterval gte(double x) {
        return new DoubleInterval(closed(x), HIGHEST);
    }

    // We use OPEN for equality because of rounding errors
    public static DoubleInterval eq(double x) {
        return new DoubleInterval(open(x), open(x));
    }

    public static DoubleInterval combine(DoubleInterval i1, DoubleInterval i2) {
        return new DoubleInterval(combineLow(i1.min, i2.min), combineHigh(i1.max, i2.max));
    }

    public ICriterion toCriterion(String prop) {
        if (min.isInfiniteLow()) {
            if (max.isInfiniteHigh()) {
                return ICriterion.all();
            } else {
                if (max.isClosed()) {
                    return ICriterion.lt(prop, max.highValue());
                } else {
                    return ICriterion.le(prop, max.highValue());
                }
            }
        } else if (max.isInfiniteHigh()) {
            if (min.isClosed()) {
                return ICriterion.gt(prop, min.lowValue());
            } else {
                return ICriterion.ge(prop, min.lowValue());
            }
        } else {
            return ICriterion.between(prop, min.lowValue(), min.isClosed(), max.highValue(), max.isClosed());
        }
    }
}
