import { t } from '@/lib/i18n'

/**
 * The patch notes: what changed on the site, a couple of weeks at a time, newest first. They are
 * written here and built into static pages (/updates), so reading them asks nothing of the backend.
 *
 * Every member may read them, and anyone holding the address can fetch a static page, so they say
 * only what any member may know: what members see and do. Nothing about admins, operators or who may
 * do what beyond members (patch-notes.test.ts holds to that), and no MMR, which members do not see.
 */

/** A value that moved: "label: before ⇒ after". */
export type PatchValueChange = {
  label: string
  before: string
  after: string
}

export type PatchChange = string | PatchValueChange

export type PatchEntry = {
  title: string
  tag?: 'new' | 'changed'
  /** Why it changed, in a line or two. */
  note?: string
  changes: PatchChange[]
}

export type PatchSection = {
  title: string
  /** The page the section is about. */
  href?: string
  entries: PatchEntry[]
}

export type PatchNote = {
  /** "26.3": the year, then the patch's number in it. */
  version: string
  /** The day it went out, in Korean time (YYYY-MM-DD). */
  date: string
  /** The days whose changes it gathers, as read ("10월 26일 ~ 11월 8일"). */
  period: string
  summary: string
  comment: string[]
  highlight: { title: string; body: string; href?: string }
  sections: PatchSection[]
  fixes: string[]
  /** What is being made now; it may still change. */
  inProgress: string[]
}

export const PATCH_NOTES: readonly PatchNote[] = [
  {
    version: '26.3',
    date: '2026-11-08',
    period: '10월 26일 ~ 11월 8일',
    summary: '정기 감전 감독 포인트와 이벤트 페이지가 생기고, 정기 감전 화면이 한눈에 보기 쉬워졌어요.',
    comment: [
      '패치 노트 페이지를 새로 열었어요. 그동안 사이트가 꽤 자주 바뀌었는데, 무엇이 바뀌었는지 한곳에서 볼 곳이 없었죠.',
      '앞으로는 바뀐 것과 만들고 있는 것을 2주쯤마다 여기에 정리해 둘게요. 10월부터 바뀐 것은 26.1과 26.2에 시기별로 묶어 두었어요.',
    ],
    highlight: {
      title: '정기 감전 감독 포인트',
      body: '정기 감전에서 감독을 맡으면 3p를 받아요. 첫 세트 결과가 저장될 때 들어오고, 하루 두 번까지 받을 수 있어요.',
      href: '/captain-draft',
    },
    sections: [
      {
        title: '정기 감전',
        href: '/captain-draft',
        entries: [
          {
            title: '감독 포인트',
            tag: 'new',
            note: '팀을 꾸리고 이끄는 감독에게도 포인트를 드리려고 만들었어요.',
            changes: [
              { label: '감독 포인트', before: '없음', after: '3p (첫 세트 결과가 저장될 때, 하루 2번까지)' },
              '정기 감전 기록이 지워지면 받은 포인트도 돌아가요.',
            ],
          },
          {
            title: '티어표에 팀 색깔',
            tag: 'new',
            note: '누가 뽑혔고 누가 남았는지 한눈에 보이도록 했어요.',
            changes: ['청팀·백팀에 뽑힌 선수가 왼쪽 티어표에 팀 색깔로 표시돼요. 색만으로 구분하지 않도록 청/백 글자도 함께 붙어요.'],
          },
          {
            title: '선수별 경기 수',
            tag: 'new',
            note: '라인업을 짤 때 한 사람에게 경기가 몰리지 않게 하려고요.',
            changes: [
              '라인업 입력 위에 팀마다 1인당 몇 경기씩인지와 선수별 경기 수가 보여요.',
              '목표를 채운 선수에게는 체크가, 넘친 선수에게는 "n경기 초과"가 붙어요.',
            ],
          },
          {
            title: '선수 순서',
            tag: 'changed',
            changes: [{ label: '팀 현황의 선수 순서', before: '티어별, 닉네임순', after: '랭킹 페이지와 같은 순서' }],
          },
          {
            title: '새 정기 감전의 입력 방식',
            tag: 'changed',
            changes: [
              { label: '처음 고르는 입력 방식', before: '진행자가 양 팀 입력', after: '감독이 팀별로 제출' },
              '감독 중에 사이트 계정이 연결되지 않은 사람이 있으면 "진행자가 양 팀 입력"을 골라 주세요.',
            ],
          },
        ],
      },
      {
        title: '포인트·이벤트',
        href: '/events',
        entries: [
          {
            title: '이벤트 페이지',
            tag: 'new',
            changes: [
              '상품 이벤트를 이벤트 메뉴에서 볼 수 있어요.',
              '이벤트마다 순위를 정하는 기준이 있어요: 기간 동안 모은 포인트, 뛴 경기 수, 또는 다른 앱에서 센 숫자(예: 채팅 수).',
            ],
          },
          {
            title: '후원 장부의 상품 지출',
            tag: 'changed',
            changes: ['이벤트 상품으로 나간 지출에 받은 사람의 이름이 적혀요.'],
          },
        ],
      },
      {
        title: '공지사항',
        href: '/notices',
        entries: [
          {
            title: '여러 개 고르는 투표',
            tag: 'new',
            changes: ['투표에 따라 선택지를 여러 개 고를 수 있어요. 하나씩 고르고 뺄 수 있어요.'],
          },
        ],
      },
      {
        title: '경기 결과',
        href: '/results',
        entries: [
          {
            title: '최근 결과의 순서',
            tag: 'changed',
            note: '일찍 확정하고 늦게 결과를 넣은 경기가 예전 경기 사이에 섞여 보였어요.',
            changes: [
              { label: '정렬 기준', before: '경기를 확정한 시각', after: '결과를 입력한 시각' },
              '결과를 나중에 고쳐도 순서는 그대로예요.',
            ],
          },
        ],
      },
    ],
    fixes: [
      '정기 감전, 알림, 선수 목록, 공지 목록, 승부 예측의 결과 확인이 서버를 덜 불러서 더 가볍게 열려요.',
      '보안을 강화했어요.',
    ],
    inProgress: [
      '사이트를 더 가볍고 빠르게 만드는 작업을 이어 가고 있어요.',
      '패치 노트는 2주쯤마다 올릴게요.',
    ],
  },
  {
    version: '26.2',
    date: '2026-10-25',
    period: '10월 13일 ~ 10월 25일',
    summary: '정기 감전을 누구나 열 수 있게 되고, 메뉴 출석 포인트와 고대비 모드가 생겼어요.',
    comment: [
      '10월 중순에는 정기 감전을 사이트에서 처음부터 끝까지 치를 수 있게 만들었어요.',
      '팀 게임 세트, 라운드, 감독의 라인업 제출까지 실제로 치르는 방식에 맞췄어요.',
    ],
    highlight: {
      title: '정기 감전, 이제 누구나',
      body: '팸원 누구나 정기 감전을 열고, 팀을 뽑고, 세트 결과를 넣을 수 있어요. 결과마다 누가 넣었는지도 보여요.',
      href: '/captain-draft',
    },
    sections: [
      {
        title: '정기 감전',
        href: '/captain-draft',
        entries: [
          {
            title: '진행자 입력',
            tag: 'changed',
            note: '진행자가 감독을 한 명씩 골라 가며 입력하던 불편을 없앴어요.',
            changes: [
              '진행자가 양 팀의 픽과 엔트리를 한 화면에서 넣어요. 팀 현황에서 청팀·백팀 버튼으로 선수를 데려와요.',
              '픽 순서를 꼭 번갈아 지키지 않아도 돼요. 잘못 뽑은 선수는 되돌릴 수 있어요.',
            ],
          },
          {
            title: '팀 게임 세트',
            tag: 'new',
            note: '실제 정기 감전처럼 3:3, 2:2, 1:1을 섞어 치를 수 있게 했어요.',
            changes: [
              { label: '세트 구성', before: '1:1', after: '3:3, 2:2, 1:1' },
              { label: '라운드 수', before: '4라운드', after: '1~7라운드 (기본 4라운드)' },
              '세트마다 종족(P, T, Z, 랜덤)과 필수 티어 메모를 정할 수 있어요.',
              '종족 구성은 자주 치는 조합만 보여요. 3:3은 PPP, PPT, PPZ, PTZ, PPR, PRR, RRR이에요.',
            ],
          },
          {
            title: '감독 라인업 제출',
            tag: 'new',
            note: '각 팀 감독이 자기 팀 라인업을 직접 정할 수 있게 했어요.',
            changes: [
              '감독이 라운드마다 라인업을 내요. 낸 라인업은 같은 팀에게만 보이고, 두 팀 모두 내면 함께 공개돼요.',
              '에이스 결정전을 붙일 수 있어요.',
            ],
          },
          {
            title: '정기 감전 기록',
            tag: 'new',
            changes: [
              '지난 정기 감전의 날짜, 감독, 참가 인원, 라운드·세트 점수를 기록으로 볼 수 있어요.',
              '정기 감전 결과는 랭킹에 영향을 주지 않아요.',
            ],
          },
          {
            title: '같은 조 반복 알림',
            tag: 'new',
            note: '정기 감전은 여러 티어가 섞여 함께 치는 자리라, 라운드마다 조가 바뀌도록 권하려고요.',
            changes: ['앞 라운드에서 같이 세트를 뛴 두 명 이상이 뒤 라운드에서 또 같이 뛰면 라인업 위에 알려 줘요. 알려 주기만 하고 막지는 않아요.'],
          },
        ],
      },
      {
        title: '상품 추첨',
        href: '/draws',
        entries: [
          {
            title: '누구나 추첨',
            tag: 'changed',
            changes: [
              '팸원 누구나 상품 추첨을 직접 돌리고 저장할 수 있어요. 기록에는 저장한 사람이 남아요.',
              '추첨 방식으로 핀볼과 대포 중에 고를 수 있어요. 대포 뽑기도 상품 추첨 페이지로 옮겨 왔어요.',
              '"최근 정기 감전 참가자 불러오기"로 지난 정기 감전 참가자를 한 번에 체크해요.',
            ],
          },
          {
            title: '더 생생한 핀볼',
            tag: 'changed',
            changes: [
              '핀볼에 풍차와 충격파가 생기고, 마지막 순간은 느린 화면으로 보여요.',
              '당첨자가 몇 명이든 모두 배너에 나와요.',
            ],
          },
        ],
      },
      {
        title: '포인트',
        href: '/points/policy',
        entries: [
          {
            title: '메뉴 출석',
            tag: 'new',
            note: '하루 한 번 들어오는 것보다, 여러 메뉴를 둘러볼수록 포인트가 쌓이게 바꿨어요.',
            changes: [
              { label: '출석 포인트', before: '하루 첫 접속', after: '메뉴 10곳을 하루 처음 열 때마다 1p (하루 최대 10p)' },
              '대상 메뉴: 공지사항, 자유게시판, 익명게시판, 추천영상, 선수 관리, 랭킹, 승부 예측, 파트너스, 경기 결과, 포인트 정책',
            ],
          },
        ],
      },
      {
        title: '공지사항·게시판',
        href: '/boards/free',
        entries: [
          {
            title: '링크 공유 버튼',
            tag: 'new',
            note: '휴대폰 홈 화면에 추가한 사이트에는 주소창이 없어서 링크를 보내기 어려웠어요.',
            changes: ['공지와 게시글에 "링크 공유" 버튼이 생겼어요. 휴대폰에서는 카카오톡 같은 공유 창이 뜨고, 컴퓨터에서는 링크가 복사돼요.'],
          },
        ],
      },
      {
        title: '보기 편하게',
        entries: [
          {
            title: '고대비 모드',
            tag: 'new',
            changes: ['테마 버튼 옆의 대비 버튼으로 글자와 테두리를 더 또렷하게 볼 수 있어요. 기기에서 대비 높이기를 켜 두었다면 처음부터 켜져요.'],
          },
          {
            title: '화면 읽기와 키보드',
            tag: 'changed',
            changes: [
              '페이지마다 이름이 생겨서, 화면 읽기 프로그램이 옮겨 간 페이지를 읽어 줘요.',
              '키보드만으로도 창과 입력란을 쓸 수 있고, 지금 선택된 곳이 잘 보여요.',
              '핀볼 추첨 결과를 화면 읽기 프로그램이 읽어 줘요.',
            ],
          },
        ],
      },
    ],
    fixes: [
      '정기 감전과 상품 추첨을 실제로 써 보며 나온 문제들을 고쳤어요.',
      '사이트 기반(서버와 화면)을 최신 버전으로 올리고 보안을 강화했어요.',
    ],
    inProgress: [],
  },
  {
    version: '26.1',
    date: '2026-10-12',
    period: '10월 1일 ~ 10월 12일',
    summary: '포인트, 승부 예측, 알림, 다중 밸런스가 열리고, 게시판과 상품 추첨이 생겼어요.',
    comment: [
      '10월 초에는 포인트, 승부 예측, 알림, 다중 밸런스를 새로 열었어요.',
      '공지사항을 함께 읽고 이야기할 수 있게 하고, 자유게시판·익명게시판·추천영상도 만들었어요.',
    ],
    highlight: {
      title: '포인트와 승부 예측',
      body: '활동하면 포인트가 쌓이고, 확정된 경기의 이긴 팀을 맞히면 포인트를 받아요. 틀려도 잃는 포인트는 없어요.',
      href: '/points',
    },
    sections: [
      {
        title: '포인트',
        href: '/points',
        entries: [
          {
            title: '포인트',
            tag: 'new',
            changes: [
              '경기 결과 입력, 승부 예측 적중, 공지 읽기·좋아요·댓글 같은 활동으로 포인트가 쌓여요.',
              '포인트 월간 랭킹에서 닉네임을 누르면 그 달에 포인트를 어떻게 모았는지 볼 수 있어요.',
              '자세한 기준은 포인트 정책 페이지에 모두 정리돼 있어요.',
            ],
          },
          {
            title: '결과 확인 포인트',
            tag: 'new',
            note: '자기 경기는 예측할 수 없어서, 경기를 뛴 선수도 포인트를 받을 수 있게 했어요.',
            changes: [
              { label: '결과 확인', before: '없음', after: '1p (3:3 밸런스 경기, 결과가 들어온 뒤 48시간 안, 하루 10경기까지)' },
            ],
          },
          {
            title: '게시판 활동 포인트',
            tag: 'new',
            changes: [
              { label: '자유게시판·추천영상 글쓰기', before: '없음', after: '1p (하루 3개까지)' },
              { label: '다른 사람 글에 댓글·좋아요', before: '없음', after: '각 1p (글마다 한 번, 하루 10개까지)' },
              '익명게시판은 글쓴이가 드러나지 않도록 포인트가 없어요.',
              '글이 지워지면 그 글로 받은 포인트도 돌아가요.',
            ],
          },
        ],
      },
      {
        title: '승부 예측',
        href: '/predictions',
        entries: [
          {
            title: '승부 예측',
            tag: 'new',
            changes: [
              '경기가 확정되면 이길 팀을 예측할 수 있어요. 맞히면 포인트를 받고, 틀려도 잃는 건 없어요.',
              '예측은 경기 확정 3분 뒤나 결과가 들어오면 마감돼요.',
              '자기가 뛰는 경기는 예측할 수 없어요.',
            ],
          },
          {
            title: '누가 어느 팀을 골랐는지',
            tag: 'new',
            changes: ['마감된 뒤에는 팀마다 고른 사람의 닉네임이 보여요. 마감 전에는 숫자도 이름도 보이지 않아요.'],
          },
          {
            title: '경기를 확정한 사람과 시각',
            tag: 'new',
            changes: ['경기마다 누가 언제 확정했는지 보여요.'],
          },
          {
            title: '내가 확정한 경기',
            tag: 'changed',
            note: '경기를 확정하는 사람은 결과를 알고 나서 확정할 수도 있어서, 공정하게 맞췄어요.',
            changes: ['내가 확정한 경기에 낸 내 예측은 맞혀도 포인트가 없어요. 결과를 넣은 사람도 마찬가지예요.'],
          },
        ],
      },
      {
        title: '밸런스 매치·다중 밸런스',
        href: '/balance',
        entries: [
          {
            title: '다중 밸런스',
            tag: 'new',
            changes: [
              '다중 밸런스를 누구나 쓸 수 있어요. 매치마다 팀을 유지한 채 시리즈로 치르고, 먼저 2승 하면 끝나요.',
              '"전체 복사"와 매치별 "복사"로 결과를 채팅에 붙여 넣을 수 있어요.',
              '시리즈마다 지금 입력하는 사람이 보여요.',
            ],
          },
          {
            title: '경기 확정',
            tag: 'changed',
            changes: [
              '"경기 확정" 버튼이 "밸런스 매치 생성" 바로 아래로 옮겨졌어요.',
              '다른 메뉴에 다녀와도 밸런스 화면이 그대로 남아 있어요.',
            ],
          },
          {
            title: '경기 취소',
            tag: 'new',
            changes: ['경기를 확정한 사람은 결과를 넣기 전에 그 경기를 취소할 수 있어요. 승부 예측 페이지에서도 할 수 있어요.'],
          },
          {
            title: '중복 입력 방지',
            tag: 'new',
            changes: ['같은 두 팀의 결과가 5분 안에 또 들어오면 막아요.'],
          },
          {
            title: '티어 배정 전 선수',
            tag: 'changed',
            changes: ['티어 배정이 필요한 선수가 있으면 밸런스 경기 결과를 넣을 수 없어요. 티어가 정해진 뒤에 넣어 주세요.'],
          },
        ],
      },
      {
        title: '선수 관리·랭킹',
        href: '/ranking',
        entries: [
          {
            title: 'D 티어 세분화',
            tag: 'changed',
            changes: [
              { label: 'D 티어', before: 'D', after: 'D+, D, D-' },
              '티어 배정 전 선수는 랭킹 맨 아래에 보여요.',
            ],
          },
          {
            title: '휴면',
            tag: 'new',
            changes: ['휴면 중인 선수는 선수 목록과 랭킹에서 빠지지만, 지난 경기 결과에는 닉네임이 그대로 남아요.'],
          },
          {
            title: '경기 결과의 종족 표시',
            tag: 'new',
            changes: ['PPT, PPZ, PTZ 경기에서 테란·저그로 기록된 선수 옆에 (T), (Z)가 붙어요.'],
          },
        ],
      },
      {
        title: '공지사항·게시판',
        href: '/notices',
        entries: [
          {
            title: '공지사항',
            tag: 'new',
            changes: [
              '공지마다 읽음 표시, 좋아요, 댓글이 생겼어요.',
              '댓글에 답글을 달고, 내 댓글을 고치고, 좋아요를 누를 수 있어요. 최신 댓글이 위에 보여요.',
              '공지 안에 이미지가 들어가요.',
            ],
          },
          {
            title: '공지 투표',
            tag: 'new',
            changes: [
              '공지에 투표가 붙을 수 있어요. 선택지는 공지마다 다르고, 투표에 따라 선택지를 직접 더하거나 이름을 밝히고 투표해요.',
              '투표가 열려 있는 동안에는 바꾸거나 취소할 수 있어요.',
            ],
          },
          {
            title: '자유게시판·익명게시판·추천영상',
            tag: 'new',
            changes: [
              '자유게시판: 누구나 글을 쓰고 댓글과 좋아요를 남겨요.',
              '익명게시판: 다른 팸원에게는 글이 보이지 않고, 글쓴이는 누구에게도 드러나지 않아요.',
              '추천영상: 유튜브 링크를 올리면 글 안에서 바로 재생돼요.',
            ],
          },
          {
            title: '닉변 요청',
            tag: 'new',
            changes: ['새 닉네임을 요청하고 처리 결과와 답변을 확인할 수 있어요. 요청은 한 번에 하나씩이고, 기다리는 동안에는 취소할 수 있어요.'],
          },
          {
            title: '검색',
            tag: 'new',
            changes: ['공지사항과 자유게시판에서 제목과 내용을 검색할 수 있어요. 글마다 읽음·댓글·좋아요·투표 수가 보여요.'],
          },
          {
            title: '댓글은 하루 10개까지',
            tag: 'new',
            changes: ['댓글은 한 사람이 하루 10개까지 쓸 수 있어요. 공지와 게시판을 합친 수이고, 답글도 들어가요.'],
          },
          {
            title: '메뉴 정리',
            tag: 'changed',
            changes: ['공지사항과 게시판은 게시판 메뉴로, 밸런스·정기 감전·추첨은 매치 메뉴로, 포인트 화면은 포인트 메뉴로 모았어요.'],
          },
        ],
      },
      {
        title: '알림',
        entries: [
          {
            title: '알림',
            tag: 'new',
            changes: ['새 공지가 올라오거나 승부 예측이 열리면 종 모양 알림으로 알려 줘요.'],
          },
        ],
      },
      {
        title: '상품 추첨·대포 뽑기',
        href: '/draws',
        entries: [
          {
            title: '핀볼 상품 추첨',
            tag: 'new',
            changes: ['상품을 핀볼로 뽑고, 결과는 누구나 볼 수 있는 기록으로 남아요. 핀볼은 아무도 정하지 않은 값으로 굴러가서 결과를 조작할 수 없어요.'],
          },
          {
            title: '대포 뽑기',
            tag: 'new',
            note: '에이스 결정전 종족을 고를 때 쓰던 프로그램을 사이트로 옮겼어요.',
            changes: ['대포를 쏴서 1부터 정한 숫자 중 하나, 또는 적어 둔 항목 중 하나를 뽑아요.'],
          },
        ],
      },
    ],
    fixes: [
      '휴대폰에서 상단 버튼이 오른쪽에 그대로 있도록 고쳤어요.',
      '다중 밸런스 시리즈 보드에서 1경기가 늘 2경기 왼쪽에 보이도록 고쳤어요.',
      '다른 팸원의 포인트 내역에는 날짜만 보이고 시각은 보이지 않아요.',
      '서버를 최신 버전으로 올리고 보안을 강화했어요.',
    ],
    inProgress: [],
  },
]

/** The address part of a patch note: "26.3" → "26-3". */
export function patchNoteSlug(version: string): string {
  return version.replace(/\./g, '-')
}

export function findPatchNote(slug: string): PatchNote | undefined {
  return PATCH_NOTES.find((note) => patchNoteSlug(note.version) === slug)
}

export function patchNoteTitle(version: string): string {
  return t('updates.patchTitle', { version })
}

/** "2026-11-08" as "2026년 11월 8일"; the date is a Korean day already, so no time zone is involved. */
export function formatPatchDate(date: string): string {
  const [year, month, day] = date.split('-').map(Number)
  return t('updates.date', { year, month, day })
}
