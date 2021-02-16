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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody.SortBy.Direction.ASC;
import static fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody.SortBy.Direction.DESC;

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

    public Try<ItemSearchBody> parseItemSearch(
            Integer limit,
            BBox bbox,
            String datetime,
            List<String> collections,
            List<String> ids,
            String fields,
            String query,
            String sortBy
    ) {
        return parseDateInterval(datetime).flatMap(dt ->
            parseFields(fields).flatMap(f ->
                parseQuery(query).flatMap(q ->
                    parseSortBy(sortBy).map(s ->
                        new ItemSearchBody(
                            bbox, dt, null, collections, ids, limit, f, q, s
                        )
                    )
                )
            )
        );
    }

    private Try<DateInterval> parseDateInterval(String repr) {
        return DateInterval.parseDateInterval(repr);
    }

    public Try<List<ItemSearchBody.SortBy>> parseSortBy(String repr) {
        return Try.of(() ->
            Stream.of(Option.of(repr).getOrElse("").split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .foldLeft(
                    List.empty(),
                    (acc, str) -> {
                        if (str.startsWith("-")) {
                            return acc.append(new ItemSearchBody.SortBy(str.substring(1), DESC));
                        } else {
                            return acc.append(new ItemSearchBody.SortBy(str.replaceFirst("^\\+", ""), ASC));
                        }
                    }
                )
        );
    }

    public Try<Map<String, ItemSearchBody.QueryObject>> parseQuery(String query) {
        return Try.of(() -> gson.fromJson(query, new TypeToken<Map<String, ItemSearchBody.QueryObject>>() {
        }.getType()));
    }

    public Try<ItemSearchBody.Fields> parseFields(String repr) {
        return Try.of(() ->
            Stream.of(Option.of(repr).getOrElse("").split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .foldLeft(
                    new ItemSearchBody.Fields(List.empty(), List.empty()),
                    (acc, str) -> {
                        if (str.startsWith("-")) {
                            return acc.withExcludes(acc.getExcludes().append(str.substring(1)));
                        } else {
                            return acc.withIncludes(acc.getIncludes().append(str));
                        }
                    }
                )
        );
    }

    // @formatter:on
}
