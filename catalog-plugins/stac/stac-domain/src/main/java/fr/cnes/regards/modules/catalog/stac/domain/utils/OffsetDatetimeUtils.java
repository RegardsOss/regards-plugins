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

package fr.cnes.regards.modules.catalog.stac.domain.utils;

import io.vavr.control.Option;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.OffsetDateTime;

import static java.time.Instant.ofEpochMilli;
import static java.time.ZoneOffset.UTC;

/**
 * TODO: OffsetDatetimeUtils description
 *
 * @author gandrieu
 */
public class OffsetDatetimeUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(OffsetDatetimeUtils.class);

    public static final OffsetDateTime EPOCH = OffsetDateTime.ofInstant(Instant.EPOCH, UTC);

    public static final OffsetDateTime lowestBound() {
        return EPOCH;
    }
    public static final OffsetDateTime uppestBound() {
        return OffsetDateTime.now(UTC).plusDays(10);
    }

    public static final Option<OffsetDateTime> extractTemporalBound(Option<Double> timestamp) {
        return timestamp.map(Double::longValue).flatMap(l -> parseDatetime(l).toOption());
    }

    public static final Try<OffsetDateTime> parseDatetime(Long ts) {
        return Try.of(() -> OffsetDateTime.ofInstant(ofEpochMilli(ts), UTC))
            .onFailure(t -> LOGGER.warn("Could not parse instant from timestamp {}", ts, t));
    }

}
