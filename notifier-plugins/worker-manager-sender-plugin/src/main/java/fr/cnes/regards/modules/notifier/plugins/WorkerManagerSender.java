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
package fr.cnes.regards.modules.notifier.plugins;

import com.google.gson.JsonObject;
import fr.cnes.regards.common.notifier.plugins.AbstractRabbitMQSender;
import fr.cnes.regards.framework.amqp.RawMessageEvent;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.modules.notifier.domain.NotificationRequest;
import fr.cnes.regards.modules.notifier.utils.SessionNameAndOwner;
import fr.cnes.regards.modules.notifier.utils.SessionUtils;
import fr.cnes.regards.modules.workermanager.amqp.events.RawMessageBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The purpose of this plugin is to send worker manager processing requests through rs-notifier microservice.
 *
 * @author Iliana Ghazali
 **/
@Plugin(author = "REGARDS Team",
        description = "The purpose of this plugin is to send worker manager processing requests.",
        id = WorkerManagerSender.PLUGIN_ID,
        version = "1.0.0",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CNES",
        url = "https://regardsoss.github.io/")
public class WorkerManagerSender extends AbstractRabbitMQSender {

    //@formatter:off
    @PluginParameter(
            label = "Recipient acknowledgment",
            description = "When value is True, the recipient will send back an acknowledgment.",
            name = ACK_REQUIRED_PARAM_NAME,
            optional = true,
            defaultValue = "false")
    private boolean ackRequired;

    @PluginParameter(
            label = "Pattern to name the session on the dashboard (optional).",
            name = WS_SESSION_NAME_PATTERN_NAME,
            description = "If the parameter is not filled, the session will be named with the metadata of "
                    + "the requests received. If a value is provided, the session will be named the following way "
                    + "\"{<jsonPathToAccessProductType>}-#day(.*)\""
                    + ", where the parameter <jsonPathToAccessProductType> has to be replaced with the json path to access the product type. "
                    + "The RegExp could be for instance : \"{properties.data.type}-#day-foo\". "
                    + "The parameter properties.data.type will be replaced with the corresponding product type and #day with the ISO_LOCAL_DATE "
                    + "formatted current date. Note that any additional parameters can be provided after #day to help "
                    + "identifying the session names on the dashboard. If the pattern is not found, the "
                    + "session will be named with the pattern " + SessionUtils.SESSION_NAME_PATTERN_ERROR + ". In that "
                    + "case, check if the path to access the product type is valid and if the regexp match the "
                    + "pattern mentioned before.",
            optional = true)
    private String sessionNamePattern;

    @PluginParameter(
            label = "Type of products processed",
            name = WS_CONTENT_TYPE_NAME)
    private String contentType;

    @PluginParameter(
            label = "Recipient tenant (optional)",
            name = RECIPIENT_TENANT_NAME,
            description = "Specify the recipient tenant in case it is different from the one sending the messages.",
            optional = true)
    private String recipientTenant;
    //@formatter:on

    @Autowired
    private IRuntimeTenantResolver runtimeTenantResolver;

    public static final String PLUGIN_ID = "WorkerManagerSender";

    /**
     * Plugin parameters names
     */
    public static final String ACK_REQUIRED_PARAM_NAME = "ackRequired";

    public static final String WS_SESSION_NAME_PATTERN_NAME = "sessionNamePattern";

    public static final String WS_CONTENT_TYPE_NAME = "contentType";

    public static final String RECIPIENT_TENANT_NAME = "recipientTenant";

    @Override
    public Collection<NotificationRequest> send(Collection<NotificationRequest> requestsToProcess) {
        List<RawMessageEvent> messagesToSend = buildWorkerManagerSenderMessages(requestsToProcess);
        // headers are empty hashmap because they are already contained in Message.messageProperties
        return sendEvents(messagesToSend, new HashMap<>());
    }

    @Override
    public boolean isAckRequired() {
        return this.ackRequired;
    }

    private List<RawMessageEvent> buildWorkerManagerSenderMessages(Collection<NotificationRequest> requestsToProcess) {
        List<RawMessageEvent> messagesToSend = new ArrayList<>();
        String tenantName = recipientTenant == null ? runtimeTenantResolver.getTenant() : recipientTenant;

        for (NotificationRequest notificationRequest : requestsToProcess) {
            JsonObject feature = notificationRequest.getPayload();

            SessionNameAndOwner sessionNameAndOwner = SessionUtils.computeSessionNameAndOwner(notificationRequest,
                                                                                              sessionNamePattern,
                                                                                              tenantName);
            messagesToSend.add(RawMessageBuilder.build(tenantName,
                                                       getContentType(notificationRequest),
                                                       sessionNameAndOwner.sessionOwnerName(),
                                                       sessionNameAndOwner.sessionName(),
                                                       UUID.randomUUID().toString(),
                                                       null,
                                                       feature.toString().getBytes(StandardCharsets.UTF_8)));
        }
        return messagesToSend;
    }

    /**
     * Subclasses can use the NotificationRequest or its payload to change contentType
     */
    protected String getContentType(NotificationRequest notificationRequest) {
        return contentType;
    }
}
