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
package fr.cnes.regards.modules.catalog.services.plugins.utils;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Range;
import com.google.gson.JsonParser;
import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.indexer.domain.DataFile;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import fr.cnes.regards.modules.model.dto.properties.MarkdownURL;
import org.springframework.data.domain.PageRequest;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.*;

import static fr.cnes.regards.modules.catalog.services.plugins.utils.DataInitHelper.DataProperties.*;

/**
 * Helper to create {@link DataObject}s
 *
 * @author Iliana Ghazali
 **/
public class DataInitHelper {

    public static final int NB_LARGE_COLUMNS = 100;

    private static final Set<DataType> imgTypes = Set.of(DataType.QUICKLOOK_SD,
                                                         DataType.QUICKLOOK_MD,
                                                         DataType.QUICKLOOK_HD);

    private DataInitHelper() {
        // helper class
    }

    public static FacetPage<DataObject> simulatePageDataObjects(int nbDataObjects,
                                                                int pageIndex,
                                                                int nbEntitiesPerPage,
                                                                DataObjectTestType[] types) {
        int nbDataObjectsInPage;
        // simulate number of objects per page
        if (nbDataObjects > nbEntitiesPerPage) {
            if (pageIndex == nbDataObjects / nbEntitiesPerPage) {
                nbDataObjectsInPage = nbDataObjects % nbEntitiesPerPage;
            } else {
                nbDataObjectsInPage = nbEntitiesPerPage;
            }
        } else {
            nbDataObjectsInPage = nbDataObjects;
        }
        List<DataObject> dataObjects = createDataObjects(nbDataObjectsInPage, types);
        return new FacetPage<>(dataObjects, Set.of(), PageRequest.of(pageIndex, nbEntitiesPerPage), nbDataObjects);
    }

    public static List<DataObject> createDataObjects(int nbDataObjects, DataObjectTestType[] types) {
        List<DataObject> dataObjects = new ArrayList<>(nbDataObjects);
        for (int i = 0; i < nbDataObjects; i++) {
            dataObjects.add(createDataObject(i, types[i % types.length]));
        }
        return dataObjects;
    }

    public static DataObject createDataObject(int index, DataObjectTestType type) {
        DataObject dataObject = createBasicDataObject(index);
        dataObject.setProperties(switch (type) {
            case EARTH -> getEarthDynamicProperties();
            case MARS -> getMarsDynamicProperties();
            case MOON -> getMoonDynamicProperties();
            case LARGE -> getLargeDynamicProperties();
        });
        return dataObject;
    }

    private static DataObject createBasicDataObject(int index) {
        // Feature
        UniformResourceName urn = UniformResourceName.build("theidentifier",
                                                            EntityType.DATA,
                                                            "theTenant",
                                                            UUID.fromString("2023dc12-b609-4fe8-b31e-da7afd8de567"),
                                                            1,
                                                            1L,
                                                            "test");
        DataObjectFeature feature = new DataObjectFeature(urn, "providerId " + index, "label " + index);

        // Files
        Multimap<DataType, DataFile> files = LinkedListMultimap.create();
        files.put(DataType.RAWDATA, createDatafile(DataType.RAWDATA, index));
        files.put(DataType.QUICKLOOK_MD, createDatafile(DataType.QUICKLOOK_MD, index));
        feature.setFiles(files);
        feature.setTags(new LinkedHashSet<>(2) {{
            add("tag1");
            add("tag2");
        }});

        // Model
        Model model = Model.build("TheModel", "description", EntityType.DATA);

        return DataObject.wrap(model, feature, true);
    }

    private static DataFile createDatafile(DataType type, int index) {
        String fileExtension = imgTypes.contains(type) ? "png" : "raw";
        String filename = String.format("filename_%d.%s", index, fileExtension);
        long filesize = 10000;
        URI folderUri = Paths.get("src/test/resources/" + filename).toUri();
        DataFile dataFileRawData = DataFile.build(type, filename, folderUri, null, true, true);
        dataFileRawData.setFilesize(filesize);
        dataFileRawData.setChecksum("cf68eae574852d033a3607a02433de82");
        return dataFileRawData;
    }

    private static Set<IProperty<?>> getEarthDynamicProperties() {
        Set<IProperty<?>> properties = new LinkedHashSet<>(9);
        properties.add(IProperty.buildBoolean(STARTED, true));
        properties.add(IProperty.buildString(MISSION, "TerraeNovae"));
        properties.add(IProperty.buildDate(START_DATE, OffsetDateTime.parse("2022-12-22T12:50:20+01:00")));
        properties.add(IProperty.buildObject(CYCLE_RANGE,
                                             IProperty.buildInteger(CYCLE_MIN, 1),
                                             IProperty.buildInteger(CYCLE_MAX, 128)));
        properties.add(IProperty.buildLongArray(REF_CODES, 102L, 65124L));
        properties.add(IProperty.buildDoubleArray(RANGE, 125.2, 653.56));

        properties.add(IProperty.buildDateInterval(TIME_PERIOD,
                                                   Range.open(OffsetDateTime.parse("2335-12-22T13:30:30+05:00"),
                                                              OffsetDateTime.parse("2335-12-22T13:30:33+05:00"))));
        properties.add(IProperty.buildLong(LAST_MAJOR_VERSION, 1L));
        try {
            properties.add(IProperty.buildObject(HISTORY,
                                                 IProperty.buildBoolean(APPROVED, true),
                                                 IProperty.buildStringCollection(ENTITIES, List.of("CNES", "ESA")),
                                                 IProperty.buildDoubleCollection(VERSIONS, List.of(1.0, 2.0, 2.1)),
                                                 IProperty.buildUrl(DOCUMENT_LINK,
                                                                    new MarkdownURL("document",
                                                                                    new URL(
                                                                                        "https://cnes.fr/document.html")))));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return properties;
    }

    private static Set<IProperty<?>> getMarsDynamicProperties() {
        Set<IProperty<?>> properties = new LinkedHashSet<>(4);
        properties.add(IProperty.buildBoolean(STARTED, false));
        properties.add(IProperty.buildString(MISSION, "MarsNovae"));
        properties.add(IProperty.buildBoolean(STARTED, false));
        properties.add(IProperty.buildDate(START_DATE, OffsetDateTime.parse("2335-12-22T13:30:30+05:00")));
        return properties;
    }

    private static Set<IProperty<?>> getMoonDynamicProperties() {
        Set<IProperty<?>> properties = new LinkedHashSet<>(4);
        properties.add(IProperty.buildString(MISSION, "Artemis"));
        properties.add(IProperty.buildBoolean(STARTED, false));
        properties.add(IProperty.buildDate(START_DATE, OffsetDateTime.parse("2024-11-20T06:50:00+04:00")));
        properties.add(IProperty.buildJson(DESCRIPTION, JsonParser.parseString("""
                                                                                   {
                                                                                     "members": ["Reid Wiseman","Victor Glover", "Christina Koch","Jeremy Hansen"],
                                                                                     "commander": "Reid Wiseman",
                                                                                     "agency": {
                                                                                       "name": "NASA",
                                                                                       "missionType": "Crewed lunar flyby",
                                                                                       "missionDurationDays":10
                                                                                     }
                                                                                   }
                                                                                   """)));
        return properties;
    }

    private static Set<IProperty<?>> getLargeDynamicProperties() {
        Set<IProperty<?>> properties = new LinkedHashSet<>(NB_LARGE_COLUMNS + 1);
        for (int i = 0; i < NB_LARGE_COLUMNS + 1; i++) {
            properties.add(IProperty.buildInteger(String.valueOf(i), i));
        }
        return properties;
    }

    public enum DataObjectTestType {
        EARTH, MARS, MOON, LARGE
    }

    public static class DataProperties {

        public static final String STARTED = "started";

        public static final String MISSION = "mission";

        public static final String DESCRIPTION = "crew";

        public static final String START_DATE = "startDate";

        public static final String CYCLE_RANGE = "cycleRange";

        public static final String CYCLE_MIN = "cycleMin";

        public static final String CYCLE_MAX = "cycleMax";

        public static final String REF_CODES = "refCodes";

        public static final String RANGE = "range";

        public static final String TIME_PERIOD = "timePeriod";

        public static final String LAST_MAJOR_VERSION = "lastMajorVersion";

        public static final String VERSIONS = "versions";

        public static final String HISTORY = "history";

        public static final String DOCUMENT_LINK = "documentLink";

        public static final String APPROVED = "approved";

        public static final String ENTITIES = "entities";

        public static List<String> getAllPropertiesAsPaths() {
            return List.of(STARTED,
                           MISSION,
                           DESCRIPTION,
                           START_DATE,
                           CYCLE_RANGE + "." + CYCLE_MIN,
                           CYCLE_RANGE + "." + CYCLE_MAX,
                           REF_CODES,
                           RANGE,
                           TIME_PERIOD,
                           LAST_MAJOR_VERSION,
                           HISTORY + "." + APPROVED,
                           HISTORY + "." + VERSIONS,
                           HISTORY + "." + ENTITIES,
                           HISTORY + "." + DOCUMENT_LINK);

        }
    }
}
