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

package fr.cnes.regards.modules.catalog.stac.domain.error;

import org.slf4j.Logger;

import java.util.UUID;

/**
 * Positioning a UUID for each request.
 * Provides logging shortcuts to add the current request UUID as log prefix.
 */
public final class StacRequestCorrelationId {

    private static final ThreadLocal<UUID> requestCorrelationId = new ThreadLocal<>();

    public static final String STAC_REQUEST = "STAC Request ";

    public static UUID fresh() {
        UUID cid = UUID.randomUUID();
        requestCorrelationId.set(cid);
        return cid;
    }

    public static UUID currentCId() {
        return requestCorrelationId.get();
    }

    public static void clean() {
        requestCorrelationId.remove();
    }

    public static void trace(Logger logger, String pattern, Object... params) {
        logger.trace(STAC_REQUEST + currentCId() + " - " + pattern, params);
    }

    public static void debug(Logger logger, String pattern, Object... params) {
        logger.debug(STAC_REQUEST + currentCId() + " - " + pattern, params);
    }

    public static void info(Logger logger, String pattern, Object... params) {
        logger.info(STAC_REQUEST + currentCId() + " - " + pattern, params);
    }

    public static void warn(Logger logger, String pattern, Object... params) {
        logger.warn(STAC_REQUEST + currentCId() + " - " + pattern, params);
    }

    public static void error(Logger logger, String pattern, Object... params) {
        logger.error(STAC_REQUEST + currentCId() + " - " + pattern, params);
    }

}
