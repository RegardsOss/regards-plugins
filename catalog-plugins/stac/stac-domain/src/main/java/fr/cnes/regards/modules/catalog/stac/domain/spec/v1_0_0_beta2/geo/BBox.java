/*
 * Copyright 2017-2020 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

package fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import fr.cnes.regards.framework.gson.annotation.GsonTypeAdapter;
import lombok.Value;
import lombok.With;

import java.io.IOException;

/**
 * Bounding box of a geometry
 */
@Value @With
public class BBox {

    double minX;
    double minY;
    double maxX;
    double maxY;

    public Centroid centroid() {
        return new Centroid((minX+maxX)/2d, (minY+maxY)/2d);
    }

    @GsonTypeAdapter(adapted = BBox.class)
    public static class BBoxTypeAdapter extends TypeAdapter<BBox> {
        @Override
        public void write(JsonWriter out, BBox value) throws IOException {
            out.beginArray();
            out.value(value.minX);
            out.value(value.minY);
            out.value(value.maxX);
            out.value(value.maxY);
            out.endArray();
        }

        @Override
        public BBox read(JsonReader in) throws IOException {
            in.beginArray();
            double minX = in.nextDouble();
            double minY = in.nextDouble();
            double maxX = in.nextDouble();
            double maxY = in.nextDouble();
            in.endArray();
            return new BBox(minX, minY, maxX, maxY);
        }
    }
}
