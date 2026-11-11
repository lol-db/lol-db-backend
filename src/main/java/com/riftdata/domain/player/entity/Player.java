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

/** 선수 (player) */
@Entity
@Table(name = "player")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Player {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "player_id")
	private Long id;

	/** 현재 소속 팀 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	/** 프로게이머 닉네임 (CSV playername) */
	@Column(name = "nickname", nullable = false, length = 15, unique = true)
	private String nickname;

	/** 주포지션 */
	@Enumerated(EnumType.STRING)
	@Column(name = "position", nullable = false)
	private Position position;

	/** 실명. CSV에 없어서 직접 입력하고, 없으면 NULL */
	@Column(name = "name", length = 15)
	private String name;

	@Builder
	private Player(Team team, String nickname, Position position, String name) {
		this.team = team;
		this.nickname = nickname;
		this.position = position;
		this.name = name;
	}

	/** 이적 등으로 소속 팀이 바뀔 때 (B2 CSV 적재) */
	public void changeTeam(Team team) {
		this.team = team;
	}

	public void changeName(String name) {
		this.name = name;
	}
}
