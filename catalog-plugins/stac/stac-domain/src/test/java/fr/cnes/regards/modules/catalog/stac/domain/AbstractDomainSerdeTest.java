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

package fr.cnes.regards.modules.catalog.stac.domain;

import com.google.gson.GsonBuilder;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.DateInterval;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.gson.BBoxTypeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.gson.DateIntervalTypeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.gson.QueryObjectTypeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.testutils.serde.AbstractGsonSerdeTest;
import io.vavr.Tuple;
import io.vavr.collection.List;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;

import java.time.OffsetDateTime;

public abstract class AbstractDomainSerdeTest<T> extends AbstractGsonSerdeTest<T> {

    @Override
    public void updateRandomParameters(EasyRandom generator, EasyRandomParameters params) {
        params.randomize(Extent.Temporal.class,
                         () -> new Extent.Temporal(List.range(0, generator.nextInt(10))
                                                       .map(i -> Tuple.of(generator.nextBoolean() ?
                                                                              null :
                                                                              generator.nextObject(OffsetDateTime.class),
                                                                          generator.nextBoolean() ?
                                                                              null :
                                                                              generator.nextObject(OffsetDateTime.class)))))
              .randomize(SearchBody.QueryObject.class, () -> {
                  return generator.nextBoolean() ?
                      generator.nextBoolean() ?
                          generator.nextObject(SearchBody.BooleanQueryObject.class) :
                          generator.nextObject(SearchBody.NumberQueryObject.class) :
                      generator.nextObject(SearchBody.StringQueryObject.class);
              })
              .randomize(DateInterval.class, () -> {
                  return generator.nextBoolean() ?
                      DateInterval.single(generator.nextObject(OffsetDateTime.class)) :
                      generator.nextBoolean() ?
                          DateInterval.from(generator.nextObject(OffsetDateTime.class)) :
                          generator.nextBoolean() ?
                              DateInterval.to(generator.nextObject(OffsetDateTime.class)) :
                              DateInterval.of(generator.nextObject(OffsetDateTime.class),
                                              generator.nextObject(OffsetDateTime.class));
              });
    }

    @Override
    public GsonBuilder updateGsonBuilder(GsonBuilder builder) {
        builder.registerTypeAdapter(BBox.class, new BBoxTypeAdapter());
        builder.registerTypeAdapter(SearchBody.QueryObject.class, new QueryObjectTypeAdapter());
        builder.registerTypeAdapter(DateInterval.class, new DateIntervalTypeAdapter());
        return builder;
    }
}
