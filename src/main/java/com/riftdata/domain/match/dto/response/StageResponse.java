package com.riftdata.domain.match.dto.response;

import com.riftdata.domain.match.entity.Stage;

/** 스테이지 요약. 명세서의 round(R1/R2/PO) 대신 최종 DB의 stage를 그대로 보낸다. */
public record StageResponse(Long stageId, String name, boolean regular) {

	public static StageResponse from(Stage stage) {
		return new StageResponse(stage.getId(), stage.getName(), stage.getRegular());
	}
}
