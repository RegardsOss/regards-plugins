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

package fr.cnes.regards.modules.catalog.stac.service.utils;

import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Utilities to go from/to base64.
 */
public interface Base64Codec {

    // Remove trailing '=' as a workaround for bad url encoding of links
    Base64.Encoder ENCODER = Base64.getEncoder().withoutPadding();
    Base64.Decoder DECODER = Base64.getDecoder();

    default String toBase64(String content) {
        return ENCODER.encodeToString(content.getBytes(UTF_8));
    }

    default String fromBase64(String b64) {
        // Reset trailing '=' as a workaround for bad url encoding of links
        int paddingNeeded = (4 - (b64.length() % 4)) % 4;
        b64 = b64 + "=".repeat(paddingNeeded);
        return new String(DECODER.decode(b64), UTF_8);
    }

}
