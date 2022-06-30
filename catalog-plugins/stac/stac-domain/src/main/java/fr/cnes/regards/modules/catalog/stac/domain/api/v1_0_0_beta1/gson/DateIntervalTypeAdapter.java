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

package fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import fr.cnes.regards.framework.gson.annotation.GsonTypeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.DateInterval;

import java.io.IOException;
import java.util.function.Function;

/**
 * Gson type adapter for DateInterval
 *
 * @see <a href="https://github.com/radiantearth/stac-api-spec/tree/v1.0.0-beta.1/item-search#query-parameter-table">definition</a>
 */
@GsonTypeAdapter(adapted = DateInterval.class)
public class DateIntervalTypeAdapter extends TypeAdapter<DateInterval> {

    @Override
    public void write(JsonWriter out, DateInterval value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.repr());
        }
    }

    @Override
    public DateInterval read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        } else {
            return DateInterval.parseDateInterval(in.nextString())
                               .getOrElseThrow((Function<Throwable, IOException>) IOException::new)
                               .getOrNull();
        }
    }

}
