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
package fr.cnes.regards.modules.catalog.stac.plugin.it.swot;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Before;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMethod;

import com.google.gson.Gson;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.test.integration.AbstractRegardsTransactionalIT;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.accessrights.client.IProjectUsersClient;
import fr.cnes.regards.modules.dam.client.entities.IDatasetClient;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.Collection;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.Dataset;
import fr.cnes.regards.modules.feature.dto.Feature;
import fr.cnes.regards.modules.indexer.dao.IEsRepository;
import fr.cnes.regards.modules.indexer.service.IIndexerService;
import fr.cnes.regards.modules.model.client.IAttributeModelClient;
import fr.cnes.regards.modules.model.client.IModelAttrAssocClient;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.model.domain.ModelAttrAssoc;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import fr.cnes.regards.modules.model.gson.MultitenantFlattenedAttributeAdapterFactory;
import fr.cnes.regards.modules.model.service.IAttributeModelService;
import fr.cnes.regards.modules.model.service.ModelService;
import fr.cnes.regards.modules.opensearch.service.cache.attributemodel.IAttributeFinder;
import fr.cnes.regards.modules.project.client.rest.IProjectsClient;
import fr.cnes.regards.modules.project.domain.Project;
import fr.cnes.regards.modules.search.domain.plugin.SearchEngineConfiguration;
import fr.cnes.regards.modules.search.domain.plugin.SearchEngineMappings;
import fr.cnes.regards.modules.search.service.ISearchEngineConfigurationService;

/**
 * Engine common methods
 * @author Marc Sordi
 */
@SuppressWarnings("deprecation")
//@DirtiesContext
public abstract class AbstractSwotIT extends AbstractRegardsTransactionalIT {

    // FIXME clean
    //    /**
    //     * Common metadata
    //     */
    //    protected static final String ATT_DATETIME = "datetime";
    //
    //    protected static final String ATT_START_DATETIME = "start_datetime";
    //
    //    protected static final String ATT_END_DATETIME = "end_datetime";
    //
    //    protected static final String ATT_PROVIDER = "provider";
    //
    //    protected static final String ATT_PROVIDER_NAME = "name";
    //
    //    protected static final String ATT_PLATFORM = "platform";
    //
    //    protected static final String ATT_INSTRUMENT = "instrument";
    //
    //    protected static final String ATT_MISSION = "mission";
    //
    //    /**
    //     * Version extension
    //     */
    //    protected static final String ATT_VERSION = "version";
    //
    //    /**
    //     * Hydrology extension
    //     */
    //    protected static final String ATT_HYDRO = "hydro";
    //
    //    protected static final String ATT_HYDRO_DATA_TYPE = "data_type";
    //
    //    protected static final String ATT_HYDRO_variables = "variables";
    //
    //    protected static final String ATT_HYDRO_categories = "categories";
    //
    //    /**
    //     * Spatial extension
    //     */
    //    protected static final String ATT_SPATIAL = "spatial";
    //
    //    protected static final String ATT_SPATIAL_CYCLE_ID = "cycle_id";
    //
    //    protected static final String ATT_SPATIAL_CRIT = "crid";
    //
    //    protected static final String ATT_SPATIAL_PASS_ID = "pass_id";
    //
    //    protected static final String ATT_SPATIAL_TILE_ID = "tile_id";
    //
    //    protected static final String ATT_SPATIAL_TILE_SIDE = "tile_side";
    //
    //    protected static final String ATT_SPATIAL_BASSIN_ID = "bassin_id";
    //
    //    protected static final String ATT_SPATIAL_CONTINENT_ID = "continent_id";
    //
    //    protected static final String ATT_SCENE_ID = "scene_id";
    //
    //    /**
    //     * Data characteristics extension
    //     */
    //    protected static final String ATT_DCS = "dcs";
    //
    //    protected static final String ATT_DCS_ORIGIN = "origin";
    //
    //    protected static final String ATT_DCS_TECHNO_ID = "techno_id";
    //
    //    protected static final String ATT_DCS_REF_CATALOG = "reference_catalog";

    /**
     * Data folders
     */
    private static final Path DATA_FOLDER = Paths.get("src", "test", "resources", "data");

    private static final Path DATA_FOLDER_FOR_SWOT = DATA_FOLDER.resolve("swot");

    private static final Path DATA_FOLDER_FOR_SWOT_CONFIG = DATA_FOLDER_FOR_SWOT.resolve("config");

    @Autowired
    protected ModelService modelService;

    @Autowired
    protected IEsRepository esRepository;

    @Autowired
    protected IAttributeModelService attributeModelService;

    @Autowired
    protected IProjectUsersClient projectUserClientMock;

    @Autowired
    protected IAttributeModelClient attributeModelClientMock;

    @Autowired
    protected IAttributeFinder finder;

    @Autowired
    protected IProjectsClient projectsClientMock;

    @Autowired
    protected IModelAttrAssocClient modelAttrAssocClientMock;

    @Autowired
    protected MultitenantFlattenedAttributeAdapterFactory gsonAttributeFactory;

    @Autowired
    protected IIndexerService indexerService;

    @Autowired
    protected ISearchEngineConfigurationService searchEngineService;

    @Autowired
    protected IDatasetClient datasetClientMock;

    @Autowired
    protected Gson gson;

    // FIXME
    //    private static class Marker {
    //
    //        private boolean prepareAll;
    //
    //        // If prepareAll is true, index population can be disabled
    //        private boolean butIndex;
    //
    //        public Marker(boolean prepareAll, boolean butIndex) {
    //            this.prepareAll = prepareAll;
    //            this.butIndex = butIndex;
    //        }
    //
    //        /**
    //         * @return the prepareAll
    //         */
    //        public boolean isPrepareAll() {
    //            return prepareAll;
    //        }
    //
    //        /**
    //         * @param prepareAll the prepareAll to set
    //         */
    //        public void setPrepareAll(boolean prepareAll) {
    //            this.prepareAll = prepareAll;
    //        }
    //
    //        /**
    //         * @return the butIndex
    //         */
    //        public boolean isButIndex() {
    //            return butIndex;
    //        }
    //
    //        /**
    //         * @param butIndex the butIndex to set
    //         */
    //        public void setButIndex(boolean butIndex) {
    //            this.butIndex = butIndex;
    //        }
    //
    //    }
    //
    //    private static final Marker DEFAULT_MARKER = new Marker(true, true);

    //    /**
    //     * Override this method to change preparation behavior
    //     */
    //    protected Marker getMarker() {
    //        return DEFAULT_MARKER;
    //    }

    protected void initIndex(String index) {
        if (esRepository.indexExists(index)) {
            esRepository.deleteIndex(index);
        }
        esRepository.createIndex(index);
    }

    protected void prepareProject() {

        // Needed for test on date in opensearch descriptors. Date are generated in test and compare with date generated
        // by elasticsearch on test server.
        // Test server is in UTC timezone, so to do comparasion we have to be in the same timezone.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        // Manage project
        Project project = new Project(1L, "SWOT project", "http://plop/icon.png", true, "SWOT");
        project.setHost("http://regards/swot");
        ResponseEntity<EntityModel<Project>> response = ResponseEntity.ok(new EntityModel<>(project));
        Mockito.when(projectsClientMock.retrieveProject(Mockito.anyString())).thenReturn(response);

        // Bypass method access rights
        List<String> relativeUrlPaths = new ArrayList<>();

        // Authorities for engine generic endpoints
        relativeUrlPaths.add(SearchEngineMappings.SEARCH_ALL_MAPPING);
        relativeUrlPaths.add(SearchEngineMappings.SEARCH_ALL_MAPPING_EXTRA);
        relativeUrlPaths.add(SearchEngineMappings.GET_ENTITY_MAPPING);

        relativeUrlPaths.add(SearchEngineMappings.SEARCH_COLLECTIONS_MAPPING);
        relativeUrlPaths.add(SearchEngineMappings.SEARCH_COLLECTIONS_MAPPING_EXTRA);
        relativeUrlPaths.add(SearchEngineMappings.GET_COLLECTION_MAPPING);

        relativeUrlPaths.add(SearchEngineMappings.SEARCH_DATASETS_MAPPING);
        relativeUrlPaths.add(SearchEngineMappings.SEARCH_DATASETS_MAPPING_EXTRA);
        relativeUrlPaths.add(SearchEngineMappings.GET_DATASET_MAPPING);
        relativeUrlPaths.add(SearchEngineMappings.GET_DATASET_DESCRIPTION_MAPPING);

        relativeUrlPaths.add(SearchEngineMappings.SEARCH_DATAOBJECTS_MAPPING);
        relativeUrlPaths.add(SearchEngineMappings.SEARCH_DATAOBJECTS_MAPPING_EXTRA);
        relativeUrlPaths.add(SearchEngineMappings.GET_DATAOBJECT_MAPPING);

        relativeUrlPaths.add(SearchEngineMappings.SEARCH_DATASET_DATAOBJECTS_MAPPING);
        relativeUrlPaths.add(SearchEngineMappings.SEARCH_DATASET_DATAOBJECTS_MAPPING_EXTRA);

        relativeUrlPaths.add(SearchEngineMappings.SEARCH_DATAOBJECTS_DATASETS_MAPPING);

        // TODO : authorities for STAC endpoints

        for (String relativeUrlPath : relativeUrlPaths) {
            setAuthorities(SearchEngineMappings.TYPE_MAPPING + relativeUrlPath, RequestMethod.GET, getDefaultRole());
        }

        manageAccessRights();
    }

    @Before
    public void prepareData() throws ModuleException, InterruptedException {

        // FIXME
        //        if (!getMarker().isPrepareAll()) {
        //            return;
        //        } else {
        //
        //            getMarker().setPrepareAll(false);
        //        }

        prepareProject();

        // - Import models
        // DATA : SWOT feature
        Model swotModel = modelService.importModel(this.getClass().getResourceAsStream("model_hygor_V0.1.0.xml"));

        // - Manage attribute model retrieval
        Mockito.when(modelAttrAssocClientMock.getModelAttrAssocsFor(Mockito.any())).thenAnswer(invocation -> {
            EntityType type = invocation.getArgument(0);
            return ResponseEntity.ok(modelService.getModelAttrAssocsFor(type));
        });
        Mockito.when(datasetClientMock.getModelAttrAssocsForDataInDataset(Mockito.any())).thenAnswer(invocation -> {
            // UniformResourceName datasetUrn = invocation.getArgumentAt(0, UniformResourceName.class);
            return ResponseEntity.ok(modelService.getModelAttrAssocsFor(EntityType.DATA));
        });
        Mockito.when(modelAttrAssocClientMock.getModelAttrAssocs(Mockito.any())).thenAnswer(invocation -> {
            String modelName = invocation.getArgument(0);
            return ResponseEntity.ok(modelService.getModelAttrAssocs(modelName).stream()
                    .map(a -> new EntityModel<ModelAttrAssoc>(a)).collect(Collectors.toList()));
        });

        // - Refresh attribute factory
        List<AttributeModel> atts = attributeModelService.getAttributes(null, null, null);
        gsonAttributeFactory.refresh(getDefaultTenant(), atts);

        // - Manage attribute cache
        List<EntityModel<AttributeModel>> resAtts = new ArrayList<>();
        atts.forEach(att -> resAtts.add(new EntityModel<AttributeModel>(att)));
        Mockito.when(attributeModelClientMock.getAttributes(null, null)).thenReturn(ResponseEntity.ok(resAtts));
        finder.refresh(getDefaultTenant());

        initPlugins();

        // FIXME
        //        if (!getMarker().isButIndex()) {
        //            return;
        //        }
        //
        //        // FIXME
        //        getMarker().setButIndex(false);
        initIndex(getDefaultTenant());
        // Create SWOT data
        indexerService.saveBulkEntities(getDefaultTenant(), createSWOTData(swotModel));
        // Refresh index to be sure data is available for requesting
        indexerService.refresh(getDefaultTenant());

    }

    protected void initPlugins() throws ModuleException {
        SearchEngineConfiguration conf = loadFromJson(DATA_FOLDER_FOR_SWOT_CONFIG.resolve("STAC-engine-configuration.json"),
                                                      SearchEngineConfiguration.class);
        searchEngineService.createConf(conf);
    }

    /**
     * Default implementation
     */
    protected void manageAccessRights() {
        // Bypass access rights
        Mockito.when(projectUserClientMock.isAdmin(Mockito.anyString())).thenReturn(ResponseEntity.ok(Boolean.TRUE));
    }

    private List<DataObject> createSWOTData(Model swotModel) {
        try {
            return Files.list(DATA_FOLDER_FOR_SWOT).filter(Files::isRegularFile)
                    .map(p -> createDataObjectFromFeature(swotModel, p)).collect(Collectors.toList());
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }
        return null;
    }

    /**
     * Create a data object from source feature
     * @param swotModel
     * @param filename
     * @return {@link DataObject}
     */
    private DataObject createDataObjectFromFeature(Model swotModel, Path filepath) {
        // Load from file
        Feature feature = loadFromJson(filepath, Feature.class);
        // Create related data object
        DataObject data = createEntity(swotModel, feature.getId());
        data.setGroups(getAccessGroups());
        data.setCreationDate(OffsetDateTime.now());
        data.setGeometry(feature.getGeometry());
        data.setProperties(feature.getProperties());
        // TODO : maybe add files!
        return data;
    }

    /**
     * Default implementation : no group on data object
     */
    protected Set<String> getAccessGroups() {
        return null;
    }

    /**
     * Load POJO from JSON file
     * @param filepath path to the file
     * @param target {@link Class}
     * @return {@link Feature}
     */
    private <T> T loadFromJson(Path filepath, Class<T> clazz) {
        try (Reader reader = Files.newBufferedReader(filepath)) {
            return gson.fromJson(reader, clazz);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
        return null;
    }

    /**
     * Initialize an entity
     */
    @SuppressWarnings("unchecked")
    protected <T> T createEntity(Model model, String label) {
        AbstractEntity<?> entity;
        switch (model.getType()) {
            case COLLECTION:
                entity = new Collection(model, getDefaultTenant(), label, label);
                break;
            case DATA:
                entity = new DataObject(model, getDefaultTenant(), label, label);
                break;
            case DATASET:
                entity = new Dataset(model, getDefaultTenant(), label, label);
                break;
            default:
                throw new UnsupportedOperationException("Unknown entity type " + model.getType());
        }
        return (T) entity;
    }

    /**
     * Enclose string in quotes
     */
    protected String protect(String value) {
        String protect = "\"";
        if (value.startsWith(protect)) {
            return value;
        }
        return String.format("%s%s%s", protect, value, protect);
    }
}
