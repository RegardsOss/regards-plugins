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

import static fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody.SortBy.Direction.ASC;
import static fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody.SortBy.Direction.DESC;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.FIELDS_PARSING;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.SORTBY_PARSING;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;
import static java.lang.String.format;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody.Fields;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody.ReturnedType;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody.SortBy;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import io.vavr.control.Try;

/**
 * Implementation for the ItemSearchBodyFactory interface.
 */
@Component
public class ItemSearchBodyFactoryImpl implements ItemSearchBodyFactory {

    private final Gson gson;

    @Autowired
    public ItemSearchBodyFactoryImpl(Gson gson) {
        this.gson = gson;
    }

    // @formatter:off

    @Override
    public Try<ItemSearchBody> parseItemSearch(
            Integer limit,
            BBox bbox,
            String datetime,
            List<String> collections,
            List<String> ids,
            String fields,
            String query,
            String sortBy,
            ReturnedType returnedType
    ) {
        return parseDateInterval(datetime).flatMap(dt ->
            parseFields(fields).flatMap(f ->
                parseQuery(query).flatMap(q ->
                    parseSortBy(sortBy).map(s ->
                        new ItemSearchBody(
                            bbox, dt.getOrNull(), null, collections, ids, limit, f, q, s, returnedType
                        )
                    )
                )
            )
        );
    }

    private Try<Option<DateInterval>> parseDateInterval(String repr) {
        return DateInterval.parseDateInterval(repr);
    }

    public Try<List<SortBy>> parseSortBy(String repr) {
        return trying(() -> {
            List<SortBy> objects = Stream.of(Option.of(repr).getOrElse("").split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .foldLeft(
                    List.empty(),
                    (acc, str) -> {
                        if (str.startsWith("-")) {
                            return acc.append(new SortBy(str.substring(1), DESC));
                        } else {
                            return acc.append(new SortBy(str.replaceFirst("^\\+", ""), ASC));
                        }
                    }
                );
            return objects;
        })
        .mapFailure(SORTBY_PARSING, () -> format("Failed to parse sort by: '%s'", repr));
    }

    public Try<Map<String, ItemSearchBody.QueryObject>> parseQuery(String query) {
        return Try.of(() -> gson.fromJson(query, new TypeToken<Map<String, ItemSearchBody.QueryObject>>() {
        }.getType()));
    }

    public Try<Fields> parseFields(String repr) {
        return trying(() ->
            Stream.of(Option.of(repr).getOrElse("").split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .foldLeft(
                    new Fields(List.empty(), List.empty()),
                    (acc, str) -> {
                        if (str.startsWith("-")) {
                            return acc.withExcludes(acc.getExcludes().append(str.substring(1)));
                        } else {
                            return acc.withIncludes(acc.getIncludes().append(str));
                        }
                    }
                )
        )
        .mapFailure(FIELDS_PARSING, () -> format("Failed to parse fields: '%s'", repr));
    }

    // @formatter:on
}
