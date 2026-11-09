package com.riftdata.domain.record.entity;

import com.riftdata.domain.champion.entity.Champion;
import com.riftdata.domain.player.entity.Player;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 선수세트기록 (player_set_stat). 팀세트기록의 종속 엔티티 (팀당 5행, 포지션별 1행).
 *
 * <p>CSV 원본 값만 저장한다. 분당·비율·차이 지표는 뷰(v_player_set)에서 계산한다.
 * 시간대 값(xx_at_NN)은 경기가 그 전에 끝나면 NULL이다.
 *
 * <pre>
 * PlayerSetStat.builder()
 *     .id(PlayerSetStatId.of(teamSet.getId(), Position.MID))
 *     .teamSetStat(teamSet).player(player).champion(champion)
 *     .kills(3).deaths(1).assists(7)
 *     ...
 *     .build();
 * </pre>
 */
@Entity
@Table(name = "player_set_stat")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlayerSetStat {

	@EmbeddedId
	private PlayerSetStatId id;

	/** 키 값은 id가 관리하고, 이 필드는 조회용이다. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumns({
			@JoinColumn(name = "match_id", referencedColumnName = "match_id", insertable = false, updatable = false),
			@JoinColumn(name = "set_no", referencedColumnName = "set_no", insertable = false, updatable = false),
			@JoinColumn(name = "team_id", referencedColumnName = "team_id", insertable = false, updatable = false)
	})
	private TeamSetStat teamSetStat;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id", nullable = false)
	private Player player;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "champion_id", nullable = false)
	private Champion champion;

	/** 킬 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "kills", nullable = false)
	private Integer kills = 0;

	/** 데스 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "deaths", nullable = false)
	private Integer deaths = 0;

	/** 어시스트 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "assists", nullable = false)
	private Integer assists = 0;

	/** 더블킬 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "double_kills", nullable = false)
	private Integer doubleKills = 0;

	/** 트리플킬 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "triple_kills", nullable = false)
	private Integer tripleKills = 0;

	/** 쿼드라킬 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "quadra_kills", nullable = false)
	private Integer quadraKills = 0;

	/** 펜타킬 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "penta_kills", nullable = false)
	private Integer pentaKills = 0;

	/** 첫 킬을 함 */
	@Builder.Default
	@Column(name = "first_blood_kill", nullable = false)
	private Boolean firstBloodKill = false;

	/** 첫 킬 어시스트 */
	@Builder.Default
	@Column(name = "first_blood_assist", nullable = false)
	private Boolean firstBloodAssist = false;

	/** 첫 킬을 당함 */
	@Builder.Default
	@Column(name = "first_blood_victim", nullable = false)
	private Boolean firstBloodVictim = false;

	/** 챔피언에게 준 피해 */
	@Builder.Default
	@Column(name = "damage_to_champions", nullable = false)
	private Integer damageToChampions = 0;

	/** 분당 받은 피해 */
	@Column(name = "damage_taken_pm", precision = 7, scale = 1)
	private BigDecimal damageTakenPm;

	/** 분당 경감한 피해 */
	@Column(name = "damage_mitigated_pm", precision = 7, scale = 1)
	private BigDecimal damageMitigatedPm;

	/** 건물에 준 피해 */
	@Column(name = "damage_to_buildings")
	private Integer damageToBuildings;

	/** 시야 점수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "vision_score", nullable = false)
	private Integer visionScore = 0;

	/** 와드 설치 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "wards_placed", nullable = false)
	private Integer wardsPlaced = 0;

	/** 와드 제거 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "wards_killed", nullable = false)
	private Integer wardsKilled = 0;

	/** 제어 와드 구매 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "control_wards_bought", nullable = false)
	private Integer controlWardsBought = 0;

	/** 총 골드 */
	@Builder.Default
	@Column(name = "total_gold", nullable = false)
	private Integer totalGold = 0;

	/** 획득 골드 */
	@Builder.Default
	@Column(name = "earned_gold", nullable = false)
	private Integer earnedGold = 0;

	/** 사용 골드 */
	@Builder.Default
	@Column(name = "gold_spent", nullable = false)
	private Integer goldSpent = 0;

	/** 미니언 처치 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "minion_kills", nullable = false)
	private Integer minionKills = 0;

	/** 정글 몬스터 처치 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "monster_kills", nullable = false)
	private Integer monsterKills = 0;

	/** 우리 정글 몬스터 처치 */
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "monster_kills_own")
	private Integer monsterKillsOwn;

	/** 상대 정글 몬스터 처치 */
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "monster_kills_enemy")
	private Integer monsterKillsEnemy;

	/** 10분 골드 */
	@Column(name = "gold_at_10")
	private Integer goldAt10;

	/** 10분 경험치 */
	@Column(name = "xp_at_10")
	private Integer xpAt10;

	/** 10분 CS */
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "cs_at_10")
	private Integer csAt10;

	/** 10분 킬 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "kills_at_10")
	private Integer killsAt10;

	/** 10분 어시스트 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "assists_at_10")
	private Integer assistsAt10;

	/** 10분 데스 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "deaths_at_10")
	private Integer deathsAt10;

	/** 15분 골드 */
	@Column(name = "gold_at_15")
	private Integer goldAt15;

	/** 15분 경험치 */
	@Column(name = "xp_at_15")
	private Integer xpAt15;

	/** 15분 CS */
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "cs_at_15")
	private Integer csAt15;

	/** 15분 킬 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "kills_at_15")
	private Integer killsAt15;

	/** 15분 어시스트 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "assists_at_15")
	private Integer assistsAt15;

	/** 15분 데스 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "deaths_at_15")
	private Integer deathsAt15;

	/** 20분 골드 */
	@Column(name = "gold_at_20")
	private Integer goldAt20;

	/** 20분 경험치 */
	@Column(name = "xp_at_20")
	private Integer xpAt20;

	/** 20분 CS */
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "cs_at_20")
	private Integer csAt20;

	/** 20분 킬 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "kills_at_20")
	private Integer killsAt20;

	/** 20분 어시스트 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "assists_at_20")
	private Integer assistsAt20;

	/** 20분 데스 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "deaths_at_20")
	private Integer deathsAt20;

	/** 25분 골드 */
	@Column(name = "gold_at_25")
	private Integer goldAt25;

	/** 25분 경험치 */
	@Column(name = "xp_at_25")
	private Integer xpAt25;

	/** 25분 CS */
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "cs_at_25")
	private Integer csAt25;

	/** 25분 킬 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "kills_at_25")
	private Integer killsAt25;

	/** 25분 어시스트 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "assists_at_25")
	private Integer assistsAt25;

	/** 25분 데스 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "deaths_at_25")
	private Integer deathsAt25;

	/** 세트 평점 0–10 (유도) */
	@Column(name = "rating", precision = 3, scale = 1)
	private BigDecimal rating;

	/** POG 여부 (유도) */
	@Builder.Default
	@Column(name = "is_pog", nullable = false)
	private Boolean pog = false;

	/** B3 배치: 세트 평점과 POG를 계산해 넣는다. */
	public void applyRating(BigDecimal rating, boolean pog) {
		this.rating = rating;
		this.pog = pog;
	}
}
