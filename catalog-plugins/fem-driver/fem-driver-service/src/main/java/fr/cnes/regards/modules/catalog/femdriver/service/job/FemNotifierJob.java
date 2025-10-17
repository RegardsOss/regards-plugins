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
package fr.cnes.regards.modules.catalog.femdriver.service.job;

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
import fr.cnes.regards.modules.search.dto.SearchRequest;
import org.apache.commons.compress.utils.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author sbinda
 */
public class FemNotifierJob extends AbstractJob<Void> {

    public static final String REQUEST_PARAMETER = "req";

    public static final String RECIPIENTS_PARAMETER = "recipients";

    @Autowired
    private IServiceHelper serviceHelper;

    @Autowired
    private FeatureClient featureClient;

    @Autowired
    private IJobInfoService jobService;

    private SearchRequest request;

    /**
     * List of recipients(business identifiers of plugin configurations
     * {@link fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration}) for the direct
     * notification
     */
    private Set<String> recipients;

    private String jobOwner;

    @Override
    public void setParameters(Map<String, JobParameter> parameters)
        throws JobParameterMissingException, JobParameterInvalidException {
        jobOwner = jobService.retrieveJob(this.getJobInfoId()).getOwner();

        request = getValue(parameters, REQUEST_PARAMETER, SearchRequest.class);
        recipients = getValue(parameters, RECIPIENTS_PARAMETER);
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        Pageable page = PageRequest.of(0, 1000);
        Page<DataObject> results = null;
        do {
            try {
                results = serviceHelper.getDataObjects(request, page.getPageNumber(), page.getPageSize());
                List<FeatureUniformResourceName> features = new ArrayList<>();
                for (DataObject dataObject : results.getContent()) {
                    addFeature(dataObject, features);
                }
                logger.info("[FEM DRIVER NOTIFICATION JOB] Sending {} features notify requests.", features.size());
                featureClient.notifyFeatures(jobOwner, features, PriorityLevel.NORMAL, recipients);
                page = page.next();
            } catch (ModuleException e) {
                logger.error("[FEM DRIVER NOTIFICATION JOB] Error retrieving catalog objects.", e);
                results = null;
            }
        } while (results != null && results.hasNext());
        logger.info("[FEM DRIVER NOTIFICATION JOB] Handled in {}ms.", System.currentTimeMillis() - start);
    }

    private void addFeature(DataObject dataObject, List<FeatureUniformResourceName> features) {
        try {
            features.add(FeatureUniformResourceName.fromString(dataObject.getIpId().toString()));
        } catch (IllegalArgumentException e) {
            logger.error("[FEM DRIVER NOTIFICATION JOB] Error trying to notify feature [{}] from FEM microservice. "
                         + "Feature identifier is not a valid FeatureUniformResourceName. Cause: {}",
                         dataObject.getIpId().toString(),
                         e.getMessage());
        }
    }

}
