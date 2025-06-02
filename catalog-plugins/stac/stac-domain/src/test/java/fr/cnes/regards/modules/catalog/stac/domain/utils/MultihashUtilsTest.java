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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Julien Canches
 */
class MultihashUtilsTest {

    private static List<Arguments> encode() {
        return List.of(Arguments.of("md5",
                                    "E908F5B5EC9DEDF0CB5BD964E1206F55",
                                    "d50110e908f5b5ec9dedf0cb5bd964e1206f55"),
                       Arguments.of("sha1",
                                    "8AE7C61A25D0236160CF1426115BF674C09E1FAB",
                                    "11148ae7c61a25d0236160cf1426115bf674c09e1fab"),
                       Arguments.of("sha256",
                                    "5B3594C5DB15B9528A815452932E142B9EA9A04D07A5A51AB9335E494D84D07E",
                                    "12205b3594c5db15b9528a815452932e142b9ea9a04d07a5a51ab9335e494d84d07e"),
                       Arguments.of("sha512",
                                    "C1B70B247486CA7A23C688F4D6050A6945166D0171D9A282F91F1F2653EB35B36F0F615049AC1F80EC7314955C849E7CF27EE83EC7CB3C1E058568A9B27D5EBF",
                                    "1340c1b70b247486ca7a23c688f4d6050a6945166d0171d9a282f91f1f2653eb35b36f0f615049ac1f80ec7314955c849e7cf27ee83ec7cb3c1e058568a9b27d5ebf"));
    }

    @ParameterizedTest
    @MethodSource
    void encode(String algorithm, String checksum, String expectedMultihash) throws DecoderException {
        assertThat(MultihashUtils.encode(algorithm, checksum)).isEqualTo(expectedMultihash);
    }

    @Test
    void encodeUnsupportedAlgorithm() {
        assertThatThrownBy(() -> MultihashUtils.encode("unsupported", "0123456789abcdef")).isInstanceOf(
            UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "wxyz", "123" })
    void encodeInvalidChecksum(String checksum) {
        assertThatThrownBy(() -> MultihashUtils.encode("md5", checksum)).isInstanceOf(DecoderException.class);
    }
}
