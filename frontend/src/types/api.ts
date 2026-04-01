export type TeamSide = 'HOME' | 'AWAY'
export type PlayerRace = 'P' | 'T' | 'Z' | 'PT' | 'PZ' | 'TZ' | 'R'
export type MatchTeamSide = TeamSide | 'UNKNOWN'

export type HealthResponse = {
  status: string
  service: string
}

export type BalancePlayerInput = {
  playerId?: number
  name: string
  mmr: number
}

export type BalancePlayerOption = {
  id: number
  nickname: string
  race: PlayerRace
  currentMmr: number
  tier?: PlayerTierStatus
}

export type BalanceRequest = {
  groupId?: number
  playerIds?: number[]
  teamSize?: number
  players?: BalancePlayerInput[]
}

export type BalanceResponse = {
  teamSize: number
  homeTeam: BalancePlayerInput[]
  awayTeam: BalancePlayerInput[]
  homeMmr: number
  awayMmr: number
  mmrDiff: number
  expectedHomeWinRate: number
}

export type MultiBalanceRequest = {
  groupId: number
  playerIds: number[]
  balanceMode?: MultiBalanceMode
}

export type MultiBalanceMode =
  | 'MMR_FIRST'
  | 'DIVERSITY_FIRST'
  | 'RACE_DISTRIBUTION_FIRST'

export type MultiBalanceMatch = {
  matchNumber: number
  matchType: '3v3' | '2v2'
  teamSize: number
  homeTeam: BalancePlayerInput[]
  awayTeam: BalancePlayerInput[]
  homeMmr: number
  awayMmr: number
  mmrDiff: number
  expectedHomeWinRate: number
  raceSummary: {
    home: string
    away: string
  }
  penaltySummary: {
    repeatTeammatePenalty: number
    repeatMatchupPenalty: number
    racePenalty: number
  }
}

export type MultiBalanceWaitingPlayer = {
  id: number
  nickname: string
}

export type MultiBalanceResponse = {
  balanceMode: MultiBalanceMode
  totalPlayers: number
  assignedPlayers: number
  waitingPlayers: MultiBalanceWaitingPlayer[]
  matchCount: number
  matches: MultiBalanceMatch[]
}

export type MatchResultRequest = {
  winnerTeam: TeamSide
}

export type MatchResultParticipant = {
  playerId: number
  nickname: string
  team: MatchTeamSide
  mmrBefore: number
  mmrAfter: number
  mmrDelta: number
}

export type MatchResultResponse = {
  matchId: number
  winnerTeam: TeamSide
  kFactor: number
  homeExpectedWinRate: number
  awayExpectedWinRate: number
  participants: MatchResultParticipant[]
}

export type RecentMatchPlayer = {
  playerId: number
  nickname: string
  team: MatchTeamSide
  mmr: number
}

export type RecentMatchItem = {
  matchId: number
  playedAt: string
  winningTeam: TeamSide | null
  resultRecordedAt: string | null
  resultRecordedByNickname: string | null
  homeTeam: RecentMatchPlayer[]
  awayTeam: RecentMatchPlayer[]
  homeMmr: number
  awayMmr: number
  mmrDiff: number
}

export type RankingItem = {
  rank: number
  nickname: string
  race: PlayerRace
  currentMmr: number
  wins: number
  losses: number
  games: number
  winRate: number
  streak: string
  last10: string
  mmrDelta: number
}

export type RankingResponse = RankingItem[]

export type PlayerTier =
  | 'S'
  | 'A+'
  | 'A'
  | 'A-'
  | 'B+'
  | 'B'
  | 'B-'
  | 'C+'
  | 'C'
  | 'C-'

export type PlayerTierStatus = PlayerTier | 'UNASSIGNED'

export type PlayerRosterItem = {
  id: number
  nickname: string
  race: PlayerRace
  tier: PlayerTierStatus
  baseMmr?: number
  baseTier?: PlayerTierStatus
  currentMmr: number
  wins: number
  losses: number
  games: number
}

export type GroupDashboardKpiSummary = {
  totalPlayers: number
  topMmr: number
  averageMmr: number
  totalGames: number
}

export type GroupDashboardTopRankingPreviewItem = {
  rank: number
  nickname: string
  race: PlayerRace
  currentMmr: number
  winRate: number
}

export type GroupDashboardRecentBalanceTeamPlayer = {
  nickname: string
  mmr: number
}

export type GroupDashboardRecentBalancePreview = {
  matchId: number
  homeTeam: GroupDashboardRecentBalanceTeamPlayer[]
  awayTeam: GroupDashboardRecentBalanceTeamPlayer[]
  homeMmr: number
  awayMmr: number
  mmrDiff: number
  createdAt: string
}

export type GroupDashboardMyRaceStat = {
  race: PlayerRace
  wins: number
  losses: number
  games: number
  winRate: number
}

export type GroupDashboardMyRaceSummary = {
  linked: boolean
  nickname: string | null
  wins: number
  losses: number
  games: number
  winRate: number
  byRace: GroupDashboardMyRaceStat[]
}

export type GroupDashboardMyGameTypeStat = {
  gameType: string
  wins: number
  losses: number
  games: number
  winRate: number
}

export type GroupDashboardMyGameTypeSummary = {
  linked: boolean
  nickname: string | null
  wins: number
  losses: number
  games: number
  winRate: number
  byGameType: GroupDashboardMyGameTypeStat[]
}

export type GroupDashboardResponse = {
  kpiSummary: GroupDashboardKpiSummary
  topRankingPreview: GroupDashboardTopRankingPreviewItem[]
  recentBalancePreview: GroupDashboardRecentBalancePreview | null
  myRaceSummary: GroupDashboardMyRaceSummary
  myGameTypeSummary: GroupDashboardMyGameTypeSummary
}

export type CaptainDraftTeam = 'HOME' | 'AWAY' | 'UNASSIGNED'

export type CaptainDraftCreateRequest = {
  title?: string
  participantPlayerIds: number[]
  captainPlayerIds: number[]
  setsPerRound?: number
}

export type CaptainDraftPickRequest = {
  captainPlayerId: number
  pickedPlayerId: number
}

export type CaptainDraftEntryUpdateItem = {
  roundNumber: number
  setNumber: number
  playerId: number | null
}

export type CaptainDraftEntriesUpdateRequest = {
  captainPlayerId: number
  entries: CaptainDraftEntryUpdateItem[]
}

export type CaptainDraftParticipant = {
  playerId: number
  nickname: string
  race: PlayerRace
  team: CaptainDraftTeam
  captain: boolean
  pickOrder: number | null
}

export type CaptainDraftPickLog = {
  pickOrder: number
  captainPlayerId: number | null
  captainNickname: string
  pickedPlayerId: number
  pickedPlayerNickname: string
  team: CaptainDraftTeam
}

export type CaptainDraftEntry = {
  roundNumber: number
  roundCode: string
  setNumber: number
  homePlayerId: number | null
  homePlayerNickname: string | null
  awayPlayerId: number | null
  awayPlayerNickname: string | null
}

export type CaptainDraftResponse = {
  draftId: number
  groupId: number
  title: string
  status: 'DRAFTING' | 'READY' | string
  setsPerRound: number
  participantCount: number
  currentTurnTeam: CaptainDraftTeam
  homeCaptainPlayerId: number
  homeCaptainNickname: string
  awayCaptainPlayerId: number
  awayCaptainNickname: string
  participants: CaptainDraftParticipant[]
  picks: CaptainDraftPickLog[]
  entries: CaptainDraftEntry[]
}

export type AccessRole = 'SUPER_ADMIN' | 'ADMIN' | 'MEMBER' | 'BLOCKED'

export type AccessMeResponse = {
  email: string
  nickname: string | null
  role: AccessRole
  admin: boolean
  superAdmin: boolean
  allowed: boolean
  preferredRace: PlayerRace | null
}

export type AccessEmailEntry = {
  email: string
  nickname: string | null
}

export type AccessAdminListResponse = {
  superAdmins: AccessEmailEntry[]
  admins: AccessEmailEntry[]
}

export type AccessAllowedEmailListResponse = {
  allowedUsers: AccessEmailEntry[]
}
