package com.riftdata.domain.match.entity;

import com.riftdata.domain.team.entity.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 경기 (lck_match). MATCH가 SQL 예약어라 테이블명이 lck_match다. */
@Entity
@Table(name = "lck_match")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LckMatch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "match_id")
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "stage_code", nullable = false)
	private Stage stage;

	/** 경기 일시 (CSV date) */
	@Column(name = "match_at", nullable = false)
	private LocalDateTime matchAt;

	/** 세트 수 (Bo3, Bo5) */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "best_of", nullable = false)
	private Integer bestOf;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team1_id", nullable = false)
	private Team team1;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team2_id", nullable = false)
	private Team team2;

	@Builder
	private LckMatch(Stage stage, LocalDateTime matchAt, Integer bestOf, Team team1, Team team2) {
		this.stage = stage;
		this.matchAt = matchAt;
		this.bestOf = bestOf != null ? bestOf : 3;
		this.team1 = team1;
		this.team2 = team2;
	}
}
