package fr.cnes.regards.modules.dam.plugins.datasources;

import com.google.common.collect.Sets;
import fr.cnes.regards.framework.oais.dto.ContentInformationDto;
import fr.cnes.regards.framework.oais.dto.OAISDataObjectLocationDto;
import fr.cnes.regards.framework.oais.dto.aip.AIPDto;
import fr.cnes.regards.framework.oais.dto.sip.SIPDto;
import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.accessrights.client.IProjectUsersClient;
import fr.cnes.regards.modules.fileaccess.dto.StorageType;
import fr.cnes.regards.modules.filecatalog.dto.StorageLocationDto;
import fr.cnes.regards.modules.ingest.client.IAIPRestClient;
import fr.cnes.regards.modules.ingest.domain.aip.AIPEntity;
import fr.cnes.regards.modules.ingest.domain.sip.IngestMetadata;
import fr.cnes.regards.modules.ingest.domain.sip.SIPEntity;
import fr.cnes.regards.modules.ingest.dto.SIPState;
import fr.cnes.regards.modules.ingest.dto.aip.SearchAIPsParameters;
import fr.cnes.regards.modules.ingest.dto.aip.StorageMetadata;
import fr.cnes.regards.modules.model.client.IAttributeModelClient;
import fr.cnes.regards.modules.project.client.rest.IProjectsClient;
import fr.cnes.regards.modules.project.domain.Project;
import fr.cnes.regards.modules.storage.client.IStorageLocationRestClient;
import fr.cnes.regards.modules.storage.domain.database.StorageLocationConfiguration;
import fr.cnes.regards.modules.toponyms.client.IToponymsClient;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Configuration
public class AipDataSourcePluginTestConfiguration {

    @Bean
    public IAttributeModelClient modelClient() {
        return Mockito.mock(IAttributeModelClient.class);
    }

    @Bean
    public IProjectUsersClient userClientMock() {
        return Mockito.mock(IProjectUsersClient.class);
    }

    @Bean
    public IToponymsClient toponymsClient() {
        return Mockito.mock(IToponymsClient.class);
    }

    @Bean
    public IProjectsClient projectsClientMock() {
        IProjectsClient client = Mockito.mock(IProjectsClient.class);
        Project project = new Project("desc", null, true, "test-project");
        project.setHost("http://test.com");
        EntityModel<Project> resource = EntityModel.of(project);
        ResponseEntity<EntityModel<Project>> response = new ResponseEntity<EntityModel<Project>>(resource,
                                                                                                 HttpStatus.OK);
        Mockito.when(client.retrieveProject(Mockito.anyString())).thenReturn(response);
        return client;
    }

    @Bean
    public IAIPRestClient aipClient() {
        AipClientProxy aipClientProxy = new AipClientProxy();
        InvocationHandler handler = (proxy, method, args) -> {
            for (Method aipClientProxyMethod : aipClientProxy.getClass().getMethods()) {
                if (aipClientProxyMethod.getName().equals(method.getName())) {
                    return aipClientProxyMethod.invoke(aipClientProxy, args);
                }
            }
            return null;
        };
        return (IAIPRestClient) Proxy.newProxyInstance(IAIPRestClient.class.getClassLoader(),
                                                       new Class<?>[] { IAIPRestClient.class },
                                                       handler);
    }

    private class AipClientProxy {

        @SuppressWarnings("unused")
        public ResponseEntity<PagedModel<EntityModel<AIPEntity>>> searchAIPs(SearchAIPsParameters filters,
                                                                             int page,
                                                                             int size,
                                                                             Sort sort) {
            Objects.requireNonNull(filters.getAipStates(), "List of states for AIP must not be null");

            List<AIPEntity> aipEntities = new ArrayList<>();

            for (AIPDto aip : AipDataSourcePluginTest.createAIPs(1, "tag1", "tag2", "session 1")) {
                aip.withDataObject(DataType.RAWDATA,
                                   "Name",
                                   "SHA",
                                   "Checksum",
                                   1000L,
                                   OAISDataObjectLocationDto.build("http://perdu.com", "AWS"));
                aip.withSyntaxAndDimension(MimeTypeUtils.IMAGE_JPEG, 1000d, 1500d);
                aip.withSoftwareEnvironmentProperty(AipDataSourcePlugin.AIP_PROPERTY_DATA_FILES_TYPES,
                                                    Sets.newHashSet("type1", "type2"));
                aip.registerContentInformation();
                aip.getProperties()
                   .getContentInformations()
                   .stream()
                   .map(ContentInformationDto::getDataObject)
                   .forEach(dataObject -> dataObject.setAdditionalFields(new AdditionalFieldRecord("totoValue")));

                SIPDto sip = SIPDto.build(EntityType.DATA, "sipId");
                SIPEntity sipEntity = SIPEntity.build("PROJECT1",
                                                      IngestMetadata.build("NASA",
                                                                           OffsetDateTime.now().toString(),
                                                                           null,
                                                                           "defaultChain",
                                                                           Sets.newHashSet("Cat!"),
                                                                           StorageMetadata.build("AWS")),
                                                      sip,
                                                      1,
                                                      SIPState.STORED);
                filters.getAipStates()
                       .getValues()
                       .forEach(aipState -> aipEntities.add(AIPEntity.build(sipEntity, aipState, aip)));
            }

            List<EntityModel<AIPEntity>> list = aipEntities.stream().map(EntityModel::of).collect(Collectors.toList());
            return ResponseEntity.ok(PagedModel.of(list,
                                                   new PagedModel.PageMetadata(aipEntities.size(),
                                                                               0,
                                                                               aipEntities.size(),
                                                                               1)));
        }

    }

    @Bean
    public IStorageLocationRestClient storageLocationRestClient() {
        IStorageLocationRestClient mock = Mockito.mock(IStorageLocationRestClient.class);
        StorageLocationConfiguration storageLocationConfiguration = new StorageLocationConfiguration("AWS", null, 1L);
        storageLocationConfiguration.setStorageType(StorageType.ONLINE);
        StorageLocationDto dto = StorageLocationDto.build("AWS", storageLocationConfiguration.toDto())
                                                   .withRunningProcessesInformation(false, false, false, false)
                                                   .withAllowPhysicalDeletion(false)
                                                   .withFilesInformation(1L, 0L, 1L)
                                                   .withErrorInformation(1L, 1L);
        List<EntityModel<StorageLocationDto>> list = new LinkedList<>();
        list.add(EntityModel.of(dto));
        ResponseEntity<List<EntityModel<StorageLocationDto>>> result = ResponseEntity.ok(list);
        Mockito.when(mock.retrieve()).thenReturn(result);
        return mock;
    }

    public record AdditionalFieldRecord(String totoKey) {
        // NOSONAR
    }
}
