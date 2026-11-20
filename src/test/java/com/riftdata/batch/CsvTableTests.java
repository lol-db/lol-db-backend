package com.riftdata.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvTableTests {

	@TempDir Path dir;

	@Test
	@DisplayName("헤더 이름으로 읽고, 따옴표 안의 쉼표·따옴표·줄바꿈과 BOM·CRLF를 처리한다")
	void parsesQuotedFields() throws Exception {
		Path file = dir.resolve("a.csv");
		Files.writeString(file, "﻿name,team,kills,note\r\n"
				+ "\"Faker\",\"T1, Korea\",3,\"say \"\"hi\"\"\"\r\n"
				+ "Zeus,HLE,,\"two\nlines\"\r\n"
				+ "\r\n");
		CsvTable table = CsvTable.read(file);

		assertThat(table.has("name")).isTrue();
		assertThat(table.rows()).hasSize(2);
		CsvTable.Row first = table.rows().get(0);
		assertThat(first.line()).isEqualTo(2);
		assertThat(first.text("team")).isEqualTo("T1, Korea");
		assertThat(first.text("note")).isEqualTo("say \"hi\"");
		assertThat(first.integer("kills")).isEqualTo(3);
		CsvTable.Row second = table.rows().get(1);
		assertThat(second.integer("kills")).isNull();
		assertThat(second.text("note")).isEqualTo("two\nlines");
		assertThat(second.text("missing")).isNull();
	}

	@Test
	@DisplayName("정수 칸의 소수는 반올림하고, 숫자가 아니면 줄 번호와 함께 알린다")
	void parsesNumbers() throws Exception {
		Path file = dir.resolve("b.csv");
		Files.writeString(file, "a,b,c\n612.4,1,x\n");
		CsvTable.Row row = CsvTable.read(file).rows().getFirst();
		assertThat(row.integer("a")).isEqualTo(612);
		assertThat(row.flag("b")).isTrue();
		assertThatThrownBy(() -> row.integer("c")).hasMessageContaining("2행 c");
	}
}
