/*
 * Copyright 2017-2020 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gson.JsonElement;
import fr.cnes.regards.framework.amqp.IPublisher;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.notifier.domain.NotificationRequest;
import fr.cnes.regards.modules.notifier.domain.plugin.IRecipientNotifier;

/**
 * Sender to send CHRONOS formatted feature notification
 *
 * @author Sébastien Binda
 *
 */
@Plugin(author = "REGARDS Team", description = "Sender to send CHRONOS formatted feature notification",
        id = ChronosRecipientSender.PLUGIN_ID, version = "1.0.0", contact = "regards@c-s.fr", license = "GPLv3",
        owner = "CNES", url = "https://regardsoss.github.io/")
public class ChronosRecipientSender implements IRecipientNotifier {

    public static final String PLUGIN_ID = "ChronosRecipientSender";

    private static final Logger LOGGER = LoggerFactory.getLogger(ChronosRecipientSender.class);

    private static final String OWNER_KEY = "actionOwner";

    private static final String ACTION_KEY = "action";

    @Autowired
    private IPublisher publisher;

    @PluginParameter(label = "RabbitMQ exchange name", name = "exchange")
    private String exchange;

    @PluginParameter(label = "RabbitMQ queue name", name = "queueName", optional = true)
    private String queueName;

    @PluginParameter(label = "Feature created_by property path", name = "createdByPropertyPath", optional = true,
            defaultValue = "history.createdBy")
    private String createdByPropertyPath;

    @PluginParameter(label = "Feature updated_by property path", name = "updatedByPropertyPath", optional = true,
            defaultValue = "history.updatedBy")
    private String updatedByPropertyPath;

    @PluginParameter(label = "Feature deleted_by property path", name = "updatedByPropertyPath", optional = true,
            defaultValue = "history.deletedBy")
    private String deletedByPropertyPath;

    @PluginParameter(label = "Feature gpfs_url property path", name = "gpfsUrlPropertyPath", optional = true,
            defaultValue = "properties.system.gpfs_url")
    private String gpfsUrlPropertyPath;

    @PluginParameter(label = "Feature filename property path", name = "filenamePropertyPath", optional = true,
            defaultValue = "properties.system.filename")
    private String filenamePropertyPath;

    public ChronosRecipientSender() {
    }

    /**
     * This constructor is needed for tests unfortunately
     */
    protected ChronosRecipientSender(IPublisher publisher) {
        this.publisher = publisher;
    }

    public Optional<String> getValue(JsonElement element, String key) {
        if (key.contains(".")) {
            String[] paths = key.split("\\.");
            JsonElement obj = element.getAsJsonObject().get(paths[0]);
            if (obj != null) {
                String newKey = key.substring(key.indexOf('.') + 1);
                return this.getValue(obj, newKey);
            }
        } else {
            JsonElement obj = element.getAsJsonObject().get(key);
            if (obj != null) {
                return Optional.of(obj.getAsString());
            }
        }
        return Optional.empty();
    }

    @Override
    public Collection<NotificationRequest> send(Collection<NotificationRequest> requestsToSend) {
        ListMultimap<Map<String, Object>, ChronosNotificationEvent> toSend = ArrayListMultimap.create();
        List<NotificationRequest> errors = new ArrayList<>();
        for (NotificationRequest request : requestsToSend) {
            JsonElement element = request.getPayload();
            JsonElement metadata = request.getMetadata();
            Optional<String> createdBy = getValue(element, createdByPropertyPath);
            Optional<String> updatedBy = getValue(element, updatedByPropertyPath);
            Optional<String> deletedBy = getValue(element, deletedByPropertyPath);
            String uri = getValue(element, gpfsUrlPropertyPath).orElse(null);
            // This is business key so filename cannot be null!
            String filename = getValue(element, filenamePropertyPath).orElse(null);
            if ((metadata == null) || !createdBy.isPresent() || (filename == null)) {
                LOGGER.error(
                        "Unable to send chronos notification as mandatory parameters [action={}, {}={}, {}={}] are not valid from message={}.",
                        metadata == null ? null : metadata.toString(),
                        createdByPropertyPath,
                        createdBy,
                        gpfsUrlPropertyPath,
                        uri,
                        element == null ? null : element.toString());
                errors.add(request);
            } else {
                Map<String, Object> headers = new HashMap<>();
                String actionOwner = deletedBy.orElse(updatedBy.orElse(createdBy.get()));
                String action = metadata.getAsJsonObject().get(ACTION_KEY).getAsString();
                headers.put(OWNER_KEY, actionOwner);
                headers.put(ACTION_KEY, action);
                if (uri == null) {
                    toSend.put(headers, new ChronosNotificationEvent(action, actionOwner, uri, filename));
                } else {
                    toSend.put(headers, new ChronosNotificationEvent(action, actionOwner, uri.replaceAll("//", "/"), filename));
                }
            }
        }
        for (Map<String, Object> headers : toSend.keySet()) {
            publisher.broadcastAll(exchange, Optional.of(queueName), 0, toSend.get(headers), headers);
        }
        return errors;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public void setCreatedByPropertyPath(String createdByPropertyPath) {
        this.createdByPropertyPath = createdByPropertyPath;
    }

    public void setUpdatedByPropertyPath(String updatedByPropertyPath) {
        this.updatedByPropertyPath = updatedByPropertyPath;
    }

    public void setDeletedByPropertyPath(String deletedByPropertyPath) {
        this.deletedByPropertyPath = deletedByPropertyPath;
    }

    public void setGpfsUrlPropertyPath(String gpfsUrlPropertyPath) {
        this.gpfsUrlPropertyPath = gpfsUrlPropertyPath;
    }

    public void setFilenamePropertyPath(String filenamePropertyPath) {
        this.filenamePropertyPath = filenamePropertyPath;
    }
}
