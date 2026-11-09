package com.riftdata.domain.player.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 선수시즌지표 (player_season). 선수와 1:0..1이라 player_id가 UNIQUE다.
 * B4 배치가 지우고 다시 계산해서 넣는 유도 테이블이다. 점수는 0~100 정수.
 */
@Entity
@Table(name = "player_season")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerSeason {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "season_id")
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id", nullable = false, unique = true)
	private Player player;

	/** 출전세트 수 */
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "sets", nullable = false)
	private Integer sets;

	/** 총합 점수. 10세트 미만이면 NULL */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "total_points")
	private Integer totalPoints;

	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "combat_points")
	private Integer combatPoints;

	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "damage_points")
	private Integer damagePoints;

	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "laning_points")
	private Integer laningPoints;

	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "growth_points")
	private Integer growthPoints;

	/** 시야 영역 점수 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "ward_points")
	private Integer wardPoints;

	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "survival_points")
	private Integer survivalPoints;

	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "rank_in_position")
	private Integer rankInPosition;

	@Column(name = "calculated_at", nullable = false)
	private LocalDateTime calculatedAt;

	@Builder
	private PlayerSeason(Player player, Integer sets, Integer totalPoints, Integer combatPoints,
			Integer damagePoints, Integer laningPoints, Integer growthPoints, Integer wardPoints,
			Integer survivalPoints, Integer rankInPosition, LocalDateTime calculatedAt) {
		this.player = player;
		this.sets = sets;
		this.totalPoints = totalPoints;
		this.combatPoints = combatPoints;
		this.damagePoints = damagePoints;
		this.laningPoints = laningPoints;
		this.growthPoints = growthPoints;
		this.wardPoints = wardPoints;
		this.survivalPoints = survivalPoints;
		this.rankInPosition = rankInPosition;
		this.calculatedAt = calculatedAt;
	}
}
