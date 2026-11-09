package com.riftdata.domain.record.entity;

import com.riftdata.domain.match.entity.MatchSet;
import com.riftdata.domain.team.entity.Team;
import com.riftdata.global.common.Side;
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
 * 팀세트기록 (team_set). 세트와 팀의 M:N을 푸는 테이블 (세트당 2행).
 * 논리 ERD의 식별자(경기ID + 세트 번호 + 팀ID)는 (set_id, team_id) UNIQUE로 남겼다.
 *
 * <pre>
 * TeamSet.builder()
 *     .matchSet(set).team(team)
 *     .side(Side.BLUE).victory(true)
 *     .dragons(3).towers(9)
 *     .build();
 * </pre>
 */
@Entity
@Table(name = "team_set", uniqueConstraints = {
		@UniqueConstraint(name = "uk_team_set_team", columnNames = {"set_id", "team_id"}),
		@UniqueConstraint(name = "uk_team_set_side", columnNames = {"set_id", "side"})
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TeamSet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "team_set_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "set_id", nullable = false)
	private MatchSet matchSet;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	/** 진영 */
	@Enumerated(EnumType.STRING)
	@Column(name = "side", nullable = false)
	private Side side;

	/** 승리여부 */
	@Column(name = "victory", nullable = false)
	private Boolean victory;

	/** 선취점 */
	@Builder.Default
	@Column(name = "first_blood", nullable = false)
	private Boolean firstBlood = false;

	/** 첫 용 */
	@Builder.Default
	@Column(name = "first_dragon", nullable = false)
	private Boolean firstDragon = false;

	/** 첫 전령 */
	@Builder.Default
	@Column(name = "first_herald", nullable = false)
	private Boolean firstHerald = false;

	/** 첫 바론 */
	@Builder.Default
	@Column(name = "first_baron", nullable = false)
	private Boolean firstBaron = false;

	/** 첫 포탑 */
	@Builder.Default
	@Column(name = "first_tower", nullable = false)
	private Boolean firstTower = false;

	/** 첫 미드 포탑 */
	@Builder.Default
	@Column(name = "first_mid_tower", nullable = false)
	private Boolean firstMidTower = false;

	/** 용 개수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "dragons", nullable = false)
	private Integer dragons = 0;

	/** 화염 용 개수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "fire_dragons", nullable = false)
	private Integer fireDragons = 0;

	/** 물 용 개수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "water_dragons", nullable = false)
	private Integer waterDragons = 0;

	/** 대지 용 개수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "earth_dragons", nullable = false)
	private Integer earthDragons = 0;

	/** 바람 용 개수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "wind_dragons", nullable = false)
	private Integer windDragons = 0;

	/** 마법공학 용 개수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "hextech_dragons", nullable = false)
	private Integer hextechDragons = 0;

	/** 화학공학 용 개수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "chemtech_dragons", nullable = false)
	private Integer chemtechDragons = 0;

	/** 공허유충 개수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "void_grubs", nullable = false)
	private Integer voidGrubs = 0;

	/** 바론 개수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "barons", nullable = false)
	private Integer barons = 0;

	/** 포탑 개수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "towers", nullable = false)
	private Integer towers = 0;

	/** 포탑방패 개수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "tower_plates", nullable = false)
	private Integer towerPlates = 0;

	/** 억제기 개수 */
	@Builder.Default
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "inhibitors", nullable = false)
	private Integer inhibitors = 0;
}
