package com.riftdata.domain.champion.entity;

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

/** 챔피언 (champion) */
@Entity
@Table(name = "champion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Champion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "champion_id")
	private Long id;

	/** CSV champion 값 */
	@Column(name = "name", nullable = false, length = 20, unique = true)
	private String name;

	@Builder
	private Champion(String name) {
		this.name = name;
	}
}
