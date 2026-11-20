package com.riftdata.domain.player.service;

import com.riftdata.domain.player.entity.Player;
import com.riftdata.domain.player.repository.PlayerRepository;
import com.riftdata.global.exception.BusinessException;
import com.riftdata.global.exception.ErrorCode;

/** 선수 조회 API의 입력 검사를 공통으로 처리합니다. 반올림은 global.common.Decimals를 사용합니다. */
public final class PlayerQuerySupport {
	private PlayerQuerySupport() {
	}

	public static void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}

	public static Player requirePlayer(PlayerRepository repository, Long playerId) {
		if (playerId == null || playerId <= 0) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return repository.findById(playerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PLAYER_NOT_FOUND));
	}

	public static String normalizePatch(String patch) {
		if (patch == null || patch.isBlank()) return null;
		String normalized = patch.strip();
		if (normalized.length() > 15 || normalized.chars().anyMatch(Character::isISOControl)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return normalized;
	}

	public static void validateChampionId(Long championId) {
		if (championId != null && championId <= 0) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}
}
