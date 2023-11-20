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
package fr.cnes.regards.modules.notifier.plugins;

import com.google.gson.JsonElement;
import fr.cnes.regards.common.notifier.plugins.AbstractRabbitMQSender;
import fr.cnes.regards.framework.amqp.event.Event;
import fr.cnes.regards.framework.amqp.event.ISubscribable;
import fr.cnes.regards.framework.amqp.event.JsonMessageConverter;
import fr.cnes.regards.framework.amqp.event.Target;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.notifier.domain.NotificationRequest;

import java.util.Collection;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Default plugin notification sender
 *
 * @author Kevin Marchois
 */
@Plugin(author = "REGARDS Team",
        description = "Default recipient sender",
        id = RabbitMQSender.PLUGIN_ID,
        version = "1.0.0",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CNES",
        url = "https://regardsoss.github.io/")
public class RabbitMQSender extends AbstractRabbitMQSender {

    @Event(target = Target.ONE_PER_MICROSERVICE_TYPE, converter = JsonMessageConverter.GSON)
    private static class NotificationEvent implements ISubscribable {

        private JsonElement payload;

        private JsonElement metadata;

        public NotificationEvent(NotificationRequest request) {
            this.payload = request.getPayload();
            this.metadata = request.getMetadata();
        }

        public JsonElement getPayload() {
            return payload;
        }

        public void setPayload(JsonElement payload) {
            this.payload = payload;
        }

        public JsonElement getMetadata() {
            return metadata;
        }

        public void setMetadata(JsonElement metadata) {
            this.metadata = metadata;
        }
    }

    public static final String PLUGIN_ID = "RabbitMQSender";

    public static final String ACK_REQUIRED_PARAM_NAME = "ackRequired";

    public static final String DESCRIPTION_PARAM_NAME = "description";

    @PluginParameter(label = "Recipient acknowledgment required",
                     description = "When value is True, the recipient will send back an acknowledgment.",
                     name = ACK_REQUIRED_PARAM_NAME,
                     optional = true,
                     defaultValue = "false")
    private boolean ackRequired;

    @PluginParameter(label = "Recipient description",
                     name = DESCRIPTION_PARAM_NAME,
                     optional = true,
                     defaultValue = "Rabbit MQ sender")
    private String description;

    @Override
    public Collection<NotificationRequest> send(Collection<NotificationRequest> requestsToSend) {

        return sendEvents(requestsToSend.stream().map(NotificationEvent::new).collect(Collectors.toList()),
                          new HashMap<>());
    }

    @Override
    public boolean isAckRequired() {
        return ackRequired;
    }

    @Override
    public String getDescription() {
        return description;
    }

}
