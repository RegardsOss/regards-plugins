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
package fr.cnes.regards.db.datasources.plugins.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RequestHelper {

    public static final String SELECT = "SELECT ";

    public static final String WHERE = " WHERE ";

    public static final String ORDER_BY = " ORDER BY";

    public static final String GROUP_BY = " GROUP BY ";

    public static final String SELECT_COUNT = "SELECT COUNT(*) ";

    public static final String LIMIT_CLAUSE = " ORDER BY %s LIMIT %d OFFSET %d";

    public static String mergeWhereClause(String request, String additionalWhereClause) {
        if (additionalWhereClause != null && !additionalWhereClause.isEmpty()) {
            Optional<String> firstWhereClause = getWhereClauseFromRequest(request);
            Optional<String> secondWhereClause = getWhereClauseFromRequest(additionalWhereClause);
            if (firstWhereClause.isPresent() && secondWhereClause.isPresent()) {
                return request.replace(firstWhereClause.get(),
                                       String.format("(%s) AND (%s)", firstWhereClause.get(), secondWhereClause.get()));
            } else if (firstWhereClause.isPresent()) {
                return request;
            } else if (secondWhereClause.isPresent()) {
                int requestInsertionIndex = getBeforeWhereClauseIndex(request);
                return String.format("%s%s%s%s",
                                     request.substring(0, requestInsertionIndex),
                                     WHERE,
                                     secondWhereClause.get(),
                                     request.substring(requestInsertionIndex));
            }
        }
        return request;
    }

    private static int getBeforeWhereClauseIndex(String request) {
        List<Integer> possibleEndIndex = new ArrayList<>();
        possibleEndIndex.add(request.toLowerCase().indexOf(ORDER_BY.toLowerCase()));
        possibleEndIndex.add(request.toLowerCase().indexOf(GROUP_BY.toLowerCase()));
        possibleEndIndex.add(request.length());
        return possibleEndIndex.stream().filter(i -> i > 0).min(Integer::compareTo).orElse(request.length());
    }

    public static Optional<String> getWhereClauseFromRequest(String request) {
        int whereIndex = request.toLowerCase().indexOf(WHERE.toLowerCase());
        if (whereIndex >= 0) {
            whereIndex = whereIndex + WHERE.length();
            List<Integer> possibleEndIndex = new ArrayList<>();
            possibleEndIndex.add(request.toLowerCase().indexOf(ORDER_BY.toLowerCase()));
            possibleEndIndex.add(request.toLowerCase().indexOf(GROUP_BY.toLowerCase()));
            possibleEndIndex.add(request.length());
            int lastIndex = possibleEndIndex.stream().filter(i -> i > 0).min(Integer::compareTo).orElse(0);
            return Optional.of(request.substring(whereIndex, lastIndex));
        }
        return Optional.empty();
    }

}
