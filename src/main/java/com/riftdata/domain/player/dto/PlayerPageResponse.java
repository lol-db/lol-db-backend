package com.riftdata.domain.player.dto;

import java.util.List;

/** 선수 API에서 사용하는 0부터 시작하는 페이지 응답입니다. */
public record PlayerPageResponse<T>(
		List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <T> PlayerPageResponse<T> from(List<T> rows, int page, int size) {
		long offset = (long) page * size;
		int start = (int) Math.min(offset, rows.size());
		int end = (int) Math.min(offset + size, rows.size());
		return new PlayerPageResponse<>(List.copyOf(rows.subList(start, end)), page, size,
				rows.size(), (int) ((rows.size() + (long) size - 1) / size));
	}
}
