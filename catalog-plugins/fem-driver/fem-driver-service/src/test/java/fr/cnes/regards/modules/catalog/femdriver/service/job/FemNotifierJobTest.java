/*
 * Copyright 2017-2021 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
import java.util.Objects;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.google.common.collect.Sets;
import fr.cnes.regards.framework.amqp.event.ISubscribable;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.jobs.domain.JobStatus;
import fr.cnes.regards.framework.modules.jobs.service.IJobInfoService;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.modules.catalog.femdriver.service.FemDriverService;
import fr.cnes.regards.modules.feature.dto.event.in.FeatureNotificationRequestEvent;
import fr.cnes.regards.modules.search.domain.SearchRequest;
import fr.cnes.regards.modules.search.domain.plugin.SearchEngineMappings;

/**
 * Test class
 *
 * @author Sébastien Binda
 *
 */
@TestPropertySource(locations = { "classpath:test.properties" },
        properties = { "spring.jpa.properties.hibernate.default_schema=fem_job" })
public class FemNotifierJobTest extends AbstractFemJobTest {

    @Autowired
    private IJobInfoService jobInfoService;

    @Autowired
    private FemDriverService femDriverService;

    @Autowired
    private IRuntimeTenantResolver tenantResolver;

    @Before
    public void init() throws ModuleException {
        this.doInit();
    }

    @Test
    public void testNotifyJob() throws InterruptedException {
        tenantResolver.forceTenant(getDefaultTenant());
        Mockito.verify(publisher, Mockito.times(0)).publish(recordsCaptor.capture());
        MultiValueMap<String, String> searchParameters = new LinkedMultiValueMap<>();
        SearchRequest searchRequest = new SearchRequest(SearchEngineMappings.LEGACY_PLUGIN_ID,
                                                        null,
                                                        searchParameters,
                                                        null,
                                                        null,
                                                        null);
        femDriverService.scheduleNotification(searchRequest);
        int loop = 0;
        while ((jobInfoService.retrieveJobs(JobStatus.SUCCEEDED).size() == 0) && (loop < 2000)) {
            loop++;
            Thread.sleep(100);
        }
        Mockito.verify(publisher, Mockito.atLeastOnce()).publish(recordsCaptor.capture());
        Optional<List<ISubscribable>> events = recordsCaptor.getAllValues().stream().filter(Objects::nonNull)
                .findFirst();
        Assert.assertTrue(events.isPresent());
        Assert.assertEquals(1000, events.get().size());

        events.get().forEach(e -> {
            FeatureNotificationRequestEvent event = (FeatureNotificationRequestEvent) e;
            Assert.assertNotNull("Invalid null event", event);
            Assert.assertNotNull("Deletion  feature urn is mandatory", event.getUrn());
        });

    }

    @Test
    public void testNotifyJobWithCrit() throws InterruptedException {
        tenantResolver.forceTenant(getDefaultTenant());
        Mockito.verify(publisher, Mockito.times(0)).publish(recordsCaptor.capture());
        MultiValueMap<String, String> searchParameters = new LinkedMultiValueMap<>();
        SearchRequest searchRequest = new SearchRequest(SearchEngineMappings.LEGACY_PLUGIN_ID,
                                                        null,
                                                        searchParameters,
                                                        Sets.newHashSet(datas.get(0).getIpId().toString()),
                                                        null,
                                                        null);

        femDriverService.scheduleNotification(searchRequest);
        int loop = 0;
        while ((jobInfoService.retrieveJobs(JobStatus.SUCCEEDED).size() == 0) && (loop < 2000)) {
            loop++;
            Thread.sleep(100);
        }
        Mockito.verify(publisher, Mockito.atLeastOnce()).publish(recordsCaptor.capture());
        Optional<List<ISubscribable>> events = recordsCaptor.getAllValues().stream().filter(Objects::nonNull)
                .findFirst();
        Assert.assertTrue(events.isPresent());
        Assert.assertEquals(1, events.get().size());

        events.get().forEach(e -> {
            FeatureNotificationRequestEvent event = (FeatureNotificationRequestEvent) e;
            Assert.assertNotNull("Invalid null event", event);
            Assert.assertNotNull("Deletion  feature urn is mandatory", event.getUrn());
        });
    }

}
