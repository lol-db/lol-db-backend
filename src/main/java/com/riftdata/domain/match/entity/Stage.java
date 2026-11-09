package com.riftdata.domain.match.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 스테이지 (stage). 기준정보라 PK가 코드값이다. */
@Entity
@Table(name = "stage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stage {

	@Id
	@Column(name = "stage_code", length = 3)
	private String stageCode;

	@Column(name = "stage_name", nullable = false, length = 30)
	private String stageName;

	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "stage_order", nullable = false, unique = true)
	private Integer stageOrder;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	/** 순위표 집계 대상 여부 (정규 라운드 = true) */
	@Column(name = "is_regular", nullable = false)
	private Boolean regular;

	@Builder
	private Stage(String stageCode, String stageName, Integer stageOrder,
			LocalDate startDate, LocalDate endDate, Boolean regular) {
		this.stageCode = stageCode;
		this.stageName = stageName;
		this.stageOrder = stageOrder;
		this.startDate = startDate;
		this.endDate = endDate;
		this.regular = regular;
	}
}
