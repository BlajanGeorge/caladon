# To do

Work that is agreed but not built. Design decisions live in ARCHITECTURE.md; this is only the list of
what is still owed, newest first. Cross off by deleting the line in the commit that does it.

## Art to generate

- Nothing outstanding.


## Espionage (designed in ARCHITECTURE.md → Army → Espionage and the Cave)

- **Reports**: a `report` table owned by a player, a list to read them in, an unread mark in the top bar.
  Espionage cannot ship without it, and battle reports belong in the same place. One rule to keep: an
  attack that lost every man discloses nothing about the city it hit, because nobody came back to tell.
- **The spy mission itself**: the `ESPIONAGE` movement kind exists and the bar draws it, but nothing can
  send one and one that lands only turns around. Still owed: paying the silver out of the Cave, one
  mission per target city at a time, and resolving it against the target's Cave balance.
- **Remove the Scout unit**: a migration for the rows that exist (`city_unit`, `city_study`,
  `city_recruit_order`), its place on the Academy ladder, and its medallion.

## Ranking

- The backend is built (points from cities, battle points from population killed). **No UI yet**: a
  standings screen, and the player's own rank somewhere in the HUD.

## Smaller things

- The bar's improvements the user has in mind for the Marches and Arrivals sections.
- Combat numbers are a first cut: the arm split, the Wall factor and the loss curve want tuning against
  Tribal Wars rather than being merely plausible.
- Rams and catapults do no damage yet; a Nobleman does not lower loyalty.
