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

package fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Try;
import lombok.Value;

import java.time.OffsetDateTime;

import static fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants.DATE_FORMAT;
import static fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants.parseDatetime;
import static java.time.OffsetDateTime.MAX;
import static java.time.OffsetDateTime.MIN;
import static org.apache.commons.lang3.StringUtils.split;

/**
 * Represents a date or an interval between dates.
 */
@Value(staticConstructor = "of")
public class DateInterval {

    public static final String OPEN_END = "..";
    public static final String SEPARATOR = "/";

    OffsetDateTime from;
    OffsetDateTime to;

    public boolean isSingleDate() {
        return from.equals(to);
    }

    public static DateInterval single(OffsetDateTime d) {
        return new DateInterval(d, d);
    }
    public static DateInterval from(OffsetDateTime d) {
        return new DateInterval(d, OffsetDateTime.MAX);
    }
    public static DateInterval to(OffsetDateTime d) {
        return new DateInterval(OffsetDateTime.MIN, d);
    }

    public String repr() {
        String repr;
        if (isSingleDate()) {
            repr = DATE_FORMAT.format(from);
        }
        else {
            repr = (MIN.equals(from) ? OPEN_END : DATE_FORMAT.format(from))
                    + SEPARATOR
                    + (MAX.equals(to) ? OPEN_END : DATE_FORMAT.format(to));
        }
        return repr;
    }

    public static Try<DateInterval> parseDateInterval(String repr) {
        if (repr.contains(SEPARATOR)) {
            return Try.of(() -> List.of(split(repr, SEPARATOR)))
                .map(parts -> Tuple.of(parts.get(0), parts.get(1)))
                .flatMap(parts -> parseDateOrDefault(parts._1(), MIN)
                    .flatMap(from -> parseDateOrDefault(parts._2(), MAX)
                        .map(to -> DateInterval.of(from, to))
                    )
                );
        }
        else {
            return parseDatetime(repr).map(DateInterval::single);
        }
    }

    private static Try<OffsetDateTime> parseDateOrDefault(String repr, OffsetDateTime def) {
        if (OPEN_END.equals(repr)) { return Try.success(def); }
        else { return parseDatetime(repr); }
    }

}
