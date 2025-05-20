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

package fr.cnes.regards.modules.catalog.stac.domain.utils;

import fr.cnes.regards.modules.catalog.stac.domain.StacConstants;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.OffsetDateTime;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.OFFSETDATETIME_PARSING;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;
import static java.lang.String.format;
import static java.time.Instant.ofEpochMilli;
import static java.time.ZoneOffset.UTC;

/**
 * Utility class for OffsetDateTime.
 *
 * @author gandrieu
 */
public final class OffsetDatetimeUtils {

    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(OffsetDatetimeUtils.class);

    public static final OffsetDateTime EPOCH = OffsetDateTime.ofInstant(Instant.EPOCH, UTC);

    public static OffsetDateTime lowestBound() {
        return EPOCH;
    }

    public static OffsetDateTime upperBound() {
        return OffsetDateTime.now(UTC).plusDays(10);
    }

    public static Option<OffsetDateTime> extractTemporalBound(Option<Double> timestamp) {
        return timestamp.map(Double::longValue).flatMap(l -> parseDatetime(l).toOption());
    }

    public static Try<OffsetDateTime> parseDatetime(Long ts) {
        return trying(() -> OffsetDateTime.ofInstant(ofEpochMilli(ts), UTC)).mapFailure(OFFSETDATETIME_PARSING,
                                                                                        () -> format(
                                                                                            "Could not parse instant from timestamp %s",
                                                                                            ts));
    }

    public static Try<OffsetDateTime> parseStacDatetime(String repr) {
        return trying(() -> OffsetDateTime.from(StacConstants.ISO_DATE_TIME_UTC.parse(repr))).mapFailure(
            OFFSETDATETIME_PARSING,
            () -> format("Failed to parse datetime from %s", repr));
    }

    private OffsetDatetimeUtils() {
        // Utility class
    }
}
