package com.dotacioneszambrano.inventorio.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DatabaseRepository {

    private final JdbcTemplate jdbc;

    public DatabaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void ping() {
        jdbc.queryForObject("SELECT 1", Integer.class);
    }
}
