package com.riftdata.domain.match.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 스테이지 (stage). B1에서 초기 입력하는 기준정보다. */
@Entity
@Table(name = "stage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "stage_id")
	private Long id;

	@Column(name = "name", nullable = false, length = 20)
	private String name;

	/** 진행순서 (ERD의 order, 예약어라 이름 변경) */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "stage_order", nullable = false, unique = true)
	private Integer stageOrder;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	/** 정규여부 (true면 순위표 집계 대상) */
	@Column(name = "is_regular", nullable = false)
	private Boolean regular;

	@Builder
	private Stage(String name, Integer stageOrder, LocalDate startDate, LocalDate endDate, Boolean regular) {
		this.name = name;
		this.stageOrder = stageOrder;
		this.startDate = startDate;
		this.endDate = endDate;
		this.regular = regular;
	}
}
