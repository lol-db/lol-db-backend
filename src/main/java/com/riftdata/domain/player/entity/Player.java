package com.riftdata.domain.player.entity;

import com.riftdata.domain.team.entity.Team;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 선수 (player) */
@Entity
@Table(name = "player")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Player {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "player_id")
	private Integer id;

	/** 현재 소속 팀 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	@Column(name = "player_name", nullable = false, length = 30)
	private String playerName;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "main_position", nullable = false, length = 3)
	private Position mainPosition;

	@Column(name = "source_player_id", nullable = false, length = 60, unique = true)
	private String sourcePlayerId;

	@Builder
	private Player(Team team, String playerName, Position mainPosition, String sourcePlayerId) {
		this.team = team;
		this.playerName = playerName;
		this.mainPosition = mainPosition;
		this.sourcePlayerId = sourcePlayerId;
	}

	/** 이적 등으로 소속 팀이 바뀔 때 */
	public void changeTeam(Team team) {
		this.team = team;
	}
}
