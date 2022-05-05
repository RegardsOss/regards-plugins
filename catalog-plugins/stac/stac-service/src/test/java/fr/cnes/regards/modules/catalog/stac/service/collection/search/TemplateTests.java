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
package fr.cnes.regards.modules.catalog.stac.service.collection.search;

import fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag.EODagGenerator;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag.EODagInformation;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag.EODagParameters;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class TemplateTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateTests.class);

    private static EODagInformation info() {
        EODagInformation information = new EODagInformation();
        information.setProjectName("project_name");
        information.setProvider("test_provider");
        information.setStacSearchApi("http://search.api/api/stac/...");
        information.setBaseUri("http://search.api");
        information.setPortalName("portal name");
        return information;
    }

    private static EODagParameters parameters0() {
        EODagParameters parameters = new EODagParameters("productType0");
        parameters.setStart("2020-05-01");
        parameters.setEnd("2020-05-10");
        parameters.setGeom("POLYGON ((1 43, 2 43, 2 44, 1 44, 1 43))");
        return parameters;
    }

    private static EODagParameters parameters1() {
        EODagParameters parameters = new EODagParameters("productType1");
        parameters.setStart("2020-05-01");
        parameters.setEnd("2020-05-10");
        parameters.addExtras("foo", PropertyType.STRING, "bar");
        parameters.addExtras("integer", PropertyType.INTEGER, 1);
        parameters.addExtras("long", PropertyType.LONG, 1L);
        parameters.addExtras("double", PropertyType.DOUBLE, 1D);
        return parameters;
    }

    private static EODagParameters parameters2() {
        EODagParameters parameters = new EODagParameters("productType2");
        parameters.setStart("2020-05-01");
        parameters.setEnd("2020-05-10");
        return parameters;
    }

    private static EODagParameters parameters3() {
        EODagParameters parameters = new EODagParameters("productType3");
        parameters.setGeom("POLYGON ((1 43, 2 43, 2 44, 1 44, 1 43))");
        return parameters;
    }

    @Test
    public void generateSingle() {
        StringWriter sw = new StringWriter();
        try (PrintWriter writer = new PrintWriter(sw)) {
            EODagGenerator.generateFromTemplate(writer, info(), parameters0());
        }
        LOGGER.info("JAVA generated : {}", sw);
    }

    @Test
    public void generateSingleFromTemplate() {
        StringWriter sw = new StringWriter();
        try (PrintWriter writer = new PrintWriter(sw)) {
            EODagGenerator.generateFromTemplate(writer, info(), parameters1());
        }
        LOGGER.info("JAVA generated : {}", sw);
    }

    @Test
    public void generateMulti() {
        List<EODagParameters> collectionParameters = new ArrayList<>();
        collectionParameters.add(parameters1());
        collectionParameters.add(parameters2());
        collectionParameters.add(parameters3());

        StringWriter sw = new StringWriter();
        try (PrintWriter writer = new PrintWriter(sw)) {
            EODagGenerator.generateFromTemplate(writer, info(), collectionParameters);
        }
        LOGGER.info("JAVA generated : {}", sw);
    }
}
