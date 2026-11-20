package com.riftdata.batch;

import com.riftdata.batch.CsvTable.Row;
import com.riftdata.global.common.Position;
import com.riftdata.global.common.Side;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Oracle's Elixir 경기 한 개 (원천 12행: 선수 10행 + 팀 2행).
 *
 * @param playedAt 한국 시간 경기 일시
 * @param gameNo   시리즈 안의 세트 번호 (원천 game)
 * @param stage    스테이지 (원천 split·playoffs)
 */
public record OeGame(String gameId, int line, LocalDateTime playedAt, int gameNo, String patch, StageKey stage,
		int lengthSec, TeamSide blue, TeamSide red) {

	public TeamSide side(Side side) {
		return side == Side.BLUE ? blue : red;
	}

	/**
	 * 한 팀의 기록.
	 *
	 * @param teamName 원천 teamname
	 * @param code     팀코드 (loader.team-codes 매핑)
	 * @param teamRow  position = team 행 (오브젝트·밴)
	 * @param players  포지션별 선수 행
	 */
	public record TeamSide(Side side, String teamName, String code, boolean win, Row teamRow,
			Map<Position, Row> players) {
	}

	/**
	 * 스테이지 구분. 정규 시즌은 split 이름(없으면 "정규 시즌"), 플레이오프는 "플레이오프"를 붙인다.
	 */
	public record StageKey(String split, boolean playoffs) {

		public String name() {
			String base = split == null ? "" : split;
			String name = playoffs ? (base.isEmpty() ? "플레이오프" : base + " 플레이오프") : (base.isEmpty() ? "정규 시즌" : base);
			return name.length() > 20 ? name.substring(0, 20) : name;
		}
	}
}
