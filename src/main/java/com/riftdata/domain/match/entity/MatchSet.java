package com.riftdata.domain.match.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 세트 (match_set). SET이 예약어라 테이블명이 match_set이다.
 * 논리 ERD의 식별자(경기ID + 세트 번호)는 대리키 set_id로 바꾸고 UNIQUE로 남겼다.
 */
@Entity
@Table(name = "match_set", uniqueConstraints =
		@UniqueConstraint(name = "uk_set_match_number", columnNames = {"match_id", "set_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchSet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "set_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "match_id", nullable = false)
	private LckMatch match;

	/** 세트 번호 1~5 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "set_number", nullable = false)
	private Integer setNumber;

	/** 원본게임ID (CSV gameid) */
	@Column(name = "game_id", nullable = false, length = 40, unique = true)
	private String gameId;

	/** 패치버전 */
	@Column(name = "version", nullable = false, length = 15)
	private String version;

	/** 경기시간 (CSV gamelength 초를 시:분:초로) */
	@Column(name = "play_time", nullable = false)
	private LocalTime playTime;

	@Builder
	private MatchSet(LckMatch match, Integer setNumber, String gameId, String version, LocalTime playTime) {
		this.match = match;
		this.setNumber = setNumber;
		this.gameId = gameId;
		this.version = version;
		this.playTime = playTime;
	}

	/** 경기시간을 초로. 분당 지표(DPM, CSM 등) 계산용 */
	public int getPlaySeconds() {
		return playTime.toSecondOfDay();
	}
}
