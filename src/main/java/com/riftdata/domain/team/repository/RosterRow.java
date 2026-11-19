package com.riftdata.domain.team.repository;

import com.riftdata.domain.player.entity.Player;
import com.riftdata.domain.player.entity.PlayerSeason;

/** 로스터 한 줄. season은 시즌 지표를 아직 계산하지 않았으면 null이다. */
public record RosterRow(Player player, PlayerSeason season) {
}
