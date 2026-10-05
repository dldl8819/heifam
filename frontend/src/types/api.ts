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
  | 'RANDOM'
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
  // How the two teams would play their series; the races follow the order of homeTeam and awayTeam.
  seriesPlan?: MultiBalanceSeriesPlan | null
}

export type MultiBalanceSeriesGame = {
  gameNumber: number
  raceComposition: RaceComposition
  homeRaces: AssignedRace[] | null
  awayRaces: AssignedRace[] | null
}

export type MultiBalanceSeriesPlan = {
  format: TournamentSeriesFormat
  games: MultiBalanceSeriesGame[]
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

export type ParticipantRaceRequest = {
  playerId: number
  race: AssignedRace
}

export type MatchResultRequest = {
  winnerTeam: TeamSide
  participantRaces?: ParticipantRaceRequest[]
}

export type MatchResultUpdateRequest = MatchResultRequest & {
  raceComposition?: RaceComposition
}

export type ManualMatchCreateRequest = {
  groupId: number
  teamSize: number
  homePlayerIds: number[]
  awayPlayerIds: number[]
  winnerTeam: TeamSide
  raceComposition?: RaceComposition
  participantRaces?: ParticipantRaceRequest[]
  note?: string
}

export type RatingRecalculationRequest = {
  confirm?: boolean
  dryRun?: boolean
}

export type RatingRecalculationPlayerChangeResponse = {
  playerId: number | null
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
  playerId: number | null
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
  playerId: number | null
  nickname: string
  team: MatchTeamSide
  mmr?: number
  assignedRace: AssignedRace | null
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
  canEditRaceComposition: boolean
  racesRecorded: boolean
}

export type MatchHistoryPage = {
  items: RecentMatchItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export type MatchHistoryFilters = {
  fromDate?: string
  toDate?: string
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
  isNew?: boolean
}

export type RankingResponse = RankingItem[]

export type PlayerTier =
  | 'S+'
  | 'S'
  | 'S-'
  | 'A+'
  | 'A'
  | 'A-'
  | 'B+'
  | 'B'
  | 'B-'
  | 'C+'
  | 'C'
  | 'C-'
  | 'D+'
  | 'D'
  | 'D-'

export type PlayerTierStatus = PlayerTier | 'UNASSIGNED'

export type PlayerLifecycleStatus = 'ACTIVE' | 'INACTIVE' | 'WITHDRAWN' | 'ANONYMIZED'

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
  identityHidden?: boolean
  lifecycleStatus?: PlayerLifecycleStatus
  identityRetainedUntil?: string
  chatLeftAt?: string
  chatLeftReason?: string
  chatRejoinedAt?: string
  isOwnPlayer?: boolean
}

export type GroupPlayerLastParticipationResponse = {
  lastPlayedAt: string | null
}

export type GroupDormantPlayerItem = {
  playerId: number
  nickname: string
}

export type GroupPlayerTierBoardItem = Pick<
  PlayerRosterItem,
  'id' | 'nickname' | 'race' | 'tier' | 'liveTier' | 'active'
>

export type GroupPlayerUpdateRequest = {
  nickname?: string
  race?: PlayerRace
  tier?: PlayerTierStatus
  active?: boolean
  chatLeftAt?: string | null
  chatLeftReason?: string | null
  chatRejoinedAt?: string | null
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

export type GroupDashboardMyTeammateStat = {
  nickname: string
  wins: number
  losses: number
  games: number
  winRate: number
  currentWinStreak: number
}

export type GroupDashboardMyTeammateSummary = {
  linked: boolean
  nickname: string | null
  minGames: number
  bestDuos: GroupDashboardMyTeammateStat[]
  frequentTeammates: GroupDashboardMyTeammateStat[]
  streakPartners: GroupDashboardMyTeammateStat[]
}

export type GroupDashboardResponse = {
  currentKFactor: number
  kpiSummary: GroupDashboardKpiSummary
  topRankingPreview: GroupDashboardTopRankingPreviewItem[]
  recentBalancePreview: GroupDashboardRecentBalancePreview | null
  myRaceSummary: GroupDashboardMyRaceSummary
  myGameTypeSummary: GroupDashboardMyGameTypeSummary
  myTeammateSummary: GroupDashboardMyTeammateSummary
}

export type GroupPlayerRaceStat = {
  race: PlayerRace
  wins: number
  losses: number
  games: number
  winRate: number
}

export type GroupPlayerGameTypeStat = {
  gameType: string
  wins: number
  losses: number
  games: number
  winRate: number
}

export type GroupPlayerRaceStatsItem = {
  playerId: number
  nickname: string
  race: PlayerRace
  wins: number
  losses: number
  games: number
  winRate: number
  byRace: GroupPlayerRaceStat[]
  byGameType: GroupPlayerGameTypeStat[]
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
  playerId: number | null
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
  pickedPlayerId: number | null
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
  homeCaptainPlayerId: number | null
  homeCaptainNickname: string
  awayCaptainPlayerId: number | null
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
  matchResultEditor: boolean
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

export type AccessResultEditorListResponse = {
  resultEditors: AccessEmailEntry[]
}

export type OperationAuditLogItem = {
  id: number
  action: string
  actorNickname?: string
  targetType: string
  targetId?: number
  targetLabel?: string
  groupId?: number
  summary: string
  details?: string
  createdAt: string
}

export type OperationAuditLogPage = {
  items: OperationAuditLogItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export type OperationAuditLogFilters = {
  fromDate?: string
  toDate?: string
  actor?: string
  action?: string
  content?: string
  target?: string
}

export type NoticeItem = {
  id: number
  title: string
  content: string
  authorNickname?: string
  createdAt: string
  updatedAt: string
  adminOnly: boolean
}

export type NoticeTitle = {
  title: string
  createdAt: string
}

export type NoticeListItem = {
  id: number
  title: string
  authorNickname?: string
  createdAt: string
  adminOnly: boolean
  read: boolean
  // An edit of this notice was announced again; unread, it is marked as edited in the list.
  revised: boolean
  likeCount: number
  commentCount: number
}

export type NoticeList = {
  notices: NoticeListItem[]
  readCount: number
  unreadCount: number
}

export type NoticeComment = {
  id: number
  authorNickname?: string
  content: string
  createdAt: string
  mine: boolean
  canDelete: boolean
}

export type NoticeDetail = {
  id: number
  title: string
  content: string
  authorNickname?: string
  createdAt: string
  updatedAt: string
  adminOnly: boolean
  likeCount: number
  likedByMe: boolean
  comments: NoticeComment[]
}

export type NoticeCreateRequest = {
  title: string
  content: string
  adminOnly?: boolean
}

export type NoticeUpdateRequest = {
  title: string
  content: string
  adminOnly?: boolean
  // Announce the edit again: the notice turns unread for members and they are notified.
  notify?: boolean
}

export type LedgerExpenseType = 'FIXED' | 'VARIABLE'

export type LedgerIncomeEntry = {
  id: number
  entryDate: string
  category: string
  amount: number
  memo?: string
  authorNickname?: string
  createdAt: string
}

export type LedgerIncomeEntryCreateRequest = {
  entryDate: string
  category: string
  amount: number
  memo?: string
}

export type LedgerIncomeEntryUpdateRequest = LedgerIncomeEntryCreateRequest

export type LedgerExpenseEntry = {
  id: number
  entryDate: string
  expenseType: LedgerExpenseType
  category: string
  target?: string
  amount: number
  memo?: string
  authorNickname?: string
  createdAt: string
}

export type LedgerExpenseEntryCreateRequest = {
  entryDate: string
  expenseType: LedgerExpenseType
  category: string
  target?: string
  amount: number
  memo?: string
}

export type LedgerExpenseEntryUpdateRequest = LedgerExpenseEntryCreateRequest

export type LedgerCategoriesResponse = {
  categories: string[]
}

export type LedgerDashboardBalancePoint = {
  date: string
  change: number
  balance: number
}

export type LedgerDashboardMonthItem = {
  month: string
  income: number
  incomeCount: number
  fixedExpense: number
  variableExpense: number
  totalExpense: number
  expenseCount: number
  serverCostReimbursed: number
  net: number
  endBalance: number
}

export type LedgerDashboardCategoryItem = {
  category: string
  amount: number
  count: number
}

export type LedgerDashboardResponse = {
  asOfDate: string | null
  startingBalanceDate: string | null
  startingBalance: number
  totalIncome: number
  incomeCount: number
  totalFixedExpense: number
  totalVariableExpense: number
  totalExpense: number
  expenseCount: number
  currentBalance: number
  serverCostReimbursed: number
  serverCostPending: number
  serverCostMissingKrwCount: number
  balanceTimeline: LedgerDashboardBalancePoint[]
  months: LedgerDashboardMonthItem[]
  expenseCategories: LedgerDashboardCategoryItem[]
}

export type GroupPlayerTeammateStat = {
  playerId: number
  nickname: string
  wins: number
  losses: number
  games: number
  winRate: number
  currentWinStreak: number
}

export type GroupPlayerTeammateStats = {
  playerId: number
  nickname: string
  wins: number
  losses: number
  games: number
  winRate: number
  teammates: GroupPlayerTeammateStat[]
}

export type LedgerServerCost = {
  id: number
  serviceName: string
  billingMonth: string
  chargedDate: string
  usdAmount: number | null
  krwAmount: number | null
  paidBy: string | null
  reimbursedDate: string | null
  memo: string | null
  authorNickname: string | null
  createdAt: string
}

export type LedgerServerCostRequest = {
  serviceName: string
  billingMonth: string
  chargedDate: string
  usdAmount: number | null
  krwAmount: number | null
  paidBy: string | null
  reimbursedDate: string | null
  memo: string | null
}

export type LedgerImportRowError = {
  rowNumber: number
  reason: string
}

export type LedgerImportResponse = {
  importedCount: number
  skippedRows: LedgerImportRowError[]
}

export type PointReason =
  | 'DAILY_LOGIN'
  | 'MATCH_RESULT'
  | 'MATCH_RESULT_REVERSED'
  | 'ADJUSTMENT'
  | 'PREDICTION_HIT'
  | 'PREDICTION_HIT_REVERSED'
  | 'NOTICE_READ'
  | 'NOTICE_LIKE'
  | 'NOTICE_COMMENT'
  | 'MATCH_CONFIRM'
  | 'MATCH_CONFIRM_REVERSED'

export type PointHistoryItem = {
  reason: PointReason | string
  amount: number
  kstDate: string
  memo: string | null
  createdAt: string
}

export type PointSummaryResponse = {
  balance: number
  dailyLoginEarnedToday: boolean
  dailyLoginPoints: number
  matchResultsToday: number
  matchResultDailyCap: number
  matchResultPoints: number
  predictionPointsToday: number
  predictionHitDailyCap: number
  predictionHitPoints: number
  matchConfirmsToday: number
  matchConfirmDailyCap: number
  matchConfirmPoints: number
  matchConfirmWindowHours: number
  recent: PointHistoryItem[]
}

export type PointRankingEntry = {
  rank: number
  // Opens the entry's monthly history.
  accountId: number | null
  nickname: string | null
  points: number
}

export type PointReasonTotal = {
  reason: PointReason | string
  count: number
  points: number
}

export type PointMonthlyHistory = {
  month: string
  accountId: number
  nickname: string | null
  points: number
  reasons: PointReasonTotal[]
  entries: PointHistoryItem[]
}

export type PointRankingResponse = {
  month: string
  entries: PointRankingEntry[]
}

export type TeamTournamentStatus = 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type TournamentSeriesRound = 'SEMIFINAL' | 'FINAL' | 'THIRD_PLACE'
export type TournamentSeriesFormat = 'BEST_OF_THREE' | 'MIXED_THREE'
// PLAYED: result recorded. NEXT: its match waits for a result. UPCOMING: not set up yet.
// SKIPPED: the series ended before it.
export type TournamentGameStatus = 'PLAYED' | 'NEXT' | 'UPCOMING' | 'SKIPPED'

export type TournamentPlayer = {
  playerId: number | null
  nickname: string | null
  race: string | null
  mmr: number | null
}

export type TournamentTeam = {
  teamId: number
  teamNumber: number
  finalRank: number | null
  totalMmr: number | null
  members: TournamentPlayer[]
}

export type TournamentGamePlayer = {
  playerId: number | null
  nickname: string | null
  assignedRace: AssignedRace | null
}

export type TournamentGame = {
  gameNumber: number
  raceComposition: RaceComposition | null
  status: TournamentGameStatus
  matchId: number | null
  winnerTeam: TeamSide | null
  homePlayers: TournamentGamePlayer[]
  awayPlayers: TournamentGamePlayer[]
}

export type TournamentSeries = {
  seriesId: number
  round: TournamentSeriesRound
  bracketSlot: number
  format: TournamentSeriesFormat
  status: 'IN_PROGRESS' | 'COMPLETED'
  homeTeamNumber: number
  awayTeamNumber: number
  homeWins: number
  awayWins: number
  winnerTeamNumber: number | null
  games: TournamentGame[]
}

export type TeamTournament = {
  tournamentId: number
  status: TeamTournamentStatus
  teamCount: number
  createdAt: string
  finishedAt: string | null
  waitingPlayers: TournamentPlayer[]
  teams: TournamentTeam[]
  series: TournamentSeries[]
}

export type LatestTeamTournamentResponse = {
  tournament: TeamTournament | null
}

export type NotificationItem = {
  id: number
  kind: 'NOTICE' | 'PREDICTION' | string
  title: string
  body: string | null
  link: string | null
  createdAt: string
  read: boolean
}

export type NotificationList = {
  notifications: NotificationItem[]
  unreadCount: number
}

export type PushConfig = {
  enabled: boolean
  publicKey: string | null
}

export type BalanceSeriesStatus = 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

// A series played after a multi-balance; its games read like a tournament series' games.
export type BalanceSeries = {
  seriesId: number
  // Its match in the multi-balance and the team numbers shown there; null for older series.
  matchNumber: number | null
  homeTeamNumber: number | null
  awayTeamNumber: number | null
  status: BalanceSeriesStatus
  format: TournamentSeriesFormat
  teamSize: number
  homeWins: number
  awayWins: number
  winnerTeam: TeamSide | null
  createdAt: string
  finishedAt: string | null
  homePlayers: TournamentPlayer[]
  awayPlayers: TournamentPlayer[]
  games: TournamentGame[]
}

export type BalanceSeriesList = {
  series: BalanceSeries[]
}

export type BalanceSeriesLineup = {
  homePlayerIds: number[]
  awayPlayerIds: number[]
  // Teams that could mix may still play the all-Protoss game best of three.
  format?: TournamentSeriesFormat
}

export type PrizeEventStatus = 'OPEN' | 'CONFIRMED' | 'CANCELLED'

export type PrizeCandidate = {
  rank: number
  pointAccountId: number
  nickname: string | null
  points: number
}

export type PrizeWinner = {
  place: number
  nickname: string | null
  points: number
  prize: string | null
  amount: number
  ledgerLinked: boolean
}

export type PrizeEvent = {
  eventId: number
  title: string
  periodStart: string
  periodEnd: string
  winnerCount: number
  status: PrizeEventStatus
  confirmedAt: string | null
  candidates: PrizeCandidate[]
  winners: PrizeWinner[]
}

export type PrizeEventCreateRequest = {
  title: string
  periodStart: string
  periodEnd: string
  winnerCount: number
}

export type PrizeEventConfirmRequest = {
  paidOn: string | null
  winners: Array<{ pointAccountId: number; prize: string | null; amount: number }>
}

export type TeamScoreEntry = {
  rank: number
  playerId: number
  nickname: string | null
  points: number
  championships: number
  runnerUps: number
  thirdPlaces: number
  tournaments: number
  seriesWins: number
  seriesLosses: number
  seriesWinRate: number | null
  wins: number
  losses: number
  winRate: number | null
}

export type TeamScoreBoard = {
  entries: TeamScoreEntry[]
}

// OPEN: picks taken. CLOSED: waiting for the result. RESOLVED: result in.
export type PredictionState = 'OPEN' | 'CLOSED' | 'RESOLVED'

export type PredictionPlayer = {
  nickname: string | null
  assignedRace: AssignedRace | null
}

export type PredictionMatch = {
  matchId: number
  state: PredictionState
  raceComposition: RaceComposition | null
  seriesGameNumber: number | null
  createdAt: string
  closesAt: string | null
  homePlayers: PredictionPlayer[]
  awayPlayers: PredictionPlayer[]
  myPick: TeamSide | null
  ownMatch: boolean
  homePicks: number | null
  awayPicks: number | null
  // Who picked each side, by nickname (null: no nickname). Null while picks are still taken.
  homePickers: (string | null)[] | null
  awayPickers: (string | null)[] | null
  winnerTeam: TeamSide | null
  hit: boolean | null
  pointsExcluded: boolean
  // The nickname of whoever set the match up; null when unknown.
  createdByNickname: string | null
}

// A balanced 3v3 match the player played, whose result they confirm for a point.
export type MatchConfirmation = {
  matchId: number
  raceComposition: RaceComposition | null
  seriesGameNumber: number | null
  resultRecordedAt: string
  confirmDeadline: string
  homePlayers: PredictionPlayer[]
  awayPlayers: PredictionPlayer[]
  winnerTeam: TeamSide | null
  myTeam: TeamSide | null
  confirmed: boolean
}

export type MatchConfirmationList = {
  matches: MatchConfirmation[]
  confirmedToday: number
  dailyCap: number
  points: number
  windowHours: number
}

export type PredictionBoard = {
  now: string
  windowMinutes: number
  open: PredictionMatch[]
  closed: PredictionMatch[]
  history: PredictionMatch[]
  stats: {
    resolved: number
    hits: number
  }
}
