/* Copyright 2017-2020 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import fr.cnes.regards.framework.geojson.coordinates.Position;
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.framework.geojson.geometry.LineString;
import fr.cnes.regards.framework.geojson.geometry.Polygon;
import fr.cnes.regards.framework.geojson.gson.GeometryTypeAdapterFactory;
import fr.cnes.regards.framework.gson.adapters.*;
import fr.cnes.regards.framework.gson.adapters.actuator.ApplicationMappingsAdapter;
import fr.cnes.regards.framework.gson.adapters.actuator.BeanDescriptorAdapter;
import fr.cnes.regards.framework.gson.adapters.actuator.HealthAdapter;
import fr.cnes.regards.framework.gson.strategy.GsonIgnoreExclusionStrategy;
import fr.cnes.regards.modules.catalog.stac.testutils.random.VavrWrappersRegistry;
import io.github.xshadov.easyrandom.vavr.VavrRandomizerRegistry;
import io.vavr.collection.List;
import io.vavr.gson.VavrGson;
import org.assertj.core.api.Assertions;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.beans.BeansEndpoint;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.web.mappings.MappingsEndpoint;
import org.springframework.util.MimeType;
import org.springframework.util.MultiValueMap;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Base marshalling/unmarshalling test (for DTOs, etc.).
 *
 * @author gandrieu
 */
public abstract class AbstractGsonSerdeTest<T> {

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
        for (int i = 0; i < 100 ; i++) {
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
                LOGGER.error("Different values for {}: \n    FROM: {}\n    TO  : {}", testedType, expectedJson, actualJson);
                LOGGER.error("Different values for {}: \n    FROM: {}\n    TO  : {}", testedType, expected, actual);
            }
            Assertions.assertThat(actualJson).isEqualTo(expectedJson);
        }
    }

    protected abstract void updateRandomParameters(EasyRandom generator, EasyRandomParameters params);

    protected <T> List<T> randomList(Class<T> type, int num) {
        return List.ofAll(random.objects(type, num));
    }

    protected <T> T randomInstance(Class<T> type) {
        return random.nextObject(type);
    }

    protected EasyRandom easyRandom() {
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

    protected IGeometry makeRandomGeometry(EasyRandom generator) {
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

    protected LocalDateTime getLocalDateTime(EasyRandom generator) {
        return LocalDateTime.now().withNano(0).minusSeconds(generator.nextInt(3600 * 24 * 10));
    }

    protected OffsetDateTime getOffsetDateTime(EasyRandom generator) {
        return OffsetDateTime.now().withNano(0).minusSeconds(generator.nextInt(3600 * 24 * 10))
                .withOffsetSameInstant(ZoneOffset.UTC);
    }

    protected Gson gson() {
        GsonBuilder builder = gsonBuilder();
        return builder.create();
    }

    /**
     * Override this to add specific type adapters.
     * @param builder
     * @return the updated builder
     */
    protected GsonBuilder updateGsonBuilder(GsonBuilder builder) {
        return builder;
    }

    protected GsonBuilder gsonBuilder() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeHierarchyAdapter(Path.class, new PathAdapter().nullSafe());
        builder.registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter().nullSafe());

        GeometryTypeAdapterFactory factory = new GeometryTypeAdapterFactory();
        factory.registerSubtype(Polygon.class, "POLYGON");
        factory.registerSubtype(LineString.class, "LINESTRING");
        builder.registerTypeAdapterFactory(factory);

        builder.registerTypeAdapter(MimeType.class, new MimeTypeAdapter().nullSafe());
        builder.registerTypeHierarchyAdapter(Multimap.class, new MultimapAdapter());
        builder.registerTypeHierarchyAdapter(MultiValueMap.class, new MultiValueMapAdapter());
        builder.addSerializationExclusionStrategy(new GsonIgnoreExclusionStrategy());
        builder.registerTypeAdapter(Health.class, new HealthAdapter());
        builder.registerTypeAdapter(BeansEndpoint.BeanDescriptor.class, new BeanDescriptorAdapter());
        builder.registerTypeAdapter(MappingsEndpoint.ApplicationMappings.class, new ApplicationMappingsAdapter());
        VavrGson.registerAll(builder);

        builder = updateGsonBuilder(builder);
        return builder;
    }

}
