/*
 * Copyright 2017-2018 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.dam.plugins.datasources.utils;

import javax.sql.DataSource;

import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import fr.cnes.regards.modules.accessrights.client.IProjectUsersClient;
import fr.cnes.regards.modules.dam.client.models.IAttributeModelClient;

/**
 *
 * Class PostgreDataSourcePluginTestConfiguration
 *
 * Test Configuration class
 *
 * @author Christophe Mertz
 */
@Configuration
@EnableJpaRepositories(basePackages = { "fr.cnes.regards.modules.dam.plugins.datasources.utils",
        "fr.cnes.regards.modules.dam.dao.models" })
@EnableTransactionManagement
public class PostgreDataSourcePluginTestConfiguration {

    /**
     * The JDBC PostgreSQL driver
     */
    private static final String POSTGRESQL_JDBC_DRIVER = "org.postgresql.Driver";

    @Value("${postgresql.datasource.host}")
    private String dbHost;

    @Value("${postgresql.datasource.port}")
    private String dbPort;

    @Value("${postgresql.datasource.name}")
    private String dbName;

    @Value("${postgresql.datasource.username}")
    private String dbUser;

    @Value("${postgresql.datasource.password}")
    private String dbPassword;

    /**
     *
     * Create the {@link DataSource}
     *
     * @return the {@link DataSource} created
     */
    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRESQL_JDBC_DRIVER);
        dataSource.setUrl(buildUrl());
        dataSource.setUsername(dbUser);
        dataSource.setPassword(dbPassword);
        return dataSource;
    }

    private String buildUrl() {
        return "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
    }

    @Bean
    public IAttributeModelClient attributeModelClient() {
        return Mockito.mock(IAttributeModelClient.class);
    }

    @Bean
    public IProjectUsersClient projectUsersClient() {
        return Mockito.mock(IProjectUsersClient.class);
    }
}
