/*
 * Copyright 2017-200 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.femdriver.service.job;

import java.util.List;
import java.util.Map;

import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.jobs.domain.AbstractJob;
import fr.cnes.regards.framework.modules.jobs.domain.JobParameter;
import fr.cnes.regards.framework.modules.jobs.domain.exception.JobParameterInvalidException;
import fr.cnes.regards.framework.modules.jobs.domain.exception.JobParameterMissingException;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.catalog.femdriver.dto.FeatureUpdateRequest;
import fr.cnes.regards.modules.catalog.services.helper.IServiceHelper;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.feature.client.FeatureClient;
import fr.cnes.regards.modules.feature.dto.Feature;
import fr.cnes.regards.modules.feature.dto.PriorityLevel;
import fr.cnes.regards.modules.feature.dto.event.in.FeatureUpdateRequestEvent;
import fr.cnes.regards.modules.feature.dto.urn.FeatureUniformResourceName;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import fr.cnes.regards.modules.search.domain.SearchRequest;

/**
 * Job used to send  {@link FeatureUpdateRequestEvent} to FEM for each {@link DataObject}
 * found in index catalog thanks to the given {@link SearchRequest}.
 *
 * @author Sébastien Binda
 *
 */
public class FemUpdateJob extends AbstractJob<Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FemUpdateJob.class);

    public static final String REQUEST_PARAMETER = "req";

    @Autowired
    private IServiceHelper serviceHelper;

    @Autowired
    private FeatureClient featureClient;

    private FeatureUpdateRequest request;

    private int completionCount = 1;

    @Override
    public void setParameters(Map<String, JobParameter> parameters)
            throws JobParameterMissingException, JobParameterInvalidException {
        request = getValue(parameters, REQUEST_PARAMETER, FeatureUpdateRequest.class);
    }

    @Override
    public void run() {
        PageRequest page = PageRequest.of(0, 1000);
        Page<DataObject> results = null;
        do {
            try {
                results = serviceHelper.getDataObjects(request.getSearchRequest(), page.getPageNumber(),
                                                       page.getPageSize());
                if ((page.getPageNumber() == 0) && (results.getTotalPages() > 0)) {
                    completionCount = results.getTotalPages();
                }
                List<Feature> features = Lists.newArrayList();
                for (DataObject dobj : results.getContent()) {
                    try {
                        Feature feature = Feature.build(dobj.getProviderId(),
                                                        FeatureUniformResourceName
                                                                .fromString(dobj.getIpId().toString()),
                                                        null, EntityType.DATA, dobj.getModel().getName());
                        for (IProperty<?> prop : request.getProperties()) {
                            feature.addProperty(prop);
                        }
                        features.add(feature);
                    } catch (IllegalArgumentException e) {
                        LOGGER.error("Error trying to delete feature {} from FEM microservice. Feature identifier is not a valid FeatureUniformResourceName. Cause: {}",
                                     dobj.getIpId().toString(), e.getMessage());
                    }
                }
                LOGGER.info("[FEM DRIVER] Sending {} features update requests.", features.size());
                featureClient.updateFeatures(features, PriorityLevel.NORMAL);
            } catch (ModuleException e) {
                LOGGER.error("Error retrieving catalog objects.", e);
                results = null;
            } finally {
                advanceCompletion();
            }
        } while ((results != null) && results.hasNext());
    }

    @Override
    public int getCompletionCount() {
        return this.completionCount;
    }

}
