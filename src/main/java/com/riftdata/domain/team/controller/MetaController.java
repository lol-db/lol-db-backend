package com.riftdata.domain.team.controller;

import com.riftdata.domain.team.dto.MetaResponse;
import com.riftdata.domain.team.dto.SearchResponse;
import com.riftdata.domain.team.service.MetaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "공통", description = "시즌 메타(API-01), 통합 검색(API-02)")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MetaController {

	private final MetaService metaService;

	@Operation(summary = "시즌 메타 (API-01)", description = "시즌 정보, 패치 목록, stage 목록, 마지막 갱신 시각. 앱 시작 때 1번 호출")
	@GetMapping("/meta")
	public MetaResponse getMeta() {
		return metaService.getMeta();
	}

	@Operation(summary = "통합 검색 (API-02)",
			description = "선수 닉네임·팀코드·챔피언 이름. 대소문자 무시, 앞부분 일치 우선. q가 비었거나 limit이 1~20 밖이면 400")
	@GetMapping("/search")
	public SearchResponse search(
			@Parameter(description = "검색어 (앞뒤 공백 제거 후 1자 이상). 비우면 400 COMMON_001")
			@RequestParam(required = false) String q,
			@Parameter(description = "종류(선수·팀·챔피언)별 최대 개수, 1~20. 비우면 5")
			@RequestParam(required = false) Integer limit) {
		return metaService.search(q, limit);
	}
}
