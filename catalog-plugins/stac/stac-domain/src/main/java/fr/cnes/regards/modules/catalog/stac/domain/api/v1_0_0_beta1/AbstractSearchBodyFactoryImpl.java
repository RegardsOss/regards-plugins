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
package fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.apache.commons.lang3.StringUtils;

import static fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody.SortBy.Direction.ASC;
import static fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody.SortBy.Direction.DESC;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.FIELDS_PARSING;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.SORTBY_PARSING;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;
import static java.lang.String.format;

public class AbstractSearchBodyFactoryImpl {

    private final Gson gson;

    public AbstractSearchBodyFactoryImpl(Gson gson) {
        this.gson = gson;
    }

    protected Try<Option<DateInterval>> parseDateInterval(String repr) {
        return DateInterval.parseDateInterval(repr);
    }

    public Try<List<SearchBody.SortBy>> parseSortBy(String repr) {
        return trying(() -> {
            return Stream.of(Option.of(repr).getOrElse("").split(","))
                         .map(String::trim)
                         .filter(StringUtils::isNotBlank)
                         .<List<SearchBody.SortBy>>foldLeft(List.empty(), (acc, str) -> {
                             if (str.startsWith("-")) {
                                 return acc.append(new SearchBody.SortBy(str.substring(1), DESC));
                             } else {
                                 return acc.append(new SearchBody.SortBy(str.replaceFirst("^\\+", ""), ASC));
                             }
                         });
        }).mapFailure(SORTBY_PARSING, () -> format("Failed to parse sort by: '%s'", repr));
    }

    public Try<Map<String, SearchBody.QueryObject>> parseQuery(String query) {
        return Try.of(() -> gson.fromJson(query, new TypeToken<Map<String, SearchBody.QueryObject>>() {

        }.getType()));
    }

    public Try<SearchBody.Fields> parseFields(String repr) {
        return trying(() -> Stream.of(Option.of(repr).getOrElse("").split(","))
                                  .map(String::trim)
                                  .filter(StringUtils::isNotBlank)
                                  .foldLeft(new SearchBody.Fields(List.empty(), List.empty()), (acc, str) -> {
                                      if (str.startsWith("-")) {
                                          return acc.withExcludes(acc.getExcludes().append(str.substring(1)));
                                      } else {
                                          return acc.withIncludes(acc.getIncludes().append(str));
                                      }
                                  })).mapFailure(FIELDS_PARSING, () -> format("Failed to parse fields: '%s'", repr));
    }
}
