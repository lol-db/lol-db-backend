package com.riftdata.domain.record.entity;

import com.riftdata.domain.champion.entity.Champion;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 밴 (set_ban). 팀세트기록의 종속 엔티티 (팀당 최대 5개). */
@Entity
@Table(name = "set_ban")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SetBan {

	@EmbeddedId
	private SetBanId id;

	/** 키 값은 id가 관리하고, 이 필드는 조회용이다. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumns({
			@JoinColumn(name = "match_id", referencedColumnName = "match_id", insertable = false, updatable = false),
			@JoinColumn(name = "set_no", referencedColumnName = "set_no", insertable = false, updatable = false),
			@JoinColumn(name = "team_id", referencedColumnName = "team_id", insertable = false, updatable = false)
	})
	private TeamSetStat teamSetStat;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "champion_id", nullable = false)
	private Champion champion;

	@Builder
	private SetBan(TeamSetStat teamSetStat, Integer banOrder, Champion champion) {
		this.id = SetBanId.of(teamSetStat.getId(), banOrder);
		this.teamSetStat = teamSetStat;
		this.champion = champion;
	}
}
