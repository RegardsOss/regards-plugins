/*
 * Copyright 2017-2025 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
 * along with REGARDS. If not, see `<http://www.gnu.org/licenses/>`.
 */
package fr.cnes.regards.modules.catalog.stac.domain.utils;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Utility class to compute multihash.
 *
 * @author Julien Canches
 */
public final class MultihashUtils {

    private MultihashUtils() {
    }

    /**
     * Get the checksum value with the multihash format
     *
     * @param algorithm the algorithm used to compute the checksum (one of: md5, MD5, sha1, SHA-1, sha256,
     *                  SHA-256, sha512, SHA-512)
     * @param checksum  the checksum value
     * @return the checksum value with the multihash format
     * @throws UnsupportedOperationException if algorithm is not supported
     * @throws DecoderException              if the checksum is not valid (not an even number of hexadecimal characters)
     */
    @SuppressWarnings("java:S112") // reduce unlikely-to-happen exceptions to a runtime exception
    public static String encode(String algorithm, String checksum)
        throws DecoderException, UnsupportedOperationException {
        int typeIndex = getTypeIndex(algorithm);
        try {
            byte[] rawChecksum = Hex.decodeHex(checksum);
            ByteArrayOutputStream res = new ByteArrayOutputStream();
            writeUnsignedVarint(res, typeIndex);
            writeUnsignedVarint(res, rawChecksum.length);
            res.write(rawChecksum);
            return Hex.encodeHexString(res.toByteArray());
        } catch (IOException e) {
            // Unlikely to happen on a ByteArrayOutputStream
            throw new RuntimeException(e);
        }
    }

    private static int getTypeIndex(String algorithm) {
        return switch (algorithm) {
            case "sha1", "SHA-1" -> 0x11;
            case "sha256", "SHA-256" -> 0x12;
            case "sha512", "SHA-512" -> 0x13;
            case "md5", "MD5" -> 0xd5;
            default -> throw new UnsupportedOperationException(algorithm);
        };
    }

    /**
     * Writes an integer using "unsigned varint" (spec at https://github.com/multiformats/unsigned-varint).
     */
    private static void writeUnsignedVarint(OutputStream out, long u) throws IOException {
        while (u >= 0x80) {
            out.write((byte) (u | 0x80));
            u >>= 7;
        }
        out.write((byte) u);
    }

}
