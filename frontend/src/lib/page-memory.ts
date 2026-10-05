/**
 * What a page holds while the member moves between menus, such as the players picked for a
 * balance and the teams made from them. It is kept in memory only: it names players and can hold
 * MMR, so nothing is written to browser storage. A reload or closing the tab clears it, and so
 * does signing out or another account signing in.
 */
const memory = new Map<string, unknown>()
let owner: string | null = null

export function recallPageState<T>(key: string): T | null {
  return (memory.get(key) as T | undefined) ?? null
}

export function rememberPageState<T>(key: string, state: T): void {
  memory.set(key, state)
}

export function forgetAllPageState(): void {
  memory.clear()
  owner = null
}

/**
 * Called with the signed-in account once it is known. What one account left behind is never shown
 * to another; a moment without an account (a session being refreshed) keeps it.
 */
export function claimPageMemory(account: string | null): void {
  if (account === null || account === owner) {
    return
  }
  if (owner !== null) {
    memory.clear()
  }
  owner = account
}
