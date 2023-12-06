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
package fr.cnes.regards.common.notifier.plugins;

import fr.cnes.regards.framework.amqp.IPublisher;
import fr.cnes.regards.framework.amqp.event.IEvent;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.notifier.domain.NotificationRequest;
import fr.cnes.regards.modules.notifier.domain.plugin.IRecipientNotifier;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * Abstract plugin to send notification using AMQP
 *
 * @author Kevin Marchois
 * @author Léo Mieulet
 */
public abstract class AbstractRabbitMQSender implements IRecipientNotifier {

    public static final String EXCHANGE_PARAM_NAME = "exchange";

    public static final String QUEUE_PARAM_NAME = "queueName";

    public static final String QUEUE_DL_ROUTING_KEY_PARAM_NAME = "queueDeadLetterRoutingKey";

    public static final String RECIPIENT_LABEL_PARAM_NAME = "recipientLabel";

    public static final String DESCRIPTION_PARAM_NAME = "description";

    public static final String DIRECT_NOTIFICATION_ENABLED_PARAM_NAME = "directNotificationEnabled";

    public static final String BLOCKING_REQUIRED_PARAM_NAME = "blockingRequired";

    @Autowired
    private IPublisher publisher;

    //@formatter:off
    @PluginParameter(
        label = "RabbitMQ exchange name",
        name = EXCHANGE_PARAM_NAME)
    private String exchange;

    @PluginParameter(
        label = "RabbitMQ queue name",
        name = QUEUE_PARAM_NAME,
        optional = true)
    private String queueName;

    @PluginParameter(
        label = "RabbitMQ queue dead letter routing key",
        name = QUEUE_DL_ROUTING_KEY_PARAM_NAME,
        optional = true)
    private String queueDlRoutingKey;

    @PluginParameter(
        label = "Recipient label (must be unique).",
        description = "When not specified, the emitter wont know what's the recipient label that should receive its events",
        name = RECIPIENT_LABEL_PARAM_NAME,
        optional = true)
    private String recipientLabel;

    @PluginParameter(label = "Recipient description",
                     name = DESCRIPTION_PARAM_NAME,
                     optional = true,
                     defaultValue = "Rabbit MQ sender")
    private String description;

    @PluginParameter(label = "Direct notification enabled",
                     description = "When true, indicates this plugin can be used to send to the recipient directly without checking product content against notifier rules",
                     name = DIRECT_NOTIFICATION_ENABLED_PARAM_NAME,
                     optional = true,
                     defaultValue = "false")
    private boolean directNotificationEnabled;


    @PluginParameter(
        label = "Blocking notification.",
        description = "Indicate whether this is a blocking notification or not. When value is True, the recipient "
                      + "must acquire this notification to unblock this notification",
        name = BLOCKING_REQUIRED_PARAM_NAME,
        optional = true,
        defaultValue = "false")
    private boolean blockingRequired;
    //@formatter:off
    
    public <T extends IEvent> Set<NotificationRequest> sendEvents(List<T> toSend, Map<String, Object> headers) {
        return sendEvents(toSend, headers, Optional.empty(), 0);
    }

    public <T extends IEvent> Set<NotificationRequest> sendEvents(List<T> toSend,
                                                                  Map<String, Object> headers,
                                                                  Optional<String> routingKey,
                                                                  int priority) {
        this.publisher.broadcastAll(exchange,
                                    Optional.ofNullable(queueName),
                                    routingKey,
                                    Optional.ofNullable(queueDlRoutingKey),
                                    priority,
                                    toSend,
                                    headers);

        // if there is an issue with amqp then none of the message will be sent
        return Collections.emptySet();
    }

    @Override
    public String getRecipientLabel() {
        return recipientLabel;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean isDirectNotificationEnabled() {
        return directNotificationEnabled;
    }

    @Override
    public boolean isBlockingRequired() {
        return blockingRequired;
    }

}
