import Image from 'next/image'

type Promotion = {
  title: string
  eyebrow: string
  description: string
  imageSrc: string
  imageAlt: string
  imageHref?: string
  imageFit?: 'cover' | 'contain'
  highlights: string[]
  note: string
  href?: string
  ctaLabel?: string
  secondaryHref?: string
  secondaryCtaLabel?: string
  orderHref?: string
  orderCtaLabel?: string
  qrImageSrc?: string
  qrImageAlt?: string
}

const promotions: Promotion[] = [
  {
    title: '가이드런프로젝트',
    eyebrow: '러닝 프로젝트',
    description: '시각장애 러너와 가이드러너가 함께 달리는 러닝 모임입니다.',
    imageSrc: '/promotions/guiderun-project-logo.png',
    imageAlt: '가이드런프로젝트 로고',
    imageFit: 'contain',
    highlights: ['시각장애 러너 동행', '가이드러너 참여 가능', '함께 달리는 프로젝트'],
    note: '참여 방법과 활동 안내는 프로젝트 소개와 신청 페이지에서 확인해 주세요.',
    href: 'https://about.guiderun.org/',
    ctaLabel: '프로젝트 소개',
    orderHref: 'https://guiderun.org/intro',
    orderCtaLabel: '참여 신청',
  },
  {
    title: '창원 솜솜 장수돼지',
    eyebrow: '고기 맛집',
    description: '창원 진해 숙성 고기와 고기 주문 상담 가능',
    imageSrc: '/promotions/jangsudaeji-photo.png',
    imageAlt: '창원 솜솜 장수돼지 매장 외관',
    highlights: ['경남 창원시 진해구 이동로 52', '돼지: 오겹·가브리·항정·뒷통·흑돼지', '소고기 전 부위 주문 가능'],
    note: '돼지고기와 소고기 주문은 주문 폼에서 희망 부위와 수량을 남겨 주세요.',
    href: 'https://naver.me/xdp0ZI0M',
    ctaLabel: '네이버 지도 보기',
    orderHref: 'https://forms.gle/Dy1SMFnFRmTN4hxFA',
    orderCtaLabel: '고기 주문',
    qrImageSrc: '/promotions/jangsudaeji-order-qr.jpg',
    qrImageAlt: '장수돼지 고기 주문 QR 코드',
  },
  {
    title: '경성참기름집',
    eyebrow: '국산 참기름',
    description: '스마트스토어에서 주문 가능한 국산 참기름',
    imageSrc: '/promotions/gyeongseong-sesame-oil-photo.png',
    imageAlt: '경성참기름집 매장 사진',
    imageFit: 'contain',
    highlights: ['서울 서대문구 연희동 712-31', '기름 제조 체험', '저온압착'],
    note: '주문 가능 상품과 배송 조건은 스마트스토어에서 확인해 주세요.',
    href: 'https://naver.me/FtTtMiD0',
    ctaLabel: '네이버 지도 보기',
    orderHref: 'https://smartstore.naver.com/kssesameoil1983',
    orderCtaLabel: '스마트스토어 주문',
    qrImageSrc: '/promotions/gyeongseong-sesame-oil-order-qr.jpg',
    qrImageAlt: '경성참기름집 주문 QR 코드',
  },
  {
    title: '목포 정은 디스커버리',
    eyebrow: 'Discovery',
    description: '헤이 팸원이면 택배비 무료',
    imageSrc: '/promotions/mokpo-discovery-photo.png',
    imageAlt: '목포 정은 디스커버리 매장 외관',
    highlights: ['전남 목포시 상동 1012-6', '목포 방문 시 추천', '전국 택배 가능'],
    note: '방문 전 영업 여부는 지도 앱 또는 매장 안내를 확인해 주세요.',
    href: 'https://naver.me/xQJPxoD8',
    ctaLabel: '네이버 지도 보기',
    orderHref: 'https://forms.gle/T1TPx6YWf4xzwWiu5',
    orderCtaLabel: '디스커버리 주문',
    qrImageSrc: '/promotions/mokpo-discovery-order-qr.jpg',
    qrImageAlt: '디스커버리 주문 QR 코드',
  },
  {
    title: '로카 대운각',
    eyebrow: '중화요리',
    description: '경기 양주 광적면에 있는 전통 중화요리 전문점',
    imageSrc: '/promotions/daewungak-photo.png',
    imageAlt: '로카 대운각 매장 외관',
    highlights: ['경기 양주시 광적면 가래비길 181', '전통 중화요리 전문점', '양주 방문 시 추천'],
    note: '방문 전 영업 여부는 지도 앱 또는 매장 안내를 확인해 주세요.',
    href: 'https://naver.me/GbAypLnG',
    ctaLabel: '네이버 지도 보기',
  },
  {
    title: '김해 창민 블루베리',
    eyebrow: '판매 종료',
    description: '장마 영향으로 이번 시즌 블루베리 주문 접수를 종료했습니다.',
    imageSrc: '/promotions/blueberry-card.png',
    imageAlt: '김해 창민 블루베리 할인 안내 배너',
    highlights: ['주문 접수 마감', '김해 대동농장', '다음 판매 일정 추후 안내'],
    note: '기존 주문 건은 판매자 안내에 따라 확인해 주세요.',
  },
]

export default function AdsPage() {
  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-emerald-100 bg-white px-5 py-6 shadow-sm sm:px-6">
        <p className="text-xs font-semibold uppercase tracking-[0.2em] text-emerald-700">
          Hei`Fam Local
        </p>
        <div className="mt-3 max-w-3xl space-y-2">
          <h2 className="text-3xl font-bold tracking-tight text-slate-950">파트너스</h2>
          <p className="text-sm leading-6 text-slate-600">
            Hei`Fam과 연결된 가게, 상품, 프로젝트를 소개하는 공개 파트너스 공간입니다.
          </p>
        </div>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white px-5 py-4 shadow-sm sm:px-6">
        <p className="text-sm font-semibold text-slate-950">광고 문의는 운영진에게 연락해 주세요.</p>
        <p className="mt-1 text-sm leading-6 text-slate-600">
          가게, 상품, 프로젝트 홍보를 함께 진행해드립니다.
        </p>
        <a
          href="https://www.youtube.com/@Hei-minsik"
          target="_blank"
          rel="noreferrer"
          className="mt-3 inline-flex rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700 transition-colors hover:border-red-500 hover:bg-red-100"
        >
          유튜브 채널 보기
        </a>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {promotions.map((promotion) => (
          <article
            key={promotion.title}
            className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm"
          >
            {promotion.imageHref ? (
              <a
                href={promotion.imageHref}
                target="_blank"
                rel="noreferrer"
                aria-label={`${promotion.title} 상세 이미지 보기`}
                className="relative block aspect-[16/9] w-full bg-slate-100"
              >
                <Image
                  src={promotion.imageSrc}
                  alt={promotion.imageAlt}
                  fill
                  className={promotion.imageFit === 'contain' ? 'object-contain' : 'object-cover'}
                  sizes="(min-width: 1024px) 50vw, 100vw"
                  priority={promotion.title === promotions[0].title}
                />
              </a>
            ) : (
              <div className="relative aspect-[16/9] w-full bg-slate-100">
                <Image
                  src={promotion.imageSrc}
                  alt={promotion.imageAlt}
                  fill
                  className={promotion.imageFit === 'contain' ? 'object-contain' : 'object-cover'}
                  sizes="(min-width: 1024px) 50vw, 100vw"
                  priority={promotion.title === promotions[0].title}
                />
              </div>
            )}
            <div className="space-y-4 p-5">
              <div className="space-y-2">
                <span className="inline-flex rounded-md bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700">
                  {promotion.eyebrow}
                </span>
                {promotion.href ? (
                  <h3 className="text-xl font-bold text-slate-950">
                    <a
                      href={promotion.href}
                      target="_blank"
                      rel="noreferrer"
                      className="transition-colors hover:text-emerald-700"
                    >
                      {promotion.title}
                    </a>
                  </h3>
                ) : (
                  <h3 className="text-xl font-bold text-slate-950">{promotion.title}</h3>
                )}
                <p className="text-sm leading-6 text-slate-600">{promotion.description}</p>
              </div>

              <ul className="grid gap-2 text-sm text-slate-700 sm:grid-cols-3 lg:grid-cols-1 xl:grid-cols-3">
                {promotion.highlights.map((highlight) => (
                  <li
                    key={highlight}
                    className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 font-medium"
                  >
                    {highlight}
                  </li>
                ))}
              </ul>

              <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-800">
                {promotion.note}
              </p>

              {promotion.qrImageSrc && promotion.qrImageAlt && promotion.orderHref && (
                <a
                  href={promotion.orderHref}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex flex-col items-center gap-3 rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm font-semibold text-emerald-800 transition-colors hover:border-emerald-500 hover:bg-emerald-100 sm:flex-row"
                >
                  <span className="relative h-36 w-36 shrink-0 overflow-hidden rounded-md bg-white">
                    <Image
                      src={promotion.qrImageSrc}
                      alt={promotion.qrImageAlt}
                      fill
                      unoptimized
                      className="object-contain"
                      sizes="144px"
                    />
                  </span>
                  <span>QR로 주문서 열기</span>
                </a>
              )}

              {(promotion.href || promotion.secondaryHref || promotion.orderHref) && (
                <div className="flex flex-wrap gap-2">
                  {promotion.href && promotion.ctaLabel && (
                    <a
                      href={promotion.href}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-slate-800"
                    >
                      {promotion.ctaLabel}
                    </a>
                  )}
                  {promotion.secondaryHref && promotion.secondaryCtaLabel && (
                    <a
                      href={promotion.secondaryHref}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-800 transition-colors hover:border-slate-900 hover:bg-slate-50"
                    >
                      {promotion.secondaryCtaLabel}
                    </a>
                  )}
                  {promotion.orderHref && promotion.orderCtaLabel && (
                    <a
                      href={promotion.orderHref}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex rounded-md bg-emerald-700 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-emerald-800"
                    >
                      {promotion.orderCtaLabel}
                    </a>
                  )}
                </div>
              )}
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}
