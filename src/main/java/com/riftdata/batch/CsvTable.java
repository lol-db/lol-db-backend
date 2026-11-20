package com.riftdata.batch;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 헤더가 있는 CSV 파일. 컬럼은 이름으로 찾으므로 원천 파일의 컬럼 순서가 바뀌어도 읽을 수 있다.
 * RFC 4180 따옴표 규칙(따옴표 안의 쉼표·줄바꿈, "" 이스케이프)을 지원한다.
 */
public final class CsvTable {

	private final Map<String, Integer> columns;
	private final List<Row> rows;

	private CsvTable(Map<String, Integer> columns, List<Row> rows) {
		this.columns = columns;
		this.rows = rows;
	}

	public static CsvTable read(Path file) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			List<List<String>> records = parse(reader);
			if (records.isEmpty()) {
				throw new IOException("CSV가 비어 있습니다: " + file);
			}
			List<String> header = records.getFirst();
			Map<String, Integer> columns = new HashMap<>();
			for (int i = 0; i < header.size(); i++) {
				String name = header.get(i).strip();
				if (i == 0 && name.startsWith("﻿")) {
					name = name.substring(1);
				}
				columns.putIfAbsent(name, i);
			}
			List<Row> rows = new ArrayList<>();
			for (int i = 1; i < records.size(); i++) {
				List<String> values = records.get(i);
				if (values.size() == 1 && values.getFirst().isBlank()) {
					continue;
				}
				rows.add(new Row(columns, i + 1, values));
			}
			return new CsvTable(columns, rows);
		}
	}

	public boolean has(String column) {
		return columns.containsKey(column);
	}

	public List<Row> rows() {
		return rows;
	}

	private static List<List<String>> parse(BufferedReader reader) throws IOException {
		List<List<String>> records = new ArrayList<>();
		List<String> record = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		boolean fieldStarted = false;
		int c;
		while ((c = reader.read()) != -1) {
			char ch = (char) c;
			if (quoted) {
				if (ch == '"') {
					reader.mark(1);
					int next = reader.read();
					if (next == '"') {
						field.append('"');
					} else {
						quoted = false;
						if (next != -1) reader.reset();
					}
				} else {
					field.append(ch);
				}
			} else if (ch == '"' && field.isEmpty() && !fieldStarted) {
				quoted = true;
				fieldStarted = true;
			} else if (ch == ',') {
				record.add(field.toString());
				field.setLength(0);
				fieldStarted = false;
			} else if (ch == '\n' || ch == '\r') {
				if (ch == '\r') {
					reader.mark(1);
					if (reader.read() != '\n') reader.reset();
				}
				record.add(field.toString());
				records.add(record);
				record = new ArrayList<>();
				field.setLength(0);
				fieldStarted = false;
			} else {
				field.append(ch);
				fieldStarted = true;
			}
		}
		if (fieldStarted || !field.isEmpty() || !record.isEmpty()) {
			record.add(field.toString());
			records.add(record);
		}
		return records;
	}

	/** CSV 한 줄. line은 파일의 줄 번호(헤더 = 1)이고, 오류 메시지에 쓴다. */
	public static final class Row {
		private final Map<String, Integer> columns;
		private final int line;
		private final List<String> values;

		private Row(Map<String, Integer> columns, int line, List<String> values) {
			this.columns = columns;
			this.line = line;
			this.values = values;
		}

		public int line() {
			return line;
		}

		/** 앞뒤 공백을 지운 값. 컬럼이 없거나 빈 값이면 null */
		public String text(String column) {
			Integer index = columns.get(column);
			if (index == null || index >= values.size()) return null;
			String value = values.get(index).strip();
			return value.isEmpty() ? null : value;
		}

		/** 정수 값. 원천에 "1234.0"처럼 소수로 들어 있어도 반올림해서 읽는다. 빈 값이면 null */
		public Integer integer(String column) {
			String value = text(column);
			if (value == null) return null;
			try {
				return (int) Math.round(Double.parseDouble(value));
			} catch (NumberFormatException e) {
				throw new CsvValueException(line, column, value);
			}
		}

		/** 1/0 값. 빈 값이면 null */
		public Boolean flag(String column) {
			Integer value = integer(column);
			return value == null ? null : value != 0;
		}
	}

	/** 숫자여야 하는 칸에 숫자가 아닌 값이 있음 */
	public static class CsvValueException extends RuntimeException {
		public CsvValueException(int line, String column, String value) {
			super(line + "행 " + column + ": 숫자가 아닙니다 (" + value + ")");
		}
	}
}
