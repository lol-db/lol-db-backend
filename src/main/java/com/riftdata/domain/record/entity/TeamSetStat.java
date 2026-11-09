package com.riftdata.domain.record.entity;

import com.riftdata.domain.match.entity.MatchSet;
import com.riftdata.domain.team.entity.Team;
import com.riftdata.global.common.Side;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 팀세트기록 (team_set_stat). 세트와 팀의 M:N을 푸는 종속 엔티티 (세트당 2행).
 *
 * <p>만들 때는 id에 키를 넣고 연관 엔티티도 같이 넘긴다.
 * <pre>
 * TeamSetStat.builder()
 *     .id(TeamSetStatId.of(set.getId().getMatchId(), set.getSetNo(), team.getId()))
 *     .matchSet(set).team(team)
 *     .side(Side.BLUE).win(true)
 *     ...
 *     .build();
 * </pre>
 */
@Entity
@Table(name = "team_set_stat")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TeamSetStat {

	@EmbeddedId
	private TeamSetStatId id;

	/** 키 값은 id가 관리하고, 아래 두 필드는 조회용이다. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumns({
			@JoinColumn(name = "match_id", referencedColumnName = "match_id", insertable = false, updatable = false),
			@JoinColumn(name = "set_no", referencedColumnName = "set_no", insertable = false, updatable = false)
	})
	private MatchSet matchSet;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_id", insertable = false, updatable = false)
	private Team team;

	/** 진영 BLUE/RED */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "side", nullable = false, length = 4)
	private Side side;

	/** 승리 여부 */
	@Column(name = "is_win", nullable = false)
	private Boolean win;

	/** 첫 킬 */
	@Builder.Default
	@Column(name = "first_blood", nullable = false)
	private Boolean firstBlood = false;

	/** 첫 드래곤 */
	@Builder.Default
	@Column(name = "first_dragon", nullable = false)
	private Boolean firstDragon = false;

	/** 드래곤 전체 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "dragons", nullable = false)
	private Integer dragons = 0;

	/** 화염 드래곤 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "infernals", nullable = false)
	private Integer infernals = 0;

	/** 대지 드래곤 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "mountains", nullable = false)
	private Integer mountains = 0;

	/** 바람 드래곤 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "clouds", nullable = false)
	private Integer clouds = 0;

	/** 바다 드래곤 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "oceans", nullable = false)
	private Integer oceans = 0;

	/** 화학공학 드래곤 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "chemtechs", nullable = false)
	private Integer chemtechs = 0;

	/** 마법공학 드래곤 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "hextechs", nullable = false)
	private Integer hextechs = 0;

	/** 장로 드래곤 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "elders", nullable = false)
	private Integer elders = 0;

	/** 첫 전령 */
	@Builder.Default
	@Column(name = "first_herald", nullable = false)
	private Boolean firstHerald = false;

	/** 전령 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "heralds", nullable = false)
	private Integer heralds = 0;

	/** 공허 유충 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "void_grubs", nullable = false)
	private Integer voidGrubs = 0;

	/** 아타칸 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "atakhans", nullable = false)
	private Integer atakhans = 0;

	/** 첫 바론 */
	@Builder.Default
	@Column(name = "first_baron", nullable = false)
	private Boolean firstBaron = false;

	/** 바론 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "barons", nullable = false)
	private Integer barons = 0;

	/** 첫 타워 */
	@Builder.Default
	@Column(name = "first_tower", nullable = false)
	private Boolean firstTower = false;

	/** 첫 미드 타워 */
	@Builder.Default
	@Column(name = "first_mid_tower", nullable = false)
	private Boolean firstMidTower = false;

	/** 타워 3개 먼저 */
	@Builder.Default
	@Column(name = "first_to_three_towers", nullable = false)
	private Boolean firstToThreeTowers = false;

	/** 파괴한 타워 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "towers", nullable = false)
	private Integer towers = 0;

	/** 포탑 방패 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "turret_plates", nullable = false)
	private Integer turretPlates = 0;

	/** 억제기 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "inhibitors", nullable = false)
	private Integer inhibitors = 0;
}
