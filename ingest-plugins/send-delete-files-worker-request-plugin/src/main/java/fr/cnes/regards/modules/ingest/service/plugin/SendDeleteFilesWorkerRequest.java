/*
 * Copyright 2017-2023 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.ingest.service.plugin;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import fr.cnes.regards.framework.amqp.IPublisher;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.oais.ContentInformation;
import fr.cnes.regards.modules.ingest.domain.aip.AIPEntity;
import fr.cnes.regards.modules.ingest.domain.aip.AbstractAIPEntity;
import fr.cnes.regards.modules.ingest.domain.plugin.ISipPostprocessing;
import fr.cnes.regards.modules.ingest.domain.request.postprocessing.PostProcessResult;
import fr.cnes.regards.modules.workermanager.amqp.events.RawMessageBuilder;
import fr.cnes.regards.modules.workermanager.amqp.events.in.RequestEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Sébastien Binda
 **/
@Plugin(id = SendDeleteFilesWorkerRequest.PLUGIN_ID,
        version = "1.12.0-SNAPSHOT",
        description = "This ingest post processing plugin send a request to worker manager with all input files"
                      + " urls in the original SIP. This plugin does not delete files itself. The deletion is done by"
                      + " the associated worker so you have to configure the associated worker in the workermanager "
                      + "microservice",
        author = "REGARDS Team",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CSSI",
        url = "https://github.com/RegardsOss")
public class SendDeleteFilesWorkerRequest implements ISipPostprocessing {

    public final static String PLUGIN_ID = "WorkerCleanRequestPlugin";

    public final static String WORKER_CONTENT_TYPE_DEFAULT = "delete-files-request";

    @Autowired
    private IPublisher publisher;

    @Autowired
    private IRuntimeTenantResolver runtimeTenantResolver;

    @Autowired
    private Gson gson;

    @PluginParameter(label = "Content type",
                     description = "Select content type for the request sent to workermanager microservice",
                     optional = true,
                     defaultValue = WORKER_CONTENT_TYPE_DEFAULT)
    private String contentType = WORKER_CONTENT_TYPE_DEFAULT;

    @Override
    public PostProcessResult postprocess(Collection<AIPEntity> aipEntities) {
        PostProcessResult ppr = new PostProcessResult();
        ppr.buildErrors(Maps.newHashMap());
        if (!CollectionUtils.isEmpty(aipEntities)) {
            computeFilesToDeletePerSession(aipEntities).asMap().forEach((session, urls) -> {
                Message message = RawMessageBuilder.build(runtimeTenantResolver.getTenant(),
                                                          contentType,
                                                          session.getLeft(),
                                                          session.getRight(),
                                                          UUID.randomUUID().toString(),
                                                          DeleteFilesRequestDTO.build(urls),
                                                          gson);

                publisher.basicPublish(runtimeTenantResolver.getTenant(),
                                       "regards.broadcast." + RequestEvent.class.getName(),
                                       "",
                                       message);
            });
            ppr.buildSuccesses(aipEntities.stream().map(AbstractAIPEntity::getAipId).collect(Collectors.toSet()));
        }

        return ppr;
    }

    private Multimap<Pair<String, String>, String> computeFilesToDeletePerSession(Collection<AIPEntity> aipEntities) {
        Multimap<Pair<String, String>, String> entitiesPerSession = ArrayListMultimap.create();
        aipEntities.stream().forEach(a -> {
            Pair<String, String> session = Pair.of(a.getSip().getSessionOwner(), a.getSip().getSession());
            entitiesPerSession.putAll(session, getUrlsToDelete(a));
        });
        return entitiesPerSession;
    }

    private List<String> getUrlsToDelete(AIPEntity aipEntity) {
        return aipEntity.getSip()
                        .getSip()
                        .getProperties()
                        .getContentInformations()
                        .stream()
                        .flatMap(this::getUrlsToDeleteFromDataObject)
                        .toList();
    }

    private Stream<String> getUrlsToDeleteFromDataObject(ContentInformation contentInformation) {
        return contentInformation.getDataObject()
                                 .getLocations()
                                 .stream()
                                 .filter(loc -> loc.getStorage() == null)
                                 .map(f -> f.getUrl());
    }

}
