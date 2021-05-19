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

package fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import fr.cnes.regards.framework.gson.annotation.GsonTypeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import org.apache.commons.lang3.NotImplementedException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

import static fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants.STAC_DATETIME_FORMATTER;

/**
 * Gson type adapter for QueryObject and subclasses.
 */
@GsonTypeAdapter(adapted = ItemSearchBody.QueryObject.class)
public class QueryObjectTypeAdapter extends TypeAdapter<ItemSearchBody.QueryObject> {

    @Override
    public void write(JsonWriter out, ItemSearchBody.QueryObject value) throws IOException {
        if (value == null) {
            out.nullValue();
        }
        else if (value instanceof ItemSearchBody.BooleanQueryObject) {
            ItemSearchBody.BooleanQueryObject bqo = (ItemSearchBody.BooleanQueryObject) value;
            out.beginObject();
            if (bqo.getEq() != null) {
                out.name("eq").value(bqo.getEq());
            }
            if (bqo.getNeq() != null) {
                out.name("neq").value(bqo.getNeq());
            }
            out.endObject();
        } else if (value instanceof ItemSearchBody.NumberQueryObject) {
            ItemSearchBody.NumberQueryObject nqo = (ItemSearchBody.NumberQueryObject) value;
            out.beginObject();
            if (nqo.getEq() != null) {
                out.name("eq").value(nqo.getEq());
            }
            if (nqo.getNeq() != null) {
                out.name("neq").value(nqo.getNeq());
            }
            if (nqo.getGt() != null) {
                out.name("gt").value(nqo.getGt());
            }
            if (nqo.getLt() != null) {
                out.name("lt").value(nqo.getLt());
            }
            if (nqo.getGte() != null) {
                out.name("gte").value(nqo.getGte());
            }
            if (nqo.getLte() != null) {
                out.name("lte").value(nqo.getLte());
            }
            if (nqo.getIn() != null) {
                out.name("in");
                out.beginArray();
                for (Double in : nqo.getIn()) {
                    out.value(in);
                }
                out.endArray();
            }
            out.endObject();
        } else if (value instanceof ItemSearchBody.DatetimeQueryObject) {
            ItemSearchBody.DatetimeQueryObject dqo = (ItemSearchBody.DatetimeQueryObject) value;
            out.beginObject();
            if (dqo.getEq() != null) {
                out.name("eq").value(STAC_DATETIME_FORMATTER.format(dqo.getEq()));
            }
            if (dqo.getNeq() != null) {
                out.name("neq").value(STAC_DATETIME_FORMATTER.format(dqo.getNeq()));
            }
            if (dqo.getGt() != null) {
                out.name("gt").value(STAC_DATETIME_FORMATTER.format(dqo.getGt()));
            }
            if (dqo.getLt() != null) {
                out.name("lt").value(STAC_DATETIME_FORMATTER.format(dqo.getLt()));
            }
            if (dqo.getGte() != null) {
                out.name("gte").value(STAC_DATETIME_FORMATTER.format(dqo.getGte()));
            }
            if (dqo.getLte() != null) {
                out.name("lte").value(STAC_DATETIME_FORMATTER.format(dqo.getLte()));
            }
            if (dqo.getIn() != null) {
                out.name("in");
                out.beginArray();
                for (OffsetDateTime in : dqo.getIn()) {
                    out.value(STAC_DATETIME_FORMATTER.format(in));
                }
                out.endArray();
            }
            out.endObject();
        } else if (value instanceof ItemSearchBody.StringQueryObject) {
            ItemSearchBody.StringQueryObject nqo = (ItemSearchBody.StringQueryObject) value;
            out.beginObject();
            if (nqo.getEq() != null) {
                out.name("eq").value((nqo.getEq()));
            }
            if (nqo.getNeq() != null) {
                out.name("neq").value((nqo.getNeq()));
            }
            if (nqo.getStartsWith() != null) {
                out.name("startsWith").value((nqo.getStartsWith()));
            }
            if (nqo.getEndsWith() != null) {
                out.name("endsWith").value((nqo.getEndsWith()));
            }
            if (nqo.getContains() != null) {
                out.name("contains").value((nqo.getContains()));
            }
            if (nqo.getIn() != null) {
                out.name("in");
                out.beginArray();
                for (String in : nqo.getIn()) {
                    out.value((in));
                }
                out.endArray();
            }
            out.endObject();
        } else {
            throw new NotImplementedException("Missing QueryObject adapter for class: " + value.getClass());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public ItemSearchBody.QueryObject read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        in.beginObject();
        Map<String, Object> inProps = readProps(in);
        in.endObject();
        if (inProps.isEmpty()) {
            return ItemSearchBody.BooleanQueryObject.builder().build();
        } else {
            try {
                Object head = inProps.values().head();
                if (head instanceof List) {
                    head = ((List<Object>) head).headOption()
                            // This should not really happen as a "in" list should not be empty,
                            // even more if there is no other criterion, so it's basically safe
                            // to assume another criterion.
                            .getOrElse(() -> inProps.values().tail().head());
                }

                if (head instanceof Boolean) {
                    return ItemSearchBody.BooleanQueryObject.builder()
                        .eq((Boolean) inProps.get("eq").getOrNull())
                        .neq((Boolean) inProps.get("neq").getOrNull())
                        .build();
                } else if (head instanceof Double) {
                    return ItemSearchBody.NumberQueryObject.builder()
                        .eq((Double) inProps.get("eq").getOrNull())
                        .neq((Double) inProps.get("neq").getOrNull())
                        .gt((Double) inProps.get("gt").getOrNull())
                        .lt((Double) inProps.get("lt").getOrNull())
                        .gte((Double) inProps.get("gte").getOrNull())
                        .lte((Double) inProps.get("lte").getOrNull())
                        .in((List<Double>) inProps.get("in").getOrNull())
                        .build();
                } else if (head instanceof OffsetDateTime) {
                    return ItemSearchBody.DatetimeQueryObject.builder()
                        .eq((OffsetDateTime) inProps.get("eq").getOrNull())
                        .neq((OffsetDateTime) inProps.get("neq").getOrNull())
                        .gt((OffsetDateTime) inProps.get("gt").getOrNull())
                        .lt((OffsetDateTime) inProps.get("lt").getOrNull())
                        .gte((OffsetDateTime) inProps.get("gte").getOrNull())
                        .lte((OffsetDateTime) inProps.get("lte").getOrNull())
                        .in((List<OffsetDateTime>) inProps.get("in").getOrNull())
                        .build();
                } else if (head instanceof String) {
                    return ItemSearchBody.StringQueryObject.builder()
                        .eq((String) inProps.get("eq").getOrNull())
                        .neq((String) inProps.get("neq").getOrNull())
                        .startsWith((String) inProps.get("startsWith").getOrNull())
                        .endsWith((String) inProps.get("endsWith").getOrNull())
                        .contains((String) inProps.get("contains").getOrNull())
                        .in((List<String>) inProps.get("in").getOrNull())
                        .build();
                } else {
                    throw new IOException("Unparsable QueryObject");
                }
            } catch (Exception e) {
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
        } else if (peek == JsonToken.NUMBER) {
            return in.nextDouble();
        } else if (peek == JsonToken.STRING) {
            String strValue = in.nextString();
            try {
                return OffsetDateTime.from(STAC_DATETIME_FORMATTER.parse(strValue));
            } catch (DateTimeParseException e) {
                return strValue;
            }
        } else {
            throw new IOException("Unsupported next token: " + peek);
        }
    }
}
