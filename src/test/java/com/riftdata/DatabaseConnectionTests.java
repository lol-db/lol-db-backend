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
	void connectsToMysql8() {
		String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);

		// 설계 기준은 MySQL 8.0이고, 8.x면 어느 버전이든 통과한다.
		assertThat(version).startsWith("8.");
	}

}
