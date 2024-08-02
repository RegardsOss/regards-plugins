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
package fr.cnes.regards.modules.dataprovider.plugins.product;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Binda Sébastien
 */
public class ProductNameFromFilePatternPluginTest {

    @Test
    public void test() throws ModuleException {
        Path file = Paths.get("/test/file/aaa_bbb_cccc_dddd.tar");
        String pattern = "(.*_)(.*_)(.*_)(.*)(.tar)";

        Assert.assertEquals("aaa_cccc_dddd", RegexpHelper.removeGroups(file, pattern, "5,2"));
        Assert.assertEquals("aaa_bbb_cccc_dddd", RegexpHelper.removeGroups(file, pattern, "5"));
        Assert.assertEquals(".tar", RegexpHelper.removeGroups(file, pattern, "2,3,1,4"));
    }

}
