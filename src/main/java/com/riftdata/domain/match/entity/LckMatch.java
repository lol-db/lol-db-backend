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
	private Long id;

	/** 경기일시 */
	@Column(name = "match_time", nullable = false)
	private LocalDateTime matchTime;

	/** 세트 수 (bo3 = 3, bo5 = 5) */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "sets", nullable = false)
	private Integer sets;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team1_id", nullable = false)
	private Team team1;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team2_id", nullable = false)
	private Team team2;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "stage_id", nullable = false)
	private Stage stage;

	@Builder
	private LckMatch(LocalDateTime matchTime, Integer sets, Team team1, Team team2, Stage stage) {
		this.matchTime = matchTime;
		this.sets = sets;
		this.team1 = team1;
		this.team2 = team2;
		this.stage = stage;
	}
}
