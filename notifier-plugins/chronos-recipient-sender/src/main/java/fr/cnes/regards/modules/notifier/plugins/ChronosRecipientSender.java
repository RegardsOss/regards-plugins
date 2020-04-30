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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.gson.JsonElement;

import fr.cnes.regards.framework.amqp.IPublisher;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.notifier.plugin.IRecipientNotifier;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(ChronosRecipientSender.class);

    public static final String PLUGIN_ID = "ChronosRecipientSender";

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

    @PluginParameter(label = "Feature updated_by property path", name = "gpfsUrlPropertyPath", optional = true,
            defaultValue = "properties.system.gpfs_url")
    private String gpfsUrlPropertyPath;

    @Override
    public boolean send(JsonElement element, String action) {
        Optional<String> createdBy = getValue(element, createdByPropertyPath);
        Optional<String> updatedBy = getValue(element, updatedByPropertyPath);
        Optional<String> deletedBy = getValue(element, deletedByPropertyPath);
        String uri = getValue(element, gpfsUrlPropertyPath).orElse(null);
        if ((action == null) || (createdBy == null) || (uri == null)) {
            LOGGER.error("Unable to send chronos notification as mandatory parameters [action={}, {}={}, {}={}] are not valid from message={}.",
                         action, createdByPropertyPath, createdBy, gpfsUrlPropertyPath, uri, element.toString());
            return false;
        } else {
            Map<String, Object> headers = new HashMap<>();
            String actionOwner = deletedBy.orElse(updatedBy.orElse(createdBy.get()));
            headers.put(OWNER_KEY, actionOwner);
            headers.put(ACTION_KEY, action);
            this.publisher.broadcast(exchange, Optional.of(queueName), 0,
                                     ChronosNotificationEvent.build(action, actionOwner, uri), headers);
            return true;
        }
    }

    public Optional<String> getValue(JsonElement element, String key) {
        if (key.contains(".")) {
            String[] paths = key.split("\\.");
            JsonElement obj = element.getAsJsonObject().get(paths[0]);
            if (obj != null) {
                String newKey = key.substring(key.indexOf(".") + 1, key.length());
                return this.getValue(obj, newKey);
            }
        } else {
            JsonElement obj = element.getAsJsonObject().get(key);
            if (obj != null) {
                return Optional.of(obj.toString());
            }
        }
        return Optional.empty();
    }
}
