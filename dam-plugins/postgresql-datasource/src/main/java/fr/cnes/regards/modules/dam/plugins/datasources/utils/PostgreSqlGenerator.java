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

package fr.cnes.regards.modules.dam.plugins.datasources.utils;

import com.nurkiewicz.jdbcrepository.sql.SqlGenerator;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * @author Christophe Mertz
 */
public class PostgreSqlGenerator extends SqlGenerator {

    public PostgreSqlGenerator(String allColumnsClause) {
        super(allColumnsClause);
    }

    public PostgreSqlGenerator() {
        super("*");
    }

    @Override
    protected String limitClause(Pageable page) {
        int offset = page.getPageNumber() * page.getPageSize();
        // ORDER BY is managed by SqlGenerator.selectAll(..)
        return String.format(" LIMIT %d OFFSET %d", page.getPageSize(), offset);
    }

    @Override
    protected String sortingClauseIfRequired(Sort sort) {
        if ((sort == null) || sort.isUnsorted()) {
            return "";
        }
        return super.sortingClauseIfRequired(sort);
    }

}