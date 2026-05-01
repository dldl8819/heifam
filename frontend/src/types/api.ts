export type TeamSide = 'HOME' | 'AWAY'
export type AssignedRace = 'P' | 'T' | 'Z'
export type PlayerRace = 'P' | 'T' | 'Z' | 'PT' | 'PZ' | 'TZ' | 'PTZ'
export type RaceComposition = 'PP' | 'PT' | 'PZ' | 'PPP' | 'PPT' | 'PPZ' | 'PTZ'
export type MatchTeamSide = TeamSide | 'UNKNOWN'

export type HealthResponse = {
  status: string
  service: string
}

export type BalancePlayerInput = {
  playerId?: number
  name: string
  mmr?: number
  assignedRace?: AssignedRace
}

export type BalancePlayerOption = {
  id: number
  nickname: string
  race: PlayerRace
  currentMmr?: number
  tier?: PlayerTierStatus
}

export type BalanceRequest = {
  groupId?: number
  playerIds?: number[]
  teamSize?: number
  players?: BalancePlayerInput[]
  raceComposition?: RaceComposition
}

export type BalanceResponse = {
  teamSize: number
  homeTeam: BalancePlayerInput[]
  awayTeam: BalancePlayerInput[]
  homeMmr?: number
  awayMmr?: number
  mmrDiff?: number
  expectedHomeWinRate?: number
}

export type MultiBalanceRequest = {
  groupId: number
  playerIds: number[]
  balanceMode?: MultiBalanceMode
  raceComposition?: RaceComposition
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
  homeMmr?: number
  awayMmr?: number
  mmrDiff?: number
  expectedHomeWinRate?: number
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

export type ManualMatchCreateRequest = {
  groupId: number
  teamSize: number
  homePlayerIds: number[]
  awayPlayerIds: number[]
  winnerTeam: TeamSide
  raceComposition?: RaceComposition
  note?: string
}

export type RatingRecalculationRequest = {
  confirm?: boolean
  dryRun?: boolean
}

export type RatingRecalculationPlayerChangeResponse = {
  playerId: number
  nickname: string
  beforeMmr: number
  afterMmr: number
}

export type RatingRecalculationResponse = {
  processedMatches: number
  updatedPlayers: number
  durationMs: number
  status: string
  dryRun: boolean
  averageAbsoluteDeltaDifference: number
  samplePlayerChanges: RatingRecalculationPlayerChangeResponse[]
}

export type MatchConfirmationStatus =
  | 'CREATED'
  | 'REUSED_EXISTING'
  | 'DUPLICATE_REJECTED'
  | string

export type CreateGroupMatchResponse = {
  matchId: number | null
  confirmationStatus: MatchConfirmationStatus
  message: string | null
}

export type MatchResultParticipant = {
  playerId: number
  nickname: string
  team: MatchTeamSide
  assignedRace?: AssignedRace
  mmrBefore?: number
  mmrAfter?: number
  mmrDelta?: number
}

export type MatchResultResponse = {
  matchId: number
  winnerTeam: TeamSide
  kFactor: number
  homeExpectedWinRate?: number
  awayExpectedWinRate?: number
  participants: MatchResultParticipant[]
}

export type RecentMatchPlayer = {
  playerId: number
  nickname: string
  team: MatchTeamSide
  mmr?: number
}

export type RecentMatchItem = {
  matchId: number
  playedAt: string
  status: string | null
  winningTeam: TeamSide | null
  resultRecordedAt: string | null
  resultRecordedByNickname: string | null
  homeRaceComposition: string | null
  awayRaceComposition: string | null
  homeTeam: RecentMatchPlayer[]
  awayTeam: RecentMatchPlayer[]
  homeMmr?: number
  awayMmr?: number
  mmrDiff?: number
}

export type RankingItem = {
  rank: number
  nickname: string
  race: PlayerRace
  tier: PlayerTierStatus
  currentMmr?: number
  wins: number
  losses: number
  games: number
  winRate: number
  streak: string
  last10: string
  mmrDelta?: number
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
  currentMmr?: number
  lastTierSnapshotAt?: string
  lastTierSnapshotMmr?: number
  lastTierSnapshotTier?: PlayerTierStatus
  liveTier?: PlayerTierStatus
  wins: number
  losses: number
  games: number
  active?: boolean
  chatLeftAt?: string
  chatLeftReason?: string
  chatRejoinedAt?: string
  tierChangeAcknowledgedTier?: PlayerTierStatus
  tierChangeAcknowledgedAt?: string
}

export type GroupPlayerMmrUpdateRequest = {
  mmr: number
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
  currentKFactor: number
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
  winnerTeam: TeamSide | null
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
  winnerTeam: TeamSide | null
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
  canViewMmr: boolean
  preferredRace: PlayerRace | null
}

export type AccessEmailEntry = {
  email: string
  nickname: string | null
  canViewMmr: boolean
}

export type AccessAdminListResponse = {
  superAdmins: AccessEmailEntry[]
  admins: AccessEmailEntry[]
}

export type AccessAllowedEmailListResponse = {
  allowedUsers: AccessEmailEntry[]
}
