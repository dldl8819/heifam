import assert from 'node:assert/strict'
import {
  buildMultiBalanceRequestPayload,
  DEFAULT_MULTI_BALANCE_MODE,
  getMultiBalanceModeLabelKey,
  normalizeMultiBalanceMode,
} from './multi-balance-mode.js'

assert.equal(normalizeMultiBalanceMode(undefined), DEFAULT_MULTI_BALANCE_MODE)
assert.equal(normalizeMultiBalanceMode(null), DEFAULT_MULTI_BALANCE_MODE)
assert.equal(normalizeMultiBalanceMode(''), DEFAULT_MULTI_BALANCE_MODE)

const payload = buildMultiBalanceRequestPayload(1, [1, 2, 3, 4, 5, 6], 'DIVERSITY_FIRST')
assert.deepEqual(payload, {
  groupId: 1,
  playerIds: [1, 2, 3, 4, 5, 6],
  balanceMode: 'DIVERSITY_FIRST',
})

assert.equal(
  getMultiBalanceModeLabelKey('RACE_DISTRIBUTION_FIRST'),
  'multiBalance.mode.options.RACE_DISTRIBUTION_FIRST.label'
)

console.log('multi-balance-mode checks passed')
