/*
 * Copyright 2017-2024 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

package fr.cnes.regards.modules.catalog.stac.domain.api.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import fr.cnes.regards.framework.gson.annotation.GsonTypeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.StacConstants;
import fr.cnes.regards.modules.catalog.stac.domain.api.SearchBody;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Seq;
import org.apache.commons.lang3.NotImplementedException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

/**
 * Gson type adapter for QueryObject and subclasses.
 *
 * @author gandrieu
 */
@GsonTypeAdapter(adapted = SearchBody.QueryObject.class)
public class QueryObjectTypeAdapter extends TypeAdapter<SearchBody.QueryObject> {

    @Override
    public void write(JsonWriter out, SearchBody.QueryObject value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else if (value instanceof SearchBody.BooleanQueryObject bqo) {
            writeBooleanQueryObject(out, bqo);
        } else if (value instanceof SearchBody.NumberQueryObject nqo) {
            writeNumberQueryObject(out, nqo);
        } else if (value instanceof SearchBody.DatetimeQueryObject dqo) {
            writeDatetimeQueryObject(out, dqo);
        } else if (value instanceof SearchBody.StringQueryObject sqo) {
            writeStringQueryObject(out, sqo);
        } else {
            throw new NotImplementedException("Missing QueryObject adapter for class: " + value.getClass());
        }
    }

    private void writeBooleanQueryObject(JsonWriter out, SearchBody.BooleanQueryObject bqo) throws IOException {
        out.beginObject();
        if (bqo.getEq() != null) {
            out.name("eq").value(bqo.getEq());
        }
        if (bqo.getNeq() != null) {
            out.name("neq").value(bqo.getNeq());
        }
        out.endObject();
    }

    private void writeNumberQueryObject(JsonWriter out, SearchBody.NumberQueryObject nqo) throws IOException {
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
    }

    private void writeDatetimeQueryObject(JsonWriter out, SearchBody.DatetimeQueryObject dqo) throws IOException {
        out.beginObject();
        if (dqo.getEq() != null) {
            out.name("eq").value(StacConstants.ISO_DATE_TIME_UTC.format(dqo.getEq()));
        }
        if (dqo.getNeq() != null) {
            out.name("neq").value(StacConstants.ISO_DATE_TIME_UTC.format(dqo.getNeq()));
        }
        if (dqo.getGt() != null) {
            out.name("gt").value(StacConstants.ISO_DATE_TIME_UTC.format(dqo.getGt()));
        }
        if (dqo.getLt() != null) {
            out.name("lt").value(StacConstants.ISO_DATE_TIME_UTC.format(dqo.getLt()));
        }
        if (dqo.getGte() != null) {
            out.name("gte").value(StacConstants.ISO_DATE_TIME_UTC.format(dqo.getGte()));
        }
        if (dqo.getLte() != null) {
            out.name("lte").value(StacConstants.ISO_DATE_TIME_UTC.format(dqo.getLte()));
        }
        if (dqo.getIn() != null) {
            out.name("in");
            out.beginArray();
            for (OffsetDateTime in : dqo.getIn()) {
                out.value(StacConstants.ISO_DATE_TIME_UTC.format(in));
            }
            out.endArray();
        }
        out.endObject();
    }

    private void writeStringQueryObject(JsonWriter out, SearchBody.StringQueryObject sqo) throws IOException {
        out.beginObject();
        if (sqo.getEq() != null) {
            out.name("eq").value(sqo.getEq());
        }
        if (sqo.getNeq() != null) {
            out.name("neq").value(sqo.getNeq());
        }
        if (sqo.getStartsWith() != null) {
            out.name("startsWith").value(sqo.getStartsWith());
        }
        if (sqo.getEndsWith() != null) {
            out.name("endsWith").value(sqo.getEndsWith());
        }
        if (sqo.getContains() != null) {
            out.name("contains").value(sqo.getContains());
        }
        if (sqo.getContainsAll() != null) {
            out.name("containsAll");
            out.beginArray();
            for (String in : sqo.getContainsAll()) {
                out.value((in));
            }
            out.endArray();
        }
        if (sqo.getIn() != null) {
            out.name("in");
            out.beginArray();
            for (String in : sqo.getIn()) {
                out.value((in));
            }
            out.endArray();
        }
        if (sqo.getMatchType() != null) {
            out.name("matchType").value(sqo.getMatchType());
        }
        out.endObject();
    }

    private Object findFirstDiscriminantValue(Seq<Object> values) {
        Object head = values.head();
        if (head instanceof List) {
            // Get first element of the list if not empty or find deeper
            return ((List<Object>) head).headOption().getOrElse(() -> findFirstDiscriminantValue(values.tail()));
            // This should not really happen as a list should not be empty,
            // even more if there is no other criterion, so it's basically safe
            // to assume another criterion.
        }
        return head;
    }

    @SuppressWarnings("unchecked")
    @Override
    public SearchBody.QueryObject read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        in.beginObject();
        Map<String, Object> inProps = readProps(in);
        in.endObject();
        if (inProps.isEmpty()) {
            return SearchBody.BooleanQueryObject.builder().build();
        }
        // Try to find first discriminant value
        Object head = findFirstDiscriminantValue(inProps.values());

        if (head instanceof Boolean) {
            return SearchBody.BooleanQueryObject.builder()
                                                .eq((Boolean) inProps.get("eq").getOrNull())
                                                .neq((Boolean) inProps.get("neq").getOrNull())
                                                .build();
        } else if (head instanceof Double) {
            return SearchBody.NumberQueryObject.builder()
                                               .eq((Double) inProps.get("eq").getOrNull())
                                               .neq((Double) inProps.get("neq").getOrNull())
                                               .gt((Double) inProps.get("gt").getOrNull())
                                               .lt((Double) inProps.get("lt").getOrNull())
                                               .gte((Double) inProps.get("gte").getOrNull())
                                               .lte((Double) inProps.get("lte").getOrNull())
                                               .in((List<Double>) inProps.get("in").getOrNull())
                                               .build();
        } else if (head instanceof OffsetDateTime) {
            return SearchBody.DatetimeQueryObject.builder()
                                                 .eq((OffsetDateTime) inProps.get("eq").getOrNull())
                                                 .neq((OffsetDateTime) inProps.get("neq").getOrNull())
                                                 .gt((OffsetDateTime) inProps.get("gt").getOrNull())
                                                 .lt((OffsetDateTime) inProps.get("lt").getOrNull())
                                                 .gte((OffsetDateTime) inProps.get("gte").getOrNull())
                                                 .lte((OffsetDateTime) inProps.get("lte").getOrNull())
                                                 .in((List<OffsetDateTime>) inProps.get("in").getOrNull())
                                                 .build();
        } else if (head instanceof String) {
            return SearchBody.StringQueryObject.builder()
                                               .eq((String) inProps.get("eq").getOrNull())
                                               .neq((String) inProps.get("neq").getOrNull())
                                               .startsWith((String) inProps.get("startsWith").getOrNull())
                                               .endsWith((String) inProps.get("endsWith").getOrNull())
                                               .contains((String) inProps.get("contains").getOrNull())
                                               .containsAll((List<String>) inProps.get("containsAll").getOrNull())
                                               .in((List<String>) inProps.get("in").getOrNull())
                                               .matchType((String) inProps.get("matchType").getOrNull())
                                               .build();
        } else {
            throw new IOException("QueryObject not parsable");
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
                return OffsetDateTime.from(StacConstants.ISO_DATE_TIME_UTC.parse(strValue));
            } catch (DateTimeParseException e) {
                return strValue;
            }
        } else {
            throw new IOException("Unsupported next token: " + peek);
        }
    }
}
