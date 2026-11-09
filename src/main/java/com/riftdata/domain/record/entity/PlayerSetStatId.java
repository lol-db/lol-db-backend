package com.riftdata.domain.record.entity;

import com.riftdata.global.common.Position;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 선수세트기록 PK: 경기ID + 세트 번호 + 팀ID + 포지션 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "of")
public class PlayerSetStatId implements Serializable {

	@Column(name = "match_id")
	private Integer matchId;

	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "set_no")
	private Integer setNo;

	@Column(name = "team_id")
	private Integer teamId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "position", length = 3)
	private Position position;

	public static PlayerSetStatId of(TeamSetStatId teamSet, Position position) {
		return of(teamSet.getMatchId(), teamSet.getSetNo(), teamSet.getTeamId(), position);
	}
}
