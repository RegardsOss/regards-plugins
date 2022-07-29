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

import org.junit.Assert;
import org.junit.Test;

import java.util.Optional;

public class RequestHelperTest {


    @Test
    public void getWhereClauseFromRequestTest() {
        String expectedWhereClause = "titi.first= 'toto' and titi.second = 'titi'";
        String request = "select toto from titi where "+expectedWhereClause+ " order by titi.third";
        Optional<String> whereClause = RequestHelper.getWhereClauseFromRequest(request);
        Assert.assertTrue(whereClause.isPresent());
        Assert.assertEquals(expectedWhereClause, whereClause.get());

        request = "select toto from titi where "+expectedWhereClause;
        whereClause = RequestHelper.getWhereClauseFromRequest(request);
        Assert.assertTrue(whereClause.isPresent());
        Assert.assertEquals(expectedWhereClause, whereClause.get());

        request = "select toto from titi where "+expectedWhereClause + " group by titi.third;";
        whereClause = RequestHelper.getWhereClauseFromRequest(request);
        Assert.assertTrue(whereClause.isPresent());
        Assert.assertEquals(expectedWhereClause, whereClause.get());

        request = "select toto from titi where "+expectedWhereClause + " group by titi.third order by titi.third;";
        whereClause = RequestHelper.getWhereClauseFromRequest(request);
        Assert.assertTrue(whereClause.isPresent());
        Assert.assertEquals(expectedWhereClause, whereClause.get());

        request = "select toto from titi";
        whereClause = RequestHelper.getWhereClauseFromRequest(request);
        Assert.assertFalse(whereClause.isPresent());
    }

    @Test
    public void mergeWhereRequestTest() {
        String additionalWhereWlause = "date < '2020T0101'";
        String additionalMergeClause = " WHERE " + additionalWhereWlause;
        String firstWhereClause = "titi.first= 'toto' and titi.second = 'titi'";
        String expectedWhereClause = String.format("(%s) AND (%s)",firstWhereClause, additionalWhereWlause);
        String requestTemplate = "select toto from titi where %s order by titi.third";
        String request = String.format(requestTemplate, firstWhereClause);
        String mergedRequest = RequestHelper.mergeWhereClause(request, additionalMergeClause);

        Assert.assertTrue(mergedRequest != null);
        Assert.assertEquals(String.format(requestTemplate, expectedWhereClause), mergedRequest);

        mergedRequest = RequestHelper.mergeWhereClause(String.format(requestTemplate, firstWhereClause), null);
        Assert.assertTrue(mergedRequest != null);
        Assert.assertEquals(request, mergedRequest);

        request = "select toto from titi order by toto;";
        mergedRequest = RequestHelper.mergeWhereClause(request, null);
        Assert.assertTrue(mergedRequest != null);
        Assert.assertEquals(request, mergedRequest);

        mergedRequest = RequestHelper.mergeWhereClause(request, additionalMergeClause);
        Assert.assertTrue(mergedRequest != null);
        Assert.assertEquals("select toto from titi WHERE date < '2020T0101' order by toto;", mergedRequest);

        request = "select toto from titi";
        mergedRequest = RequestHelper.mergeWhereClause(request, additionalMergeClause);
        Assert.assertTrue(mergedRequest != null);
        Assert.assertEquals("select toto from titi WHERE date < '2020T0101'", mergedRequest);


    }


}
