package com.riftdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DatabaseConnectionTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void connectsToMysql84() {
		String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);

		assertThat(version).startsWith("8.4");
	}

}
