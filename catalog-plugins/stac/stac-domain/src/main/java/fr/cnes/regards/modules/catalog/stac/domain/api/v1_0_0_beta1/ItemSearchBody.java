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

package fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import lombok.Value;
import org.apache.commons.lang3.NotImplementedException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

import static fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants.DATE_FORMAT;

/**
 * Describes the body of "POST /search" request.
 *
 * @see <a href="">Description</a>
 */
@Value
public class ItemSearchBody {

    BBox bbox;
    String datetime;
    IGeometry intersects;
    List<String> collections;
    List<String> ids;
    Integer limit;
    Fields fields;
    Map<String, QueryObject> query;
    List<SortBy> sortBy;

    @Value
    public static class SortBy {
        enum Direction {
            @SerializedName("asc") ASC, @SerializedName("desc") DESC;
        }
        String field;
        Direction direction;
    }

    @Value
    public static class Fields {
        List<String> includes;
        List<String> excludes;
    }

    public interface QueryObject {}
    @Value
    public static class BooleanQueryObject implements QueryObject {
        Boolean eq;
        Boolean neq;
    }
    @Value
    public static class NumberQueryObject implements QueryObject {
        Double eq;
        Double neq;
        Double gt;
        Double lt;
        Double gte;
        Double lte;
        List<Double> in;
    }
    @Value
    public static class DatetimeQueryObject implements QueryObject {
        OffsetDateTime eq;
        OffsetDateTime neq;
        OffsetDateTime gt;
        OffsetDateTime lt;
        OffsetDateTime gte;
        OffsetDateTime lte;
        List<OffsetDateTime> in;
    }
    @Value
    public static class StringQueryObject implements QueryObject {
        String eq;
        String neq;
        String gt;
        String lt;
        String gte;
        String lte;
        String startsWith;
        String endsWith;
        String contains;
        List<String> in;
    }

    public static class QueryObjectTypeAdapter extends TypeAdapter<QueryObject> {

        @Override
        public void write(JsonWriter out, QueryObject value) throws IOException {
            if (value instanceof BooleanQueryObject) {
                BooleanQueryObject bqo = (BooleanQueryObject) value;
                out.beginObject();
                if (bqo.eq != null) {out.name("eq").value(bqo.eq);}
                if (bqo.neq != null) {out.name("neq").value(bqo.neq);}
                out.endObject();
            }
            else if (value instanceof NumberQueryObject) {
                NumberQueryObject nqo = (NumberQueryObject) value;
                out.beginObject();
                if (nqo.eq != null) {out.name("eq").value(nqo.eq);}
                if (nqo.neq != null) {out.name("neq").value(nqo.neq);}
                if (nqo.gt != null) {out.name("gt").value(nqo.gt);}
                if (nqo.lt != null) {out.name("lt").value(nqo.lt);}
                if (nqo.gte != null) {out.name("gte").value(nqo.gte);}
                if (nqo.lte != null) {out.name("lte").value(nqo.lte);}
                if (nqo.in != null) {
                    out.name("in");
                    out.beginArray();
                    for (Double in : nqo.in) { out.value(in); }
                    out.endArray();
                }
                out.endObject();
            }
            else if (value instanceof DatetimeQueryObject) {
                DatetimeQueryObject nqo = (DatetimeQueryObject) value;
                out.beginObject();
                if (nqo.eq != null) {out.name("eq").value(DATE_FORMAT.format(nqo.eq));}
                if (nqo.neq != null) {out.name("neq").value(DATE_FORMAT.format(nqo.neq));}
                if (nqo.gt != null) {out.name("gt").value(DATE_FORMAT.format(nqo.gt));}
                if (nqo.lt != null) {out.name("lt").value(DATE_FORMAT.format(nqo.lt));}
                if (nqo.gte != null) {out.name("gte").value(DATE_FORMAT.format(nqo.gte));}
                if (nqo.lte != null) {out.name("lte").value(DATE_FORMAT.format(nqo.lte));}
                if (nqo.in != null) {
                    out.name("in");
                    out.beginArray();
                    for (OffsetDateTime in : nqo.in) { out.value(DATE_FORMAT.format(in)); }
                    out.endArray();
                }
                out.endObject();
            }
            else if (value instanceof StringQueryObject) {
                StringQueryObject nqo = (StringQueryObject) value;
                out.beginObject();
                if (nqo.eq != null) {out.name("eq").value((nqo.eq));}
                if (nqo.neq != null) {out.name("neq").value((nqo.neq));}
                if (nqo.gt != null) {out.name("gt").value((nqo.gt));}
                if (nqo.lt != null) {out.name("lt").value((nqo.lt));}
                if (nqo.gte != null) {out.name("gte").value((nqo.gte));}
                if (nqo.lte != null) {out.name("lte").value((nqo.lte));}
                if (nqo.startsWith != null) {out.name("startsWith").value((nqo.startsWith));}
                if (nqo.endsWith != null) {out.name("endsWith").value((nqo.endsWith));}
                if (nqo.contains != null) {out.name("contains").value((nqo.contains));}
                if (nqo.in != null) {
                    out.name("in");
                    out.beginArray();
                    for (String in : nqo.in) { out.value((in)); }
                    out.endArray();
                }
                out.endObject();
            }
            else {
                throw new NotImplementedException("Missing QueryObject adapter for class: " + value.getClass());
            }
        }

        @Override
        public QueryObject read(JsonReader in) throws IOException {
            in.beginObject();
            QueryObject result;
            Map<String, Object> inProps = readProps(in);
            in.endObject();
            if (inProps.isEmpty()) {
                return new BooleanQueryObject(null, null);
            }
            else {
                try {
                    Object head = inProps.values().head();
                    if (head instanceof List) {
                        head = ((List<Object>)head).headOption()
                            // This should not really happen as a "in" list should not be empty,
                            // even more if there is no other criterion, so it's basically safe
                            // to assume another criterion.
                            .getOrElse(() -> inProps.values().tail().head());
                    }

                    if (head instanceof Boolean) {
                        return new BooleanQueryObject(
                            (Boolean) inProps.get("eq").getOrNull(),
                            (Boolean) inProps.get("neq").getOrNull()
                        );
                    } else if (head instanceof Double) {
                        return new NumberQueryObject(
                            (Double) inProps.get("eq").getOrNull(),
                            (Double) inProps.get("neq").getOrNull(),
                            (Double) inProps.get("gt").getOrNull(),
                            (Double) inProps.get("lt").getOrNull(),
                            (Double) inProps.get("gte").getOrNull(),
                            (Double) inProps.get("lte").getOrNull(),
                            (List<Double>) inProps.get("in").getOrNull()
                        );
                    } else if (head instanceof OffsetDateTime) {
                        return new DatetimeQueryObject(
                            (OffsetDateTime) inProps.get("eq").getOrNull(),
                            (OffsetDateTime) inProps.get("neq").getOrNull(),
                            (OffsetDateTime) inProps.get("gt").getOrNull(),
                            (OffsetDateTime) inProps.get("lt").getOrNull(),
                            (OffsetDateTime) inProps.get("gte").getOrNull(),
                            (OffsetDateTime) inProps.get("lte").getOrNull(),
                            (List<OffsetDateTime>) inProps.get("in").getOrNull()
                        );
                    } else if (head instanceof String) {
                        return new StringQueryObject(
                            (String) inProps.get("eq").getOrNull(),
                            (String) inProps.get("neq").getOrNull(),
                            (String) inProps.get("gt").getOrNull(),
                            (String) inProps.get("lt").getOrNull(),
                            (String) inProps.get("gte").getOrNull(),
                            (String) inProps.get("lte").getOrNull(),
                            (String) inProps.get("startsWith").getOrNull(),
                            (String) inProps.get("endsWith").getOrNull(),
                            (String) inProps.get("contains").getOrNull(),
                            (List<String>) inProps.get("in").getOrNull()
                        );
                    } else {
                        throw new IOException("Unparsable QueryObject");
                    }
                }
                catch(Exception e) {
                    throw new IOException(e);
                }
            }
        }

        private Map<String, Object> readProps(JsonReader in) throws IOException {
            JsonToken peek = in.peek();
            Map<String, Object> result = HashMap.empty();
            while (peek != JsonToken.END_OBJECT) {
                result = result.put(readProp(in));
                peek = in.peek();
            }
            return result;
        }

        private Tuple2<String, Object> readProp(JsonReader in) throws IOException {
            String name = in.nextName();
            JsonToken peek = in.peek();
            if (peek == JsonToken.BEGIN_ARRAY) {
                in.beginArray();
                java.util.List<Object> array = new ArrayList<>();
                while (in.peek() != JsonToken.END_ARRAY) {
                    array.add(readNextValue(in));
                }
                in.endArray();
                return Tuple.of(name, List.ofAll(array));
            } else {
                return Tuple.of(name, readNextValue(in));
            }
        }

        private Object readNextValue(JsonReader in) throws IOException {
            JsonToken peek = in.peek();
            if (peek == JsonToken.BOOLEAN) {
                return in.nextBoolean();
            }
            else if (peek == JsonToken.NUMBER) {
                return in.nextDouble();
            }
            else if (peek == JsonToken.STRING) {
                String strValue = in.nextString();
                try {
                    return OffsetDateTime.from(DATE_FORMAT.parse(strValue));
                }
                catch(DateTimeParseException e) {
                    return strValue;
                }
            }
            else {
                throw new IOException("Unsupported next token: " + peek);
            }
        }
    }


}
