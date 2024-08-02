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

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import fr.cnes.regards.common.notifier.plugins.AbstractRabbitMQSender;
import fr.cnes.regards.framework.amqp.RawMessageEvent;
import fr.cnes.regards.framework.amqp.configuration.AmqpConstants;
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.modules.feature.dto.FeatureFile;
import fr.cnes.regards.modules.feature.dto.urn.FeatureUniformResourceName;
import fr.cnes.regards.modules.ltamanager.dto.submission.LtaDataType;
import fr.cnes.regards.modules.ltamanager.dto.submission.input.ProductFileDto;
import fr.cnes.regards.modules.ltamanager.dto.submission.input.SubmissionRequestDto;
import fr.cnes.regards.modules.notifier.domain.NotificationRequest;
import fr.cnes.regards.modules.notifier.utils.SessionNameAndOwner;
import fr.cnes.regards.modules.notifier.utils.SessionUtils;
import fr.cnes.regards.modules.workermanager.amqp.events.EventHeadersHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * Notification sender plugin for Lta Request.
 * The plugin receive a list {@link fr.cnes.regards.modules.feature.dto.Feature Feature} wrapped in
 * {@link fr.cnes.regards.modules.notifier.domain.NotificationRequest NotificationRequest} and send
 * {@link fr.cnes.regards.modules.ltamanager.dto.submission.input.SubmissionRequestDto SubmissionRequestDto} through AMQP
 *
 * @author Thibaud Michaudel
 */
@Plugin(author = "REGARDS Team",
        description = "Lta request sender",
        id = LtaRequestSender.PLUGIN_ID,
        version = "1.0.0",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CNES",
        url = "https://regardsoss.github.io/")
public class LtaRequestSender extends AbstractRabbitMQSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(LtaRequestSender.class);

    public static final String PLUGIN_ID = "LtaRequestSender";

    public static final String DATATYPE_PARAM_NAME = "dataType";

    public static final String SESSION_NAME_PATTERN_PARAM_NAME = "sessionNamePattern";

    public static final String SESSION_NAME_PATTERN_ERROR = "{sessionNamePatternError}-#day";

    public static final String RECIPIENT_TENANT_PARAM_NAME = "recipientTenant";

    public static final String REPLACE_MODE_PARAM_NAME = "replaceMode";

    public static final String ACK_REQUIRED_PARAM_NAME = "ackRequired";

    private static final TypeToken<List<FeatureFile>> typeTokenFiles = new TypeToken<>() {

    };

    private static final TypeToken<Map<String, Object>> typeTokenProperties = new TypeToken<>() {

    };

    @Autowired
    private IRuntimeTenantResolver runtimeTenantResolver;

    @Autowired
    private Gson gson;

    @PluginParameter(label = "Data Type",
                     description = "Product datatype of the generated DTOs.",
                     name = DATATYPE_PARAM_NAME)
    private String dataType;

    @PluginParameter(label = "Pattern to name the session on the dashboard (optional).",
                     name = SESSION_NAME_PATTERN_PARAM_NAME,
                     description = "If the parameter is not filled, the session will be named with the metadata of "
                                   + "the requests received. If a value is provided, the session will be named the following way "
                                   + "\"{<jsonPathToAccessProductType>}-#day(.*)\""
                                   + ", where the parameter <jsonPathToAccessProductType> has to be replaced with the json path to access the product type. "
                                   + "The RegExp could be for instance : \"{properties.data.type}-#day-foo\". "
                                   + "The parameter properties.data.type will be replaced with the corresponding product type and #day with the ISO_LOCAL_DATE "
                                   + "formatted current date. Note that any additional parameters can be provided after #day to help "
                                   + "identifying the session names on the dashboard. If the pattern is not found, the "
                                   + "session will be named with the pattern "
                                   + SESSION_NAME_PATTERN_ERROR
                                   + ". In that "
                                   + "case, check if the path to access the product type is valid and if the regexp match the "
                                   + "pattern mentioned before.",
                     optional = true)
    private String sessionNamePattern;

    @PluginParameter(label = "Recipient tenant (optional)",
                     name = RECIPIENT_TENANT_PARAM_NAME,
                     description = "Specify the recipient tenant in case it is different from the one sending the messages.",
                     optional = true)
    private String recipientTenant;

    @PluginParameter(label = "Replace Mode",
                     description = "Behaviour when the request already exists",
                     name = REPLACE_MODE_PARAM_NAME)
    private boolean replaceMode;

    @PluginParameter(label = "Recipient acknowledgment required",
                     description = "When value is True, the recipient will send back an acknowledgment.",
                     name = ACK_REQUIRED_PARAM_NAME,
                     optional = true,
                     defaultValue = "false")
    private boolean ackRequired;

    @Override
    public Collection<NotificationRequest> send(Collection<NotificationRequest> requestsToProcess) {

        return sendEvents(buildLtaRequestSenderMessages(requestsToProcess), new HashMap<>());
    }

    private List<RawMessageEvent> buildLtaRequestSenderMessages(Collection<NotificationRequest> requestsToProcess) {
        List<RawMessageEvent> messagesToSend = new ArrayList<>();

        String tenantName = recipientTenant == null ? runtimeTenantResolver.getTenant() : recipientTenant;

        for (NotificationRequest notificationRequest : requestsToProcess) {
            SessionNameAndOwner sessionNameAndOwner = SessionUtils.computeSessionNameAndOwner(notificationRequest,
                                                                                              sessionNamePattern,
                                                                                              tenantName);

            SubmissionRequestDto payload = buildSubmissionRequestDto(notificationRequest, sessionNameAndOwner);

            MessageProperties headers = new MessageProperties();
            headers.setHeader(EventHeadersHelper.REQUEST_ID_HEADER, notificationRequest.getRequestId());
            headers.setHeader(EventHeadersHelper.TENANT_HEADER, tenantName);
            headers.setHeader(AmqpConstants.REGARDS_REQUEST_OWNER_HEADER, sessionNameAndOwner.sessionOwnerName());

            messagesToSend.add(new RawMessageEvent(gson.toJson(payload).getBytes(), headers));
        }
        return messagesToSend;
    }

    private SubmissionRequestDto buildSubmissionRequestDto(NotificationRequest notificationRequest,
                                                           SessionNameAndOwner sessionNameAndOwner) {
        // Retrieve some information from the notification request
        JsonObject notificationRequestPayload = notificationRequest.getPayload();
        String id = gson.fromJson(notificationRequestPayload.get("id"), String.class);
        FeatureUniformResourceName urn = gson.fromJson(notificationRequestPayload.get("urn"),
                                                       FeatureUniformResourceName.class);
        IGeometry geometry = gson.fromJson(notificationRequestPayload.get("geometry"), IGeometry.class);
        List<FeatureFile> files = gson.fromJson(notificationRequestPayload.get("files"), typeTokenFiles.getType());
        Map<String, Object> properties = gson.fromJson(notificationRequestPayload.get("properties"),
                                                       typeTokenProperties.getType());
        // Create the submission request to send
        return new SubmissionRequestDto(notificationRequest.getRequestId(),
                                        id,
                                        dataType,
                                        geometry,
                                        mapFeatureFilesToProductFilesDtoTo(id, files),
                                        Collections.emptyList(),
                                        urn.toString(),
                                        properties,
                                        null,
                                        sessionNameAndOwner.sessionName(),
                                        replaceMode);
    }

    private List<ProductFileDto> mapFeatureFilesToProductFilesDtoTo(String featureId,
                                                                    List<FeatureFile> featureFileList) {
        return featureFileList.stream()
                              .map(featureFile -> new ProductFileDto(LtaDataType.parse(featureFile.getAttributes()
                                                                                                  .getDataType()
                                                                                                  .toString(),
                                                                                       LtaDataType.RAWDATA),
                                                                     getUrl(featureId, featureFile),
                                                                     featureFile.getAttributes().getFilename(),
                                                                     featureFile.getAttributes().getChecksum(),
                                                                     featureFile.getAttributes().getMimeType()))
                              .toList();
    }

    private static String getUrl(String featureId, FeatureFile featureFile) {
        if (featureFile.getLocations().size() > 1) {
            LOGGER.warn(
                "The file {} of the feature {} has multiples storage locations but only one will be used in the submission request",
                featureFile.getAttributes().getFilename(),
                featureId);
        }
        return featureFile.getLocations().iterator().next().getUrl();
    }

    @Override
    public boolean isAckRequired() {
        return ackRequired;
    }

}
