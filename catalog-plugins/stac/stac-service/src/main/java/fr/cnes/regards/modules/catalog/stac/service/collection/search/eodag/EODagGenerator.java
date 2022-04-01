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
package fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag;

import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import org.springframework.data.util.Pair;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * Helper class for generating EODag scripts
 */
public class EODagGenerator {

    /**
     * Collection identifier : URN
     */
    private static final String EODAG_PRODUCT_TYPE_PARAM = "productType";

    /**
     * Start period : ISO8601 date
     */
    private static final String EODAG_START_PARAM = "start";

    /**
     * End period : ISO8601 date
     */
    private static final String EODAG_END_PARAM = "end";

    /**
     * Geometry as WKT
     */
    private static final String EODAG_GEOM_PARAM = "geom";

    // TODO query extension

    private static final String TAB_AS_SPACES = "    ";

    private static final String SINGLE_PARAMETER_FORMAT = TAB_AS_SPACES + "%s=\"%s\",\n";

    private static final String FIRST_DICT_PARAMETER_FORMAT = "\"%s\": \"%s\"";

    private static final String DICT_PARAMETER_FORMAT = ", " + FIRST_DICT_PARAMETER_FORMAT;

    private static final String INTEGER_DICT_PARAMETER_FORMAT = ", " + "\"%s\": %d";

    private static final String DOUBLE_DICT_PARAMETER_FORMAT = ", " + "\"%s\": %f";

    private static final String BOOlEAN_DICT_PARAMETER_FORMAT = ", " + "\"%s\": %s";

    public static void generateSingleCollectionScript(PrintWriter writer, EODagParameters parameters) {
        writer.println("from eodag import EODataAccessGateway");
        writer.println();
        writer.println("dag = EODataAccessGateway()");
        writer.println("search_results = dag.search_all(");
        writer.printf(SINGLE_PARAMETER_FORMAT, EODAG_PRODUCT_TYPE_PARAM, parameters.getProductType());
        if (parameters.getStart().isPresent()) {
            writer.printf(SINGLE_PARAMETER_FORMAT, EODAG_START_PARAM, parameters.getStart().get());
        }
        if (parameters.getEnd().isPresent()) {
            writer.printf(SINGLE_PARAMETER_FORMAT, EODAG_END_PARAM, parameters.getEnd().get());
        }
        if (parameters.getGeom().isPresent()) {
            writer.printf(SINGLE_PARAMETER_FORMAT, EODAG_GEOM_PARAM, parameters.getGeom().get());
        }
        // Disable extras : incompatible parameter name
        //        if (parameters.hasExtras()) {
        //            for (Map.Entry<String, Object> kvp : parameters.getExtras().entrySet()) {
        //                writer.printf(SINGLE_PARAMETER_FORMAT, kvp.getKey(), kvp.getValue());
        //            }
        //        }
        writer.println(")");
        writer.println("downloaded_paths = dag.download_all(search_results)");
    }

    public static void generateMultiCollectionScript(PrintWriter writer, List<EODagParameters> collectionParameters) {
        writer.println("from eodag import EODataAccessGateway, SearchResult");
        writer.println();
        writer.println("dag = EODataAccessGateway()");
        writer.println();
        writer.println("project_query_args = [");
        for (EODagParameters parameters : collectionParameters) {
            writer.printf("%s{", TAB_AS_SPACES);
            writer.printf(FIRST_DICT_PARAMETER_FORMAT, EODAG_PRODUCT_TYPE_PARAM, parameters.getProductType());
            if (parameters.getStart().isPresent()) {
                writer.printf(DICT_PARAMETER_FORMAT, EODAG_START_PARAM, parameters.getStart().get());
            }
            if (parameters.getEnd().isPresent()) {
                writer.printf(DICT_PARAMETER_FORMAT, EODAG_END_PARAM, parameters.getEnd().get());
            }
            if (parameters.getGeom().isPresent()) {
                writer.printf(DICT_PARAMETER_FORMAT, EODAG_GEOM_PARAM, parameters.getGeom().get());
            }
            if (parameters.hasExtras()) {
                for (Map.Entry<String, Pair<PropertyType, Object>> kvp : parameters.getExtras().entrySet()) {
                    switch (kvp.getValue().getFirst()) {
                        case INTEGER:
                        case INTEGER_ARRAY:
                        case INTEGER_INTERVAL:
                            writer.printf(INTEGER_DICT_PARAMETER_FORMAT, kvp.getKey(), ((Double) kvp.getValue().getSecond()).intValue());
                            break;
                        case LONG:
                        case LONG_ARRAY:
                        case LONG_INTERVAL:
                            writer.printf(INTEGER_DICT_PARAMETER_FORMAT, kvp.getKey(), ((Double) kvp.getValue().getSecond()).longValue());
                            break;
                        case DOUBLE:
                        case DOUBLE_ARRAY:
                        case DATE_INTERVAL:
                            writer.printf(DOUBLE_DICT_PARAMETER_FORMAT, kvp.getKey(), kvp.getValue().getSecond());
                            break;
                        case BOOLEAN:
                            writer.printf(BOOlEAN_DICT_PARAMETER_FORMAT, kvp.getKey(), (Boolean) kvp.getValue().getSecond() ? "True" : "False");
                            break;
                        default:
                            writer.printf(DICT_PARAMETER_FORMAT, kvp.getKey(), kvp.getValue().getSecond());
                    }
                }
            }
            writer.println("},");
        }
        writer.println("]");
        writer.println("project_search_results = SearchResult([])");
        writer.println();
        writer.println("for query_args in project_query_args:");
        writer.printf("%sproject_search_results.extend(\n", TAB_AS_SPACES);
        writer.printf("%s%sdag.search_all(**query_args)\n", TAB_AS_SPACES, TAB_AS_SPACES);
        writer.printf("%s)\n", TAB_AS_SPACES);
        writer.println();
        writer.println("downloaded_paths = dag.download_all(project_search_results)");
    }
}
