/* Copyright 2017-2022 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.stac.testutils.serde;

import com.google.gson.Gson;
import fr.cnes.regards.modules.catalog.stac.testutils.gson.GsonAwareTest;
import fr.cnes.regards.modules.catalog.stac.testutils.random.RandomAwareTest;
import org.assertj.core.api.Assertions;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base marshalling/unmarshalling test (for DTOs, etc.).
 *
 * @author gandrieu
 */
public abstract class AbstractGsonSerdeTest<T> implements GsonAwareTest, RandomAwareTest {

    protected abstract Class<T> testedType();

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractGsonSerdeTest.class);

    public final EasyRandom random = easyRandom();

    protected final Gson gson = gson();

    protected boolean logValues() {
        return false;
    }

    @Test
    public void test_toJson_fromJson() {
        Class<T> testedType = testedType();
        for (int i = 0; i < 100; i++) {
            T expected = randomInstance(testedType);
            String expectedJson = gson.toJson(expected);
            if (logValues()) {
                LOGGER.info("\nExpected:\n\t{}\nExpected JSON:\n\t{}", expected, expectedJson);
            }
            T actual = gson.fromJson(expectedJson, testedType);
            String actualJson = gson.toJson(actual);
            if (logValues()) {
                LOGGER.info("\nActual:\n\t{}\nActual JSON:\n\t{}", actual, actualJson);
            }
            boolean equal = actualJson.equals(expectedJson);
            if (!equal) {
                LOGGER.error("Different values for {}: \n    FROM: {}\n    TO  : {}",
                             testedType,
                             expectedJson,
                             actualJson);
                LOGGER.error("Different values for {}: \n    FROM: {}\n    TO  : {}", testedType, expected, actual);
            }
            Assertions.assertThat(actualJson).isEqualTo(expectedJson);
        }
    }

}
