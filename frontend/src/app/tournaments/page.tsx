'use client'

import { TeamTournamentPanel } from '@/components/team-tournament-panel'
import { TierParticipantBoard } from '@/components/tier-participant-board'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { useAdminAuth } from '@/lib/admin-auth'
import { t } from '@/lib/i18n'
import { useMmrVisibility } from '@/lib/mmr-visibility'
import { useParticipantSelection } from '@/lib/use-participant-selection'

const TEMP_GROUP_ID = 1
const MINIMUM_SELECTION_SLOTS = 6

// Team tournaments are for competitions and leagues, so they have their own page apart from multi-balance.
export default function TournamentsPage() {
  const { canViewMmr } = useAdminAuth()
  const { mmrVisible } = useMmrVisibility()
  const showMmr = canViewMmr && mmrVisible
  const selection = useParticipantSelection({
    groupId: TEMP_GROUP_ID,
    showMmr,
    minimumSlots: MINIMUM_SELECTION_SLOTS,
  })

  return (
    <section className="space-y-6">
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <h2 className="text-2xl font-semibold tracking-tight">{t('tournaments.title')}</h2>
        <p className="text-sm text-slate-600 dark:text-slate-300">{t('tournaments.description')}</p>
      </header>

      {selection.playersError && (
        <Alert variant="destructive" appearance="light">
          <AlertIcon icon="destructive">!</AlertIcon>
          <AlertContent>
            <AlertTitle>{t('common.errorPrefix')}</AlertTitle>
            <AlertDescription>{selection.playersError}</AlertDescription>
          </AlertContent>
        </Alert>
      )}

      <TierParticipantBoard
        title={t('tournaments.selection.title')}
        helper={t('tournaments.selection.helper')}
        players={selection.players}
        slots={selection.participantSlots}
        showMmr={showMmr}
        loading={selection.playersLoading}
        selectedCountLabel={t('multiBalance.summary.selectedCount', { count: selection.selectedIds.length })}
        emptyMessage={t('multiBalance.selection.empty')}
        duplicateMessage={t('balance.validation.duplicate')}
        resetLabel={t('multiBalance.selection.reset')}
        inputRefs={selection.participantInputRefs}
        onReset={selection.resetSelection}
        onSlotInputChange={selection.handleSlotInputChange}
        onSlotAutocomplete={selection.handleSlotAutocomplete}
      />

      <TeamTournamentPanel groupId={TEMP_GROUP_ID} selectedPlayerIds={selection.selectedIds} showMmr={showMmr} />
    </section>
  )
}
