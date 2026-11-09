package com.riftdata.domain.team.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 팀 (team) */
@Entity
@Table(name = "team")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "team_id")
	private Integer id;

	@Column(name = "team_code", nullable = false, length = 5, unique = true)
	private String teamCode;

	@Column(name = "team_name", nullable = false, length = 50)
	private String teamName;

	@Column(name = "source_team_id", nullable = false, length = 60, unique = true)
	private String sourceTeamId;

	@Builder
	private Team(String teamCode, String teamName, String sourceTeamId) {
		this.teamCode = teamCode;
		this.teamName = teamName;
		this.sourceTeamId = sourceTeamId;
	}
}
