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
import java.util.function.Predicate;

/**
 * 헤더가 있는 CSV 파일. 컬럼은 이름으로 찾으므로 원천 파일의 컬럼 순서가 바뀌어도 읽을 수 있다.
 * RFC 4180 따옴표 규칙(따옴표 안의 쉼표·줄바꿈, "" 이스케이프)을 지원한다.
 */
public final class CsvTable {

	private final Map<String, Integer> columns;
	private final List<Row> rows;
	private final int skippedRows;

	private CsvTable(Map<String, Integer> columns, List<Row> rows, int skippedRows) {
		this.columns = columns;
		this.rows = rows;
		this.skippedRows = skippedRows;
	}

	public static CsvTable read(Path file) throws IOException {
		return read(file, row -> true);
	}

	/**
	 * 한 줄씩 읽으면서 filter를 통과한 행만 메모리에 남긴다. 원천 파일이 커도(전 세계 리그 약 12만 행)
	 * 필요한 행(LCK 약 6천 행)만큼만 메모리를 쓴다. 걸러 낸 행 수는 skippedRows()로 알 수 있다.
	 */
	public static CsvTable read(Path file, Predicate<Row> filter) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			List<String> header = nextRecord(reader);
			if (header == null) {
				throw new IOException("CSV가 비어 있습니다: " + file);
			}
			Map<String, Integer> columns = new HashMap<>();
			for (int i = 0; i < header.size(); i++) {
				String name = header.get(i).strip();
				if (i == 0 && name.startsWith("\uFEFF")) {
					name = name.substring(1);
				}
				columns.putIfAbsent(name, i);
			}
			List<Row> rows = new ArrayList<>();
			int skipped = 0;
			int line = 1;
			List<String> values;
			while ((values = nextRecord(reader)) != null) {
				line++;
				if (values.size() == 1 && values.getFirst().isBlank()) {
					continue;
				}
				Row row = new Row(columns, line, values);
				if (filter.test(row)) {
					rows.add(row);
				} else {
					skipped++;
				}
			}
			return new CsvTable(columns, rows, skipped);
		}
	}

	public boolean has(String column) {
		return columns.containsKey(column);
	}

	public List<Row> rows() {
		return rows;
	}

	/** read(file, filter)에서 걸러 낸 행 수 */
	public int skippedRows() {
		return skippedRows;
	}

	/** 레코드 하나(따옴표 안의 줄바꿈 포함)를 읽는다. 파일 끝이면 null */
	private static List<String> nextRecord(BufferedReader reader) throws IOException {
		List<String> record = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		boolean fieldStarted = false;
		boolean any = false;
		int c;
		while ((c = reader.read()) != -1) {
			any = true;
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
				return record;
			} else {
				field.append(ch);
				fieldStarted = true;
			}
		}
		if (!any) {
			return null;
		}
		record.add(field.toString());
		return record;
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
