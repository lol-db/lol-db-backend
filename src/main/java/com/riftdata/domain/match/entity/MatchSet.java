package com.riftdata.domain.match.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 세트 (match_set). 경기의 종속 엔티티 (식별 관계). */
@Entity
@Table(name = "match_set")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchSet {

	@EmbeddedId
	private MatchSetId id;

	/** match_id 값은 id가 관리하고, 이 필드는 조회용이다. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "match_id", insertable = false, updatable = false)
	private LckMatch match;

	/** CSV gameid */
	@Column(name = "source_game_id", nullable = false, length = 40, unique = true)
	private String sourceGameId;

	@Column(name = "patch", nullable = false, length = 10)
	private String patch;

	/** 경기 시간(초) (CSV gamelength) */
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "duration_sec", nullable = false)
	private Integer durationSec;

	@Builder
	private MatchSet(LckMatch match, Integer setNo, String sourceGameId, String patch, Integer durationSec) {
		this.id = MatchSetId.of(match.getId(), setNo);
		this.match = match;
		this.sourceGameId = sourceGameId;
		this.patch = patch;
		this.durationSec = durationSec;
	}

	public Integer getSetNo() {
		return id.getSetNo();
	}
}
