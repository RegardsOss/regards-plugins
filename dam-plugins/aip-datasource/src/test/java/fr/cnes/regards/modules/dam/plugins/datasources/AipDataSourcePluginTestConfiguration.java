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
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;

import com.google.common.collect.Sets;

import fr.cnes.regards.framework.oais.ContentInformation;
import fr.cnes.regards.framework.oais.OAISDataObjectLocation;
import fr.cnes.regards.framework.oais.urn.DataType;
import fr.cnes.regards.framework.oais.urn.EntityType;
import fr.cnes.regards.modules.accessrights.client.IProjectUsersClient;
import fr.cnes.regards.modules.dam.client.models.IAttributeModelClient;
import fr.cnes.regards.modules.ingest.client.IAIPRestClient;
import fr.cnes.regards.modules.ingest.domain.aip.AIPEntity;
import fr.cnes.regards.modules.ingest.domain.sip.IngestMetadata;
import fr.cnes.regards.modules.ingest.domain.sip.SIPEntity;
import fr.cnes.regards.modules.ingest.domain.sip.SIPState;
import fr.cnes.regards.modules.ingest.dto.aip.AIP;
import fr.cnes.regards.modules.ingest.dto.aip.SearchAIPsParameters;
import fr.cnes.regards.modules.ingest.dto.aip.StorageMetadata;
import fr.cnes.regards.modules.ingest.dto.sip.SIP;
import fr.cnes.regards.modules.project.client.rest.IProjectsClient;
import fr.cnes.regards.modules.project.domain.Project;
import fr.cnes.regards.modules.storagelight.client.IStorageRestClient;
import fr.cnes.regards.modules.storagelight.domain.database.StorageLocationConfiguration;
import fr.cnes.regards.modules.storagelight.domain.dto.StorageLocationDTO;
import fr.cnes.regards.modules.storagelight.domain.plugin.StorageType;

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
        Resource<Project> resource = new Resource<Project>(project);
        ResponseEntity<Resource<Project>> response = new ResponseEntity<Resource<Project>>(resource, HttpStatus.OK);
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
        public ResponseEntity<PagedResources<Resource<AIPEntity>>> searchAIPs(SearchAIPsParameters filters, int page,
                int size) {
            List<AIPEntity> aipEntities = new ArrayList<>();

            for (AIP aip : AipDataSourcePluginTest.createAIPs(1, "tag1", "tag2", "session 1")) {
                aip.getProperties()
                        .withDataObject(DataType.RAWDATA, "Name", "SHA", "Checksum", 1000L,
                                        OAISDataObjectLocation.build("http://perdu.com", "AWS"))
                        .withSyntax(MimeTypeUtils.IMAGE_JPEG);
                aip.getProperties().registerContentInformation();
                aip.getProperties().getContentInformations().get(0).getRepresentationInformation().getSyntax()
                        .setHeight(1500d);
                aip.getProperties().getContentInformations().get(0).getRepresentationInformation().getSyntax()
                        .setWidth(1000d);
                ContentInformation ci = aip.getProperties().getContentInformations().get(0);
                SIP sip = SIP.build(EntityType.DATA, "sipId");
                SIPEntity sipEntity = SIPEntity
                        .build("PROJECT1",
                               IngestMetadata.build("NASA", OffsetDateTime.now().toString(), "defaultChain",
                                                    Sets.newHashSet("Cat!"), StorageMetadata.build("AWS")),
                               sip, 1, SIPState.STORED);

                aipEntities.add(AIPEntity.build(sipEntity, filters.getState(), aip));

            }

            List<Resource<AIPEntity>> list = aipEntities.stream().map(n -> {
                return new Resource<AIPEntity>(n);
            }).collect(Collectors.toList());
            return ResponseEntity.ok(new PagedResources<Resource<AIPEntity>>(list,
                    new PagedResources.PageMetadata(aipEntities.size(), 0, aipEntities.size(), 1)));
        }

    }

    @Bean
    public IStorageRestClient storageRestClient() {
        IStorageRestClient mock = Mockito.mock(IStorageRestClient.class);
        StorageLocationConfiguration storageLocationConfiguration = new StorageLocationConfiguration("AWS", null, 1L);
        storageLocationConfiguration.setStorageType(StorageType.ONLINE);
        StorageLocationDTO dto = StorageLocationDTO.build("AWS", 1L, 1L, 1L, 1L, storageLocationConfiguration);
        List<Resource<StorageLocationDTO>> list = new LinkedList<>();
        list.add(new Resource<>(dto));
        ResponseEntity<List<Resource<StorageLocationDTO>>> result = ResponseEntity.ok(list);
        Mockito.when(mock.retrieve()).thenReturn(result);
        return mock;
    }

}
