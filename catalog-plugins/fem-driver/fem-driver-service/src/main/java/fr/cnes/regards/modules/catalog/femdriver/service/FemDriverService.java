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
package fr.cnes.regards.modules.catalog.femdriver.service;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.google.common.collect.Sets;

import fr.cnes.regards.framework.authentication.IAuthenticationResolver;
import fr.cnes.regards.framework.jpa.multitenant.transactional.MultitenantTransactional;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.jobs.domain.JobInfo;
import fr.cnes.regards.framework.modules.jobs.domain.JobParameter;
import fr.cnes.regards.framework.modules.jobs.service.IJobInfoService;
import fr.cnes.regards.modules.catalog.femdriver.dto.FeatureUpdateRequest;
import fr.cnes.regards.modules.catalog.femdriver.service.job.FeaturePageDeletionJob;
import fr.cnes.regards.modules.catalog.femdriver.service.job.FemDeletionJob;
import fr.cnes.regards.modules.catalog.femdriver.service.job.FemNotifierJob;
import fr.cnes.regards.modules.catalog.femdriver.service.job.FemUpdateJob;
import fr.cnes.regards.modules.catalog.services.helper.IServiceHelper;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import fr.cnes.regards.modules.search.domain.SearchRequest;

/**
 * Business service for FEM Driver
 *
 * @author Sébastien Binda
 *
 */
@Service
@MultitenantTransactional
public class FemDriverService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FemDriverService.class);

    @Autowired
    private IAuthenticationResolver authResolver;

    @Autowired
    private IJobInfoService jobInfoService;

    @Autowired
    private IServiceHelper serviceHelper;

    /**
     * Schedule a job to handle update request.  <br/>
     * An update request is build with : <ul>
     * <li> a {@link SearchRequest} containing the search request parameters to find features to update</li>
     * <li> a Map of {@link IProperty} of each property to update</li>
     * </ul>
     * @param request {@link FeatureUpdateRequest}
     * @return {@link JobInfo} of  scheduled job
     * @throws ModuleException
     */
    public JobInfo scheduleUpdate(FeatureUpdateRequest request) throws ModuleException {
        Set<JobParameter> jobParameters = Sets.newHashSet();
        jobParameters.add(new JobParameter(FemUpdateJob.REQUEST_PARAMETER, request));
        JobInfo jobInfo = new JobInfo(false, 0, jobParameters, authResolver.getUser(), FemUpdateJob.class.getName());
        return jobInfoService.createAsQueued(jobInfo);
    }

    /**
     * Schedule a job to handle deletion request.
     *
     * @param request {@link SearchRequest}
     * @return {@link JobInfo} of  scheduled job
     * @throws ModuleException
     */
    public JobInfo scheduleDeletion(SearchRequest request) {
        Set<JobParameter> jobParameters = Sets.newHashSet();
        jobParameters.add(new JobParameter(FemDeletionJob.REQUEST_PARAMETER, request));
        JobInfo jobInfo = new JobInfo(false, 0, jobParameters, authResolver.getUser(), FemDeletionJob.class.getName());
        return jobInfoService.createAsQueued(jobInfo);
    }

    // FIXME : warning all jobs are released at the same time - pay attention to memory consumption!
    public void scheduleDeletionByPage(SearchRequest request, String owner) {
        Pageable page = PageRequest.of(0, 1000);
        Page<DataObject> results = null;
        long totalElementCheck = 0;
        boolean firstPass = true;
        do {
            try {
                // Search data objects
                results = serviceHelper.getDataObjects(request, page.getPageNumber(), page.getPageSize());
                if (firstPass) {
                    totalElementCheck = results.getTotalElements();
                    LOGGER.info("Starting scheduling {} feature deletion requests.", totalElementCheck);
                    firstPass = false;
                }

                // Prepare ids
                Set<String> ids = new HashSet<>();
                for (DataObject dobj : results.getContent()) {
                    ids.add(dobj.getIpId().toString());
                    totalElementCheck--;
                }

                // Scheduling page deletion job
                schedulePageDeletion(ids, owner);

                LOGGER.info("Scheduling job for {} feature deletion requests (remaining {}).", ids.size(),
                            totalElementCheck);
                page = page.next();
            } catch (ModuleException e) {
                LOGGER.error("Error retrieving catalog objects.", e);
                results = null;
            }
        } while ((results != null) && results.hasNext());
    }

    public void schedulePageDeletion(Set<String> ids, String owner) {
        Set<JobParameter> jobParameters = Sets.newHashSet();
        jobParameters.add(new JobParameter(FeaturePageDeletionJob.IDS_PARAMETER, ids));
        JobInfo jobInfo = new JobInfo(false, 0, jobParameters, owner, FeaturePageDeletionJob.class.getName());
        jobInfoService.createAsQueued(jobInfo);
    }

    /**
     * Schedule a job to handle notification request.
     *
     * @param request {@link SearchRequest}
     * @return {@link JobInfo} of  scheduled job
     * @throws ModuleException
     */
    public JobInfo scheduleNotification(SearchRequest request) {
        Set<JobParameter> jobParameters = Sets.newHashSet();
        jobParameters.add(new JobParameter(FemNotifierJob.REQUEST_PARAMETER, request));
        JobInfo jobInfo = new JobInfo(false, 0, jobParameters, authResolver.getUser(), FemNotifierJob.class.getName());
        return jobInfoService.createAsQueued(jobInfo);
    }

}
