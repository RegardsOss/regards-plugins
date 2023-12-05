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

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.jobs.domain.AbstractJob;
import fr.cnes.regards.framework.modules.jobs.domain.JobParameter;
import fr.cnes.regards.framework.modules.jobs.domain.exception.JobParameterInvalidException;
import fr.cnes.regards.framework.modules.jobs.domain.exception.JobParameterMissingException;
import fr.cnes.regards.framework.modules.jobs.service.IJobInfoService;
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
import fr.cnes.regards.modules.model.dto.properties.StringProperty;
import fr.cnes.regards.modules.search.dto.SearchRequest;
import org.apache.commons.compress.utils.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * Job used to send  {@link FeatureUpdateRequestEvent} to FEM for each {@link DataObject}
 * found in index catalog thanks to the given {@link SearchRequest}.
 *
 * @author Sébastien Binda
 */
public class FemUpdateJob extends AbstractJob<Void> {

    public static final String REQUEST_PARAMETER = "req";

    @Autowired
    private IServiceHelper serviceHelper;

    @Autowired
    private FeatureClient featureClient;

    @Autowired
    private IJobInfoService jobService;

    private FeatureUpdateRequest request;

    private int completionCount = 1;

    private String jobOwner;

    @Override
    public void setParameters(Map<String, JobParameter> parameters)
        throws JobParameterMissingException, JobParameterInvalidException {
        request = getValue(parameters, REQUEST_PARAMETER, FeatureUpdateRequest.class);
        jobOwner = jobService.retrieveJob(this.getJobInfoId()).getOwner();
    }

    @Override
    public void run() {
        Pageable page = PageRequest.of(0, 1000);
        Page<DataObject> results = null;
        do {
            try {
                results = serviceHelper.getDataObjects(request.getSearchRequest(),
                                                       page.getPageNumber(),
                                                       page.getPageSize());
                if ((page.getPageNumber() == 0) && (results.getTotalPages() > 0)) {
                    completionCount = results.getTotalPages();
                }
                List<Feature> features = Lists.newArrayList();
                for (DataObject dobj : results.getContent()) {
                    try {
                        Feature feature = Feature.build(dobj.getProviderId(),
                                                        null,
                                                        FeatureUniformResourceName.fromString(dobj.getIpId()
                                                                                                  .toString()),
                                                        null,
                                                        EntityType.DATA,
                                                        dobj.getModel().getName());
                        for (IProperty<?> prop : request.getFeature().getProperties()) {
                            // Hack to allow fem driver to set null value into a string attribute by passing the
                            // string containing "null".
                            if (prop instanceof StringProperty stringProp && "null".equals(stringProp.getValue())) {
                                stringProp.setValue(null);
                            }
                            feature.addProperty(prop);
                        }
                        features.add(feature);
                    } catch (IllegalArgumentException e) {
                        logger.error(
                            "Error trying to update feature {} from FEM microservice. Feature identifier is not a valid FeatureUniformResourceName. Cause: {}",
                            dobj.getIpId().toString(),
                            e.getMessage());
                    }
                }
                logger.info("[FEM DRIVER] Sending {} features update requests.", features.size());
                featureClient.updateFeatures(jobOwner, features, PriorityLevel.NORMAL);
                page = page.next();
            } catch (ModuleException e) {
                logger.error("Error retrieving catalog objects.", e);
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
