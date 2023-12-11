/*
 * Copyright 2017-2023 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.dam.plugins.datasources;

import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.IPluginParam;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.framework.utils.plugins.bean.PluginUtilsBean;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.dam.domain.datasources.CrawlingCursor;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test class for dataSource plugin {@link FeatureDatasourcePlugin}
 *
 * @author Sébastien Binda
 **/
@ContextConfiguration(classes = { TestConfiguration.class, PluginUtilsBean.class })
@RunWith(SpringJUnit4ClassRunner.class)
public class FeatureDatasourcePluginTest {

    @Test
    public void test_find_all_with_mock_feature_client()
        throws NotAvailablePluginConfigurationException, DataSourceException {
        // Given
        PluginUtils.setup();
        Set<IPluginParam> parameters = IPluginParam.set(IPluginParam.build(DataSourcePluginConstants.MODEL_NAME_PARAM,
                                                                           "MODEL_NAME"));
        FeatureDatasourcePlugin plugin = PluginUtils.getPlugin(PluginConfiguration.build(FeatureDatasourcePlugin.class,
                                                                                         null,
                                                                                         parameters),
                                                               new ConcurrentHashMap<>());

        OffsetDateTime lastIngestionDate = OffsetDateTime.now().minusHours(1);
        OffsetDateTime lastIngestedProductDate = OffsetDateTime.now().minusHours(10);

        // When
        List<DataObjectFeature> result = plugin.findAll("default",
                                                        new CrawlingCursor(lastIngestedProductDate),
                                                        lastIngestionDate,
                                                        OffsetDateTime.now());

        // Then
        // Results based on mock return see TestConfiguration#featureEntityClient
        Assert.assertEquals(10L, result.size());
        Assert.assertTrue(result.stream().allMatch(dataObjectFeature -> dataObjectFeature.getProviderId() != null));
    }

}
