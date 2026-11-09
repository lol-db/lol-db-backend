package com.riftdata.domain.record.entity;

import com.riftdata.domain.champion.entity.Champion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 밴 (ban). 팀세트기록당 최대 5개. */
@Entity
@Table(name = "ban", uniqueConstraints = {
		@UniqueConstraint(name = "uk_ban_order", columnNames = {"team_set_id", "ban_order"}),
		@UniqueConstraint(name = "uk_ban_champion", columnNames = {"team_set_id", "champion_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ban {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ban_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_set_id", nullable = false)
	private TeamSet teamSet;

	/** 밴 순서 1~5 */
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "ban_order", nullable = false)
	private Integer banOrder;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "champion_id", nullable = false)
	private Champion champion;

	@Builder
	private Ban(TeamSet teamSet, Integer banOrder, Champion champion) {
		this.teamSet = teamSet;
		this.banOrder = banOrder;
		this.champion = champion;
	}
}
