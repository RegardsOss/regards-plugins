/*
 * Copyright 2017-2019 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.jobs.domain.AbstractJob;
import fr.cnes.regards.framework.modules.jobs.domain.JobParameter;
import fr.cnes.regards.framework.modules.jobs.domain.exception.JobParameterInvalidException;
import fr.cnes.regards.framework.modules.jobs.domain.exception.JobParameterMissingException;
import fr.cnes.regards.framework.modules.jobs.service.IJobInfoService;
import fr.cnes.regards.modules.catalog.services.helper.IServiceHelper;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.feature.client.FeatureClient;
import fr.cnes.regards.modules.feature.dto.PriorityLevel;
import fr.cnes.regards.modules.feature.dto.urn.FeatureUniformResourceName;
import fr.cnes.regards.modules.search.domain.SearchRequest;

/**
 * @author sbinda
 *
 */
public class FemDeletionJob extends AbstractJob<Void> {

    public static final String REQUEST_PARAMETER = "req";

    @Autowired
    private IServiceHelper serviceHelper;

    @Autowired
    private IJobInfoService jobService;

    @Autowired
    private FeatureClient featureClient;

    private SearchRequest request;

    private String jobOwner;

    @Override
    public void setParameters(Map<String, JobParameter> parameters)
            throws JobParameterMissingException, JobParameterInvalidException {
        request = getValue(parameters, REQUEST_PARAMETER, SearchRequest.class);
        jobOwner = jobService.retrieveJob(this.getJobInfoId()).getOwner();
    }

    @Override
    public void run() {
        Pageable page = PageRequest.of(0, 1000);
        Page<DataObject> results = null;
        // Add a control value to manage asynchronous on the fly deletion that shift pages.
        long totalElement = 0;
        long totalElementCheck = 0;
        boolean firstPass = true;
        do {
            try {
                results = serviceHelper.getDataObjects(request, page.getPageNumber(), page.getPageSize());
                if (firstPass) {
                    // Set total element to send at first pass before any deletion request.
                    totalElementCheck = results.getTotalElements();
                    totalElement = results.getTotalElements();
                    firstPass = false;
                }
                List<FeatureUniformResourceName> features = Lists.newArrayList();
                for (DataObject dobj : results.getContent()) {
                    if (totalElementCheck == 0) {
                        // All elements sended
                        break;
                    }
                    try {
                        features.add(FeatureUniformResourceName.fromString(dobj.getIpId().toString()));
                        totalElementCheck--;
                    } catch (IllegalArgumentException e) {
                        logger.error("Error trying to delete feature {} from FEM microservice. Feature identifier is not a valid FeatureUniformResourceName. Cause: {}",
                                     dobj.getIpId().toString(), e.getMessage());
                    }
                }
                logger.info("[FEM DRIVER] Sending {} features deletion requests (remaining {}).", features.size(),
                            totalElementCheck);
                featureClient.deleteFeatures(jobOwner, features, PriorityLevel.NORMAL);

                if (results.hasNext()) {
                    page = page.next();
                } else if (totalElementCheck > 0) {
                    page = PageRequest.of(0, 1000);
                } else {
                    logger.info("All {} features has been deleted!", totalElement);
                }
            } catch (ModuleException e) {
                logger.error("Error retrieving catalog objects.", e);
                results = null;
            }
        } while (((results != null) && results.hasNext()) || (totalElementCheck > 0));
    }
}
