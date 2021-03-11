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

package fr.cnes.regards.modules.catalog.stac.testutils.random;

import com.google.gson.JsonObject;
import fr.cnes.regards.framework.geojson.coordinates.Position;
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import io.github.xshadov.easyrandom.vavr.VavrRandomizerRegistry;
import io.vavr.collection.List;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public interface RandomAwareTest {

    default void updateRandomParameters(EasyRandom generator, EasyRandomParameters params) {}

    default <T> List<T> randomList(Class<T> type, int num) {
        return randomList(easyRandom(), type, num);
    }

    default <T> List<T> randomList(EasyRandom easyRandom, Class<T> type, int num) {
        return List.ofAll(easyRandom.objects(type, num));
    }

    default <T> T randomInstance(Class<T> type) {
        return easyRandom().nextObject(type);
    }

    default EasyRandom easyRandom() {
        VavrWrappersRegistry vavrWrappersRegistry = new VavrWrappersRegistry();
        VavrRandomizerRegistry vavrRandomizerRegistry = new VavrRandomizerRegistry();

        EasyRandomParameters parameters = new EasyRandomParameters();
        parameters.randomizerRegistry(vavrWrappersRegistry);
        parameters.randomizerRegistry(vavrRandomizerRegistry);

        EasyRandom generator = new EasyRandom(parameters);
        vavrRandomizerRegistry.setEasyRandom(generator);
        vavrWrappersRegistry.setEasyRandom(generator);

        parameters.collectionSizeRange(0, 10)
                .randomize(Duration.class, () -> Duration.ofSeconds(generator.nextInt(3600 * 24 * 10)))
                .randomize(IGeometry.class, () -> makeRandomGeometry(generator))
                .randomize(OffsetDateTime.class, () -> getOffsetDateTime(generator))
                .randomize(LocalDateTime.class, () -> getLocalDateTime(generator))
                .randomize(Object.class, () -> generator.nextObject(JsonObject.class));

        updateRandomParameters(generator, parameters);
        return generator;
    }

    default IGeometry makeRandomGeometry(EasyRandom generator) {
        if (generator.nextBoolean()) {
            return IGeometry.polygon(IGeometry.toPolygonCoordinates(IGeometry.positions(new Position[]{
                    IGeometry.position(generator.nextDouble(), generator.nextDouble()),
                    IGeometry.position(generator.nextDouble(), generator.nextDouble()),
                    IGeometry.position(generator.nextDouble(), generator.nextDouble()),
                    IGeometry.position(generator.nextDouble(), generator.nextDouble()),
                    IGeometry.position(generator.nextDouble(), generator.nextDouble())
            })));
        }
        else {
            return IGeometry.lineString(
                    generator.nextDouble(), generator.nextDouble(),
                    generator.nextDouble(), generator.nextDouble(),
                    generator.nextDouble(), generator.nextDouble());
        }
    }

    default LocalDateTime getLocalDateTime(EasyRandom generator) {
        return LocalDateTime.now().withNano(0).minusSeconds(generator.nextInt(3600 * 24 * 10));
    }

    default OffsetDateTime getOffsetDateTime(EasyRandom generator) {
        return OffsetDateTime.now().withNano(0).minusSeconds(generator.nextInt(3600 * 24 * 10))
                .withOffsetSameInstant(ZoneOffset.UTC);
    }

}
