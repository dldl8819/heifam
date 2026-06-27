export type EventStatus = 'scheduled' | 'active' | 'completed'
export type EventStatusFilter = EventStatus | 'all'

export type EventScheduleItem = {
  label: string
  value: string
}

export type EventRecord = {
  slug: string
  title: string
  category: string
  status: EventStatus
  dateLabel: string
  summary: string
  schedule: EventScheduleItem[]
  conditions: string[]
  rules: string[]
  format: string[]
  prizes: string[]
  sponsors: string[]
  teamAssignmentNote: string
  resultNote?: string
}

export const eventStatusLabels: Record<EventStatusFilter, string> = {
  all: '전체',
  scheduled: '예정',
  active: '진행 중',
  completed: '종료',
}

export const events: EventRecord[] = [
  {
    slug: 'public-3v3-win-streak-2026-06-27',
    title: '공방 3:3 연승 미션',
    category: '공방 이벤트',
    status: 'scheduled',
    dateLabel: '2026.06.27 토요일',
    summary:
      '12명 이상 모집되면 3명씩 4개 팀을 편성하고, 공방 3:3에서 가장 높은 연승 기록으로 순위를 정합니다.',
    schedule: [
      { label: '참가 모집 마감', value: '2026.06.27 18:00' },
      { label: '팀 뽑기', value: '2026.06.27 18:30' },
      { label: '게임 진행', value: '2026.06.27 19:30 - 21:30' },
    ],
    conditions: ['참가자 12명 이상 모집 시 진행', '팀 구성은 랜덤 편성'],
    rules: [
      'B- 티어 이상은 종족 랜덤으로 참가합니다.',
      'B- 티어 미만은 종족을 선택할 수 있습니다.',
      '모든 참가자는 0승 0패 아이디로 시작합니다.',
    ],
    format: [
      '공방 3:3으로 진행합니다.',
      '패배 시 해당 아이디는 종료하고 새로운 0승 0패 아이디로 다시 시작합니다.',
      '이벤트 종료 시 가장 높은 연승 기록으로 순위를 결정합니다.',
    ],
    prizes: [
      '1등: 각 3만원 상품권',
      '2등: 각 2만원 상품권',
      '3등: 각 1만원 상품권',
      '배달의민족, 신세계, 기타 희망 상품권 중 선택',
    ],
    sponsors: ['바다', '웃음'],
    teamAssignmentNote:
      '참가자 확정 후 Hei 다중 밸런스의 랜덤 편성 모드로 3명씩 4개 팀을 나눕니다.',
  },
]

export function getEventBySlug(slug: string): EventRecord | undefined {
  return events.find((event) => event.slug === slug)
}

export function filterEvents(status: EventStatusFilter): EventRecord[] {
  if (status === 'all') {
    return events
  }

  return events.filter((event) => event.status === status)
}
