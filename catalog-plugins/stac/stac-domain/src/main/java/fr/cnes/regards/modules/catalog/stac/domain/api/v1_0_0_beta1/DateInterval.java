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

import io.micrometer.core.instrument.util.StringUtils;
import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Try;
import lombok.Value;

import java.time.Instant;
import java.time.OffsetDateTime;

import static fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants.STAC_DATETIME_FORMATTER;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.DATEINTERVAL_PARSING;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.OffsetDatetimeUtils.parseStacDatetime;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.*;
import static java.lang.String.format;
import static java.time.ZoneOffset.UTC;
import static org.apache.commons.lang3.StringUtils.split;

/**
 * Represents a date or an interval between dates.
 */
@Value
public class DateInterval {

    private static final OffsetDateTime MAX = OffsetDateTime.now(UTC).plusYears(100L);
    private static final OffsetDateTime MIN = OffsetDateTime.ofInstant(Instant.EPOCH, UTC).minusYears(100);

    public static final String OPEN_END = "..";
    public static final String SEPARATOR = "/";

    OffsetDateTime from;
    OffsetDateTime to;

    public boolean isSingleDate() {
        return from.equals(to);
    }

    public static DateInterval largest() {
        return new DateInterval(MIN, MAX);
    }
    public static DateInterval single(OffsetDateTime d) {
        return new DateInterval(d, d);
    }
    public static DateInterval from(OffsetDateTime d) {
        return new DateInterval(d, MAX);
    }
    public static DateInterval to(OffsetDateTime d) {
        return new DateInterval(MIN, d);
    }
    public static DateInterval of(OffsetDateTime f, OffsetDateTime t) {
        return new DateInterval(f, t);
    }

    public String repr() {
        String repr;
        if (isSingleDate()) {
            repr = STAC_DATETIME_FORMATTER.format(from);
        }
        else {
            repr = (MIN.equals(from) ? OPEN_END : STAC_DATETIME_FORMATTER.format(from))
                    + SEPARATOR
                    + (MAX.equals(to) ? OPEN_END : STAC_DATETIME_FORMATTER.format(to));
        }
        return repr;
    }

    public static Try<DateInterval> parseDateInterval(String repr) {
        if (StringUtils.isBlank(repr)) {
            return Try.success(largest());
        }
        else if (repr.contains(SEPARATOR)) {
            return trying(() -> List.of(split(repr, SEPARATOR)))
                .map(parts -> Tuple.of(parts.get(0), parts.get(1)))
                .flatMap(parts -> parseDateOrDefault(parts._1(), MIN)
                    .flatMap(from -> parseDateOrDefault(parts._2(), MAX)
                        .map(to -> DateInterval.of(from, to))
                    )
                )
                .mapFailure(
                    DATEINTERVAL_PARSING,
                    () -> format("Failed to parse date interval from %s", repr)
                );
        }
        else {
            return parseStacDatetime(repr).map(DateInterval::single);
        }
    }

    private static Try<OffsetDateTime> parseDateOrDefault(String repr, OffsetDateTime def) {
        if (OPEN_END.equals(repr)) { return Try.success(def); }
        else { return parseStacDatetime(repr); }
    }

}
