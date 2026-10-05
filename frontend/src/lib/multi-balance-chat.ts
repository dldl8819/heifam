import { formatPercent } from '@/lib/percent'
import type { BalancePlayerInput, MultiBalanceMatch, MultiBalanceResponse } from '@/types/api'

function formatPlayerForChat(player: BalancePlayerInput): string {
  return `${player.name}${player.assignedRace ? `(${player.assignedRace})` : ''}`
}

/**
 * One match of a multi-balance as a line for the game's chat, like the balance page's copy: the
 * two teams by their numbers on the page (match 2 plays teams 3 and 4) and the first team's
 * expected win rate. Names only: no MMR goes into copied text.
 */
export function formatMultiBalanceMatchChatText(match: MultiBalanceMatch, matchIndex: number): string {
  const homeTeamNumber = matchIndex * 2 + 1
  const awayTeamNumber = homeTeamNumber + 1
  const homeTeam = match.homeTeam.map(formatPlayerForChat).join(', ')
  const awayTeam = match.awayTeam.map(formatPlayerForChat).join(', ')
  const homeWinRate = typeof match.expectedHomeWinRate === 'number' ? formatPercent(match.expectedHomeWinRate) : '-'

  return `${homeTeamNumber}팀: ${homeTeam} / ${awayTeamNumber}팀: ${awayTeam} / ${homeTeamNumber}팀 승률 ${homeWinRate}`
}

/** The whole result for a group chat: every match on its own line, then whoever is left waiting. */
export function formatMultiBalanceChatText(result: MultiBalanceResponse): string {
  const lines = result.matches.map(
    (match, matchIndex) => `경기${match.matchNumber} ${formatMultiBalanceMatchChatText(match, matchIndex)}`,
  )
  if (result.waitingPlayers.length > 0) {
    lines.push(`대기: ${result.waitingPlayers.map((player) => player.nickname).join(', ')}`)
  }
  return lines.join('\n')
}
