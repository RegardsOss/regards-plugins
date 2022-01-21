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
package fr.cnes.regards.modules.catalog.stac.plugin.it.swot;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import fr.cnes.regards.framework.jsoniter.property.AttributeModelPropertyTypeFinder;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.dao.IPluginConfigurationRepository;
import fr.cnes.regards.framework.modules.tinyurl.dao.TinyUrlRepository;
import fr.cnes.regards.framework.test.integration.AbstractRegardsTransactionalIT;
import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.utils.RsRuntimeException;
import fr.cnes.regards.modules.accessrights.client.IProjectUsersClient;
import fr.cnes.regards.modules.dam.client.entities.IDatasetClient;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.Collection;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.Dataset;
import fr.cnes.regards.modules.feature.dto.Feature;
import fr.cnes.regards.modules.feature.dto.FeatureFile;
import fr.cnes.regards.modules.indexer.dao.IEsRepository;
import fr.cnes.regards.modules.indexer.domain.DataFile;
import fr.cnes.regards.modules.indexer.service.IIndexerService;
import fr.cnes.regards.modules.model.client.IAttributeModelClient;
import fr.cnes.regards.modules.model.client.IModelAttrAssocClient;
import fr.cnes.regards.modules.model.dao.IAttributeModelRepository;
import fr.cnes.regards.modules.model.dao.IModelAttrAssocRepository;
import fr.cnes.regards.modules.model.dao.IModelRepository;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.model.domain.ModelAttrAssoc;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import fr.cnes.regards.modules.model.domain.attributes.AttributeProperty;
import fr.cnes.regards.modules.model.domain.attributes.restriction.JsonSchemaRestriction;
import fr.cnes.regards.modules.model.domain.attributes.restriction.RestrictionType;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import fr.cnes.regards.modules.model.gson.AbstractAttributeHelper;
import fr.cnes.regards.modules.model.gson.MultitenantFlattenedAttributeAdapterFactory;
import fr.cnes.regards.modules.model.service.IAttributeModelService;
import fr.cnes.regards.modules.model.service.ModelService;
import fr.cnes.regards.modules.opensearch.service.cache.attributemodel.IAttributeFinder;
import fr.cnes.regards.modules.project.client.rest.IProjectsClient;
import fr.cnes.regards.modules.project.domain.Project;
import fr.cnes.regards.modules.search.dao.ISearchEngineConfRepository;
import fr.cnes.regards.modules.search.domain.plugin.SearchEngineMappings;
import fr.cnes.regards.modules.search.service.ISearchEngineConfigurationService;
import org.junit.Assert;
import org.junit.Before;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Engine common methods
 *
 * @author Marc Sordi
 */
@SuppressWarnings("deprecation")
//@DirtiesContext
public abstract class AbstractSwotIT extends AbstractRegardsTransactionalIT {

    /**
     * Data folders
     */
    private static final Path DATA_FOLDER = Paths.get("src", "test", "resources", "data");

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
    protected AttributeModelPropertyTypeFinder attributeModelPropertyTypeFinder;

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

    @Autowired
    private IAttributeModelRepository attributeModelRepository;

    @Autowired
    private IModelAttrAssocRepository modelAttrAssocRepository;

    @Autowired
    private IModelRepository modelRepository;

    @Autowired
    private IPluginConfigurationRepository pluginConfigurationRepository;

    @Autowired
    private ISearchEngineConfRepository searchEngineConfRepository;

    @Autowired
    private TinyUrlRepository tinyUrlRepository;

    protected void cleanDatabase() {
        modelAttrAssocRepository.deleteAllInBatch();
        modelRepository.deleteAll();
        attributeModelRepository.deleteAll();
        searchEngineConfRepository.deleteAllInBatch();
        pluginConfigurationRepository.deleteAllInBatch();
        tinyUrlRepository.deleteAllInBatch();
    }

    protected abstract String getDataFolderName();

    protected Path getDataFolder() {
        return DATA_FOLDER.resolve(getDataFolderName());
    }

    protected Path getConfigFolder() {
        return getDataFolder().resolve("config");
    }

    protected Path getDatasetFolder() {
        return getDataFolder().resolve("datasets");
    }

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

    protected abstract String getDataModel();

    protected abstract String getDatasetModel();

    @Before
    public void prepareData() throws ModuleException, InterruptedException, IOException {

        prepareProject();

        // - Import models
        // DATA : SWOT feature
        Model swotModel = modelService.importModel(Files.newInputStream(getConfigFolder().resolve(getDataModel())));
        // DATASET
        Model datasetModel = modelService
                .importModel(Files.newInputStream(getConfigFolder().resolve(getDatasetModel())));

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
            return ResponseEntity
                    .ok(modelService.getModelAttrAssocs(modelName).stream().map(EntityModel::new)
                                .collect(Collectors.toList()));
        });

        // - Refresh attribute factory (legacy and Jsoniter)
        List<AttributeModel> atts = attributeModelService.getAttributes(null, null, null);
        gsonAttributeFactory.refresh(getDefaultTenant(), atts);
        attributeModelPropertyTypeFinder.refresh(getDefaultTenant(), atts);

        // - Manage attribute cache
        List<EntityModel<AttributeModel>> resAtts = new ArrayList<>();
        atts.forEach(att -> resAtts.add(new EntityModel<>(att)));
        Mockito.when(attributeModelClientMock.getAttributes(null, null)).thenReturn(ResponseEntity.ok(resAtts));
        finder.refresh(getDefaultTenant());

        initPlugins();

        initIndex(getDefaultTenant());

        // Create SWOT datasets
        Map<String, Dataset> swotDatasets = createSwotDatasets(datasetModel);
        indexerService.saveBulkEntities(getDefaultTenant(), swotDatasets.values());

        // Create SWOT data
        indexerService.saveBulkEntities(getDefaultTenant(), createSWOTData(swotModel, swotDatasets));
        // Refresh index to be sure data is available for requesting
        indexerService.refresh(getDefaultTenant());

    }

    protected abstract void initPlugins() throws ModuleException;

    /**
     * Default implementation
     */
    protected void manageAccessRights() {
        // Bypass access rights
        Mockito.when(projectUserClientMock.isAdmin(Mockito.anyString())).thenReturn(ResponseEntity.ok(Boolean.TRUE));
    }

    /**
     * Return map of dataset with key indicating related data folder
     */
    private Map<String, Dataset> createSwotDatasets(Model datasetModel) throws IOException {
        return Files.list(getDatasetFolder()).filter(Files::isRegularFile)
                .map(p -> createSWOTDatasetFromFeature(datasetModel, p))
                .collect(Collectors.toMap(AbstractEntity::getLabel, Function.identity()));
    }

    private List<DataObject> createSWOTData(Model swotModel, Map<String, Dataset> swotDatasets) {
        List<DataObject> dataobjects = new ArrayList<>();
        try {
            for (Entry<String, Dataset> entry : swotDatasets.entrySet()) {
                dataobjects.addAll(Files.list(getDataFolder().resolve(entry.getKey())).filter(Files::isRegularFile)
                                           .map(p -> createDataObjectFromFeature(swotModel, p, entry.getValue()))
                                           .collect(Collectors.toList()));
            }
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }
        return dataobjects;
    }

    private Dataset createSWOTDatasetFromFeature(Model datasetModel, Path filepath) {
        // Load from file
        Feature feature = loadFromJson(filepath, Feature.class);
        // Create related dataset label!
        // Warning : label is used to identify dataset item folders
        if (!EntityType.DATASET.equals(feature.getEntityType())) {
            Assert.fail(String.format("Feature type must be of type %s", EntityType.DATASET));
        }
        String label = feature.getId();

        // Compute label to match directory
        Dataset dataset = createEntity(datasetModel, label);
        dataset.setGeometry(feature.getGeometry());
        feature.getProperties().stream().forEach(p -> dataset.addProperty(p));
        dataset.getFeature().setFiles(createFeatureFiles(feature.getFiles()));

        return dataset;
    }

    /**
     * Create a data object from source feature
     */
    private DataObject createDataObjectFromFeature(Model swotModel, Path filepath, Dataset dataset) {
        // Load from file
        Feature feature = loadFromJson(filepath, Feature.class);
        // Create related data object
        DataObject data = createEntity(swotModel, feature.getId());
        data.setGroups(getAccessGroups());
        data.setCreationDate(OffsetDateTime.now());
        data.setGeometry(feature.getGeometry());
        data.setWgs84(feature.getGeometry()); // As we bypass automatic normalization
        data.setProperties(feature.getProperties());
        data.getFeature().setFiles(createFeatureFiles(feature.getFiles()));
        // Link to dataset
        data.addTags("CNES", "SWOT", dataset.getIpId().toString());
        return data;
    }

    private Multimap<DataType, DataFile> createFeatureFiles(List<FeatureFile> featureFiles) {
        Multimap<DataType, DataFile> files = null;
        if (featureFiles != null) {
            files = ArrayListMultimap.create();
            // TODO
            for (FeatureFile ff : featureFiles) {
                DataFile dataFile = DataFile.build(ff.getAttributes().getDataType(), ff.getAttributes().getFilename(),
                                                   ff.getLocations().stream().findFirst().get().getUrl(),
                                                   ff.getAttributes().getMimeType(), true, false);
                dataFile.setFilesize(ff.getAttributes().getFilesize());
                dataFile.setCrc32(ff.getAttributes().getCrc32());
                dataFile.setChecksum(ff.getAttributes().getChecksum());
                dataFile.setTypes(Sets.newHashSet("Netcdf"));
                files.put(dataFile.getDataType(), dataFile);
            }
        }
        return files;
    }

    /**
     * Default implementation : no group on data object
     */
    protected Set<String> getAccessGroups() {
        return null;
    }

    /**
     * Load POJO from JSON file
     *
     * @param filepath path to the file
     * @param clazz    {@link Class}
     * @return {@link Feature}
     */
    protected <T> T loadFromJson(Path filepath, Class<T> clazz) {
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
    private <T> T createEntity(Model model, String label) {
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
