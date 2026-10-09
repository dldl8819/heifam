export type NavItem = {
  label: string
  href: string
  // With children the item is a menu that opens to show them; its own href is then only the path
  // its pages live under, not somewhere to go.
  children?: NavItem[]
}

