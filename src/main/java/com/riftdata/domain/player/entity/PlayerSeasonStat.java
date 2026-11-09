package com.riftdata.domain.player.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 선수시즌지표 (player_season_stat). 선수와 1:0..1 식별 관계라 PK가 곧 player_id다.
 * B4 배치가 지우고 다시 계산해서 넣는 유도 테이블이다.
 */
@Entity
@Table(name = "player_season_stat")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerSeasonStat {

	@Id
	@Column(name = "player_id")
	private Integer playerId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id")
	private Player player;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "set_count", nullable = false)
	private Integer setCount;

	/** 0–100. 10세트 미만이면 NULL */
	@Column(name = "overall_score", precision = 5, scale = 2)
	private BigDecimal overallScore;

	@Column(name = "combat_score", precision = 5, scale = 2)
	private BigDecimal combatScore;

	@Column(name = "damage_score", precision = 5, scale = 2)
	private BigDecimal damageScore;

	@Column(name = "lane_score", precision = 5, scale = 2)
	private BigDecimal laneScore;

	@Column(name = "growth_score", precision = 5, scale = 2)
	private BigDecimal growthScore;

	@Column(name = "vision_score", precision = 5, scale = 2)
	private BigDecimal visionScore;

	@Column(name = "survival_score", precision = 5, scale = 2)
	private BigDecimal survivalScore;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "position_rank")
	private Integer positionRank;

	@Column(name = "calculated_at", nullable = false)
	private LocalDateTime calculatedAt;

	@Builder
	private PlayerSeasonStat(Player player, Integer setCount, BigDecimal overallScore,
			BigDecimal combatScore, BigDecimal damageScore, BigDecimal laneScore,
			BigDecimal growthScore, BigDecimal visionScore, BigDecimal survivalScore,
			Integer positionRank, LocalDateTime calculatedAt) {
		this.player = player;
		this.setCount = setCount;
		this.overallScore = overallScore;
		this.combatScore = combatScore;
		this.damageScore = damageScore;
		this.laneScore = laneScore;
		this.growthScore = growthScore;
		this.visionScore = visionScore;
		this.survivalScore = survivalScore;
		this.positionRank = positionRank;
		this.calculatedAt = calculatedAt;
	}
}
