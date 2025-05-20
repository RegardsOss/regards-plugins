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
 * along with REGARDS. If not, see <http://www.gnu.org/licenses/>.
 */
package fr.cnes.regards.modules.catalog.stac.domain.spec.extensions;

import com.google.gson.annotations.SerializedName;

/**
 * File info extensions. This extensions only implements checksum and size for now and is available for assets.
 * See <a href="https://github.com/stac-extensions/file">File info extensions</a>
 * See <a href="https://github.com/multiformats/multihash">Multihash specification for checksum value</a>
 * See <a href="https://github.com/multiformats/specs">Specs</a>
 *
 * @author Marc SORDI
 */
public class FileInfoExtension {

    /**
     * Extension identifier
     */
    public static final String EXTENSION_ID = "https://stac-extensions.github.io/file/v2.1.0/schema.json";

    /**
     * Extension prefix
     */
    private static final String PREFIX = "file:";

    @SerializedName(PREFIX + "checksum")
    private final String checksum;

    @SerializedName(PREFIX + "size")
    private final long size;

    protected FileInfoExtension(String checksum, long size) {
        this.checksum = checksum;
        this.size = size;
    }

    public static FileInfoExtension fromRawChecksum(String checksum, String algorithm, long size) {
        return new FileInfoExtension(getMultihashChecksum(checksum, algorithm), size);
    }

    public static FileInfoExtension fromMultihash(String multihash, long size) {
        return new FileInfoExtension(multihash, size);
    }

    /**
     * Get the checksum value with the multihash format
     *
     * @param checksum  the checksum value
     * @param algorithm the algorithm used to compute the checksum
     * @return the checksum value with the multihash format
     * <p>
     * Format:
     *     <ul>2 first digits : hash function code (e.g. D5 for MD5)</ul>
     *     <ul>third and fourth digits : hash size (e.g. 10 for 16 bytes)</ul>
     *     <ul>hash value</ul>
     * </p>
     */
    protected static String getMultihashChecksum(String checksum, String algorithm) {
        if (algorithm == null) {
            // Fall back to unknown algorithm
            return "0000" + checksum;
        }
        return switch (algorithm) {
            case "sha1", "SHA-1" -> "1114" + checksum;
            case "sha256", "SHA-256" -> "1220" + checksum;
            case "sha512", "SHA-512" -> "1340" + checksum;
            case "md5", "MD5" -> "D510" + checksum;
            default -> checksum;
        };
    }

    public String getChecksum() {
        return checksum;
    }

    public long getSize() {
        return size;
    }
}
