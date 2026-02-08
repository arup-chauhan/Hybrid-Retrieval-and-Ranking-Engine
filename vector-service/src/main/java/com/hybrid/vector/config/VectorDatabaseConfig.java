package com.hybrid.vector.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class VectorDatabaseConfig {

    @Bean
    public DataSource dataSource(
            @Value("${postgres.host}") String host,
            @Value("${postgres.port}") int port,
            @Value("${postgres.db}") String db,
            @Value("${postgres.user}") String user,
            @Value("${postgres.password}") String password
    ) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl("jdbc:postgresql://" + host + ":" + port + "/" + db);
        ds.setUsername(user);
        ds.setPassword(password);
        return ds;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
