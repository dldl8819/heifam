export type NavItem = {
  label: string
  href: string
  // With children the item is a menu that opens to show them. Its own href is never a link: it
  // names the menu, and when its pages share a path it is that path.
  children?: NavItem[]
}

