package com.riftdata.domain.record.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 팀세트기록 PK: 경기ID + 세트 번호 + 팀ID */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "of")
public class TeamSetStatId implements Serializable {

	@Column(name = "match_id")
	private Integer matchId;

	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "set_no")
	private Integer setNo;

	@Column(name = "team_id")
	private Integer teamId;
}
