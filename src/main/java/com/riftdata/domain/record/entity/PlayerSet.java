package com.riftdata.domain.record.entity;

import com.riftdata.domain.champion.entity.Champion;
import com.riftdata.domain.player.entity.Player;
import com.riftdata.global.common.Position;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 선수세트지표 (player_set). 팀세트기록당 5행 (포지션별 1행).
 *
 * <p>논리 ERD의 선수세트시간대별지표를 컬럼으로 합쳤다 (10·15·20·25분 × 골드·경험치·CS·킬·데스·어시스트).
 * 시간대 값은 경기가 그 전에 끝나면 NULL이다. 분당·비율·맞라이너 차이는 뷰(v_player_set)에서 계산한다.
 *
 * <pre>
 * PlayerSet.builder()
 *     .teamSet(teamSet).position(Position.MID).player(player).champion(champion)
 *     .kills(3).deaths(1).assists(7)
 *     ...
 *     .build();
 * </pre>
 */
@Entity
@Table(name = "player_set", uniqueConstraints = {
		@UniqueConstraint(name = "uk_player_set_position", columnNames = {"team_set_id", "position"}),
		@UniqueConstraint(name = "uk_player_set_player", columnNames = {"team_set_id", "player_id"})
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlayerSet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "player_set_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_set_id", nullable = false)
	private TeamSet teamSet;

	/** 포지션 */
	@Enumerated(EnumType.STRING)
	@Column(name = "position", nullable = false)
	private Position position;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id", nullable = false)
	private Player player;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "champion_id", nullable = false)
	private Champion champion;

	/** 킬 수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "kills", nullable = false)
	private Integer kills = 0;

	/** 데스 수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "deaths", nullable = false)
	private Integer deaths = 0;

	/** 어시스트 수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "assists", nullable = false)
	private Integer assists = 0;

	/** 더블킬 수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "double_kills", nullable = false)
	private Integer doubleKills = 0;

	/** 트리플킬 수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "triple_kills", nullable = false)
	private Integer tripleKills = 0;

	/** 쿼드라킬 수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "quadra_kills", nullable = false)
	private Integer quadraKills = 0;

	/** 펜타킬 수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "penta_kills", nullable = false)
	private Integer pentaKills = 0;

	/** 선취점 킬 */
	@Builder.Default
	@Column(name = "first_blood_kill", nullable = false)
	private Boolean firstBloodKill = false;

	/** 선취점 어시스트 */
	@Builder.Default
	@Column(name = "first_blood_assist", nullable = false)
	private Boolean firstBloodAssist = false;

	/** 선취점 데스 */
	@Builder.Default
	@Column(name = "first_blood_death", nullable = false)
	private Boolean firstBloodDeath = false;

	/** 챔피언에게 가한 피해량 */
	@Builder.Default
	@Column(name = "damage_to_champions", nullable = false)
	private Integer damageToChampions = 0;

	/** 받은 피해량 (분당) */
	@Column(name = "damage_taken_pm")
	private Integer damageTakenPm;

	/** 경감한 피해량 (분당) */
	@Column(name = "damage_mitigated_pm")
	private Integer damageMitigatedPm;

	/** 건물에게 가한 피해량 */
	@Column(name = "damage_to_buildings")
	private Integer damageToBuildings;

	/** 시야점수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "vision_points", nullable = false)
	private Integer visionPoints = 0;

	/** 설치한 와드 수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "wards_placed", nullable = false)
	private Integer wardsPlaced = 0;

	/** 제거한 와드 수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "wards_removed", nullable = false)
	private Integer wardsRemoved = 0;

	/** 제어와드 구매 수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "control_wards_bought", nullable = false)
	private Integer controlWardsBought = 0;

	/** 총 골드 */
	@Builder.Default
	@Column(name = "total_golds", nullable = false)
	private Integer totalGolds = 0;

	/** 획득한 골드 */
	@Builder.Default
	@Column(name = "earned_golds", nullable = false)
	private Integer earnedGolds = 0;

	/** 사용한 골드 */
	@Builder.Default
	@Column(name = "gold_spent", nullable = false)
	private Integer goldSpent = 0;

	/** 미니언 처치 수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "minion_kills", nullable = false)
	private Integer minionKills = 0;

	/** 몬스터 처치 수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "monster_kills", nullable = false)
	private Integer monsterKills = 0;

	/** 총 점수 0~100 (유도, B3에서 계산) */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "total_points")
	private Integer totalPoints;

	/** POG 여부 (유도, B3에서 계산) */
	@Builder.Default
	@Column(name = "is_pog", nullable = false)
	private Boolean pog = false;

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

	/** 10분 데스 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "deaths_at_10")
	private Integer deathsAt10;

	/** 10분 어시스트 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "assists_at_10")
	private Integer assistsAt10;

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

	/** 15분 데스 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "deaths_at_15")
	private Integer deathsAt15;

	/** 15분 어시스트 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "assists_at_15")
	private Integer assistsAt15;

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

	/** 20분 데스 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "deaths_at_20")
	private Integer deathsAt20;

	/** 20분 어시스트 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "assists_at_20")
	private Integer assistsAt20;

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

	/** 25분 데스 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "deaths_at_25")
	private Integer deathsAt25;

	/** 25분 어시스트 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "assists_at_25")
	private Integer assistsAt25;

	/** B3 배치: 세트 총 점수와 POG를 계산해 넣는다. */
	public void applyTotalPoints(Integer totalPoints, boolean pog) {
		this.totalPoints = totalPoints;
		this.pog = pog;
	}
}
