package fr.cnes.regards.modules.dam.plugins.datasources;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;

import com.google.common.collect.Sets;

import fr.cnes.regards.framework.oais.OAISDataObjectLocation;
import fr.cnes.regards.framework.oais.urn.DataType;
import fr.cnes.regards.framework.oais.urn.EntityType;
import fr.cnes.regards.modules.accessrights.client.IProjectUsersClient;
import fr.cnes.regards.modules.ingest.client.IAIPRestClient;
import fr.cnes.regards.modules.ingest.domain.aip.AIPEntity;
import fr.cnes.regards.modules.ingest.domain.sip.IngestMetadata;
import fr.cnes.regards.modules.ingest.domain.sip.SIPEntity;
import fr.cnes.regards.modules.ingest.domain.sip.SIPState;
import fr.cnes.regards.modules.ingest.dto.aip.AIP;
import fr.cnes.regards.modules.ingest.dto.aip.SearchAIPsParameters;
import fr.cnes.regards.modules.ingest.dto.aip.StorageMetadata;
import fr.cnes.regards.modules.ingest.dto.sip.SIP;
import fr.cnes.regards.modules.model.client.IAttributeModelClient;
import fr.cnes.regards.modules.project.client.rest.IProjectsClient;
import fr.cnes.regards.modules.project.domain.Project;
import fr.cnes.regards.modules.storage.client.IStorageRestClient;
import fr.cnes.regards.modules.storage.domain.database.StorageLocationConfiguration;
import fr.cnes.regards.modules.storage.domain.dto.StorageLocationDTO;
import fr.cnes.regards.modules.storage.domain.plugin.StorageType;

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
    public IProjectsClient projectsClientMock() {
        IProjectsClient client = Mockito.mock(IProjectsClient.class);
        Project project = new Project("desc", null, true, "test-project");
        project.setHost("http://test.com");
        EntityModel<Project> resource = new EntityModel<Project>(project);
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
                                                       new Class<?>[] { IAIPRestClient.class }, handler);
    }

    private class AipClientProxy {

        @SuppressWarnings("unused")
        public ResponseEntity<PagedModel<EntityModel<AIPEntity>>> searchAIPs(SearchAIPsParameters filters, int page,
                int size) {
            List<AIPEntity> aipEntities = new ArrayList<>();

            for (AIP aip : AipDataSourcePluginTest.createAIPs(1, "tag1", "tag2", "session 1")) {
                aip.withDataObject(DataType.RAWDATA, "Name", "SHA", "Checksum", 1000L,
                                   OAISDataObjectLocation.build("http://perdu.com", "AWS"));
                aip.withSyntaxAndDimension(MimeTypeUtils.IMAGE_JPEG, 1000d, 1500d);
                aip.withSoftwareEnvironmentProperty(AipDataSourcePlugin.AIP_PROPERTY_DATA_FILES_TYPES,
                                                    Sets.newHashSet("type1", "type2"));
                aip.registerContentInformation();
                SIP sip = SIP.build(EntityType.DATA, "sipId");
                SIPEntity sipEntity = SIPEntity
                        .build("PROJECT1",
                               IngestMetadata.build("NASA", OffsetDateTime.now().toString(), "defaultChain",
                                                    Sets.newHashSet("Cat!"), StorageMetadata.build("AWS")),
                               sip, 1, SIPState.STORED);

                aipEntities.add(AIPEntity.build(sipEntity, filters.getState(), aip));

            }

            List<EntityModel<AIPEntity>> list = aipEntities.stream().map(n -> {
                return new EntityModel<AIPEntity>(n);
            }).collect(Collectors.toList());
            return ResponseEntity.ok(new PagedModel<EntityModel<AIPEntity>>(list,
                    new PagedModel.PageMetadata(aipEntities.size(), 0, aipEntities.size(), 1)));
        }

    }

    @Bean
    public IStorageRestClient storageRestClient() {
        IStorageRestClient mock = Mockito.mock(IStorageRestClient.class);
        StorageLocationConfiguration storageLocationConfiguration = new StorageLocationConfiguration("AWS", null, 1L);
        storageLocationConfiguration.setStorageType(StorageType.ONLINE);
        StorageLocationDTO dto = StorageLocationDTO.build("AWS", 1L, 1L, 1L, 1L, false, false, false,
                                                          storageLocationConfiguration);
        List<EntityModel<StorageLocationDTO>> list = new LinkedList<>();
        list.add(new EntityModel<>(dto));
        ResponseEntity<List<EntityModel<StorageLocationDTO>>> result = ResponseEntity.ok(list);
        Mockito.when(mock.retrieve()).thenReturn(result);
        return mock;
    }

}
