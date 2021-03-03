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

package fr.cnes.regards.modules.catalog.stac.testutils.gson;

import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.cnes.regards.framework.geojson.GeoJsonType;
import fr.cnes.regards.framework.geojson.coordinates.Position;
import fr.cnes.regards.framework.geojson.gson.FeatureTypeAdapterFactory;
import fr.cnes.regards.framework.geojson.gson.GeoJsonTypeAdapter;
import fr.cnes.regards.framework.geojson.gson.GeometryTypeAdapterFactory;
import fr.cnes.regards.framework.geojson.gson.PositionTypeAdapter;
import fr.cnes.regards.framework.gson.adapters.*;
import fr.cnes.regards.framework.gson.adapters.actuator.ApplicationMappingsAdapter;
import fr.cnes.regards.framework.gson.adapters.actuator.BeanDescriptorAdapter;
import fr.cnes.regards.framework.gson.adapters.actuator.HealthAdapter;
import fr.cnes.regards.framework.gson.strategy.GsonIgnoreExclusionStrategy;
import io.vavr.gson.VavrGson;
import org.springframework.boot.actuate.beans.BeansEndpoint;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.web.mappings.MappingsEndpoint;
import org.springframework.util.MimeType;
import org.springframework.util.MultiValueMap;

import java.nio.file.Path;
import java.time.OffsetDateTime;

public interface GsonAwareTest {


    default Gson gson() {
        GsonBuilder builder = gsonBuilder();
        return builder.create();
    }

    /**
     * Override this to add specific type adapters.
     * @param builder
     * @return the updated builder
     */
    default GsonBuilder updateGsonBuilder(GsonBuilder builder) {
        return builder;
    }

    default GsonBuilder gsonBuilder() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeHierarchyAdapter(Path.class, new PathAdapter().nullSafe());
        builder.registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter().nullSafe());

        builder.registerTypeAdapterFactory(new FeatureTypeAdapterFactory());
        builder.registerTypeAdapter(Position.class, new PositionTypeAdapter());
        builder.registerTypeAdapter(GeoJsonType.class, new GeoJsonTypeAdapter());
        builder.registerTypeAdapterFactory(new GeometryTypeAdapterFactory());

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
