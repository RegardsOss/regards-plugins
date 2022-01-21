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

package fr.cnes.regards.modules.catalog.stac.service.criterion.query.number;

import fr.cnes.regards.modules.catalog.stac.service.criterion.query.NumberQueryCriterionBuilder;

import static fr.cnes.regards.modules.catalog.stac.service.criterion.query.number.Closedness.CLOSED;
import static fr.cnes.regards.modules.catalog.stac.service.criterion.query.number.Closedness.OPEN;
import static java.lang.Double.NEGATIVE_INFINITY;
import static java.lang.Double.POSITIVE_INFINITY;

/**
 * Describes a double interval boundary.
 */
public class DoubleIntervalBoundary {

    public static final DoubleIntervalBoundary LOWEST = open(NEGATIVE_INFINITY);
    public static final DoubleIntervalBoundary HIGHEST = open(POSITIVE_INFINITY);

    private final Closedness closedness;
    private final double value;

    public DoubleIntervalBoundary(Closedness closedness, double value) {
        this.closedness = closedness;
        this.value = value;
    }

    public Closedness getClosedness() {
        return closedness;
    }

    public double getValue() {
        return value;
    }

    public double lowValue() {
        return closedness == OPEN && value != NEGATIVE_INFINITY ? value - NumberQueryCriterionBuilder.DOUBLE_COMPARISON_PRECISION : value;
    }

    public double highValue() {
        return closedness == OPEN && value != POSITIVE_INFINITY ? value + NumberQueryCriterionBuilder.DOUBLE_COMPARISON_PRECISION : value;
    }

    public static DoubleIntervalBoundary open(double x) {
        return new DoubleIntervalBoundary(OPEN, x);
    }

    public static DoubleIntervalBoundary closed(double x) {
        return new DoubleIntervalBoundary(CLOSED, x);
    }

    public static DoubleIntervalBoundary combineLow(DoubleIntervalBoundary min1, DoubleIntervalBoundary min2) {
        return min1.value == min2.value ? (min1.closedness == min2.closedness ? min1 : new DoubleIntervalBoundary(OPEN, min1.value))
                : min1.value < min2.value ? min2
                : min1;
    }

    public static DoubleIntervalBoundary combineHigh(DoubleIntervalBoundary max1, DoubleIntervalBoundary max2) {
        return max1.value == max2.value ? (max1.closedness == max2.closedness ? max1 : new DoubleIntervalBoundary(OPEN, max1.value))
                : max1.value < max2.value ? max1
                : max2;
    }

    public boolean isClosed() {
        return closedness == CLOSED;
    }

    public boolean isInfiniteHigh() {
        return value == POSITIVE_INFINITY;
    }
    public boolean isInfiniteLow() {
        return value == NEGATIVE_INFINITY;
    }
}
