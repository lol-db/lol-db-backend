package com.riftdata.domain.match.entity;

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

/** 세트 PK: 경기ID + 세트 번호 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "of")
public class MatchSetId implements Serializable {

	@Column(name = "match_id")
	private Integer matchId;

	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "set_no")
	private Integer setNo;
}
