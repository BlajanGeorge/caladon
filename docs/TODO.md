# To do

Work that is agreed but not built. Design decisions live in ARCHITECTURE.md; this is only the list of
what is still owed, newest first. Cross off by deleting the line in the commit that does it.

## Art to generate

- **Marks for the three errands** — attack, support and espionage. The bar draws them as text glyphs
  today (`⚔`, `✚`, `◎` in `CityQueues.tsx`), tinted red, green and blue. Wanted: three small medallions in
  the style of the unit ones (`hud-unit-*.png`), readable at 16 px, one per errand, used in both the
  Marches and the Arrivals rows.
- **The Cave** — three tiers like every other building (`b-cave-1..3.png`), plus its icon
  (`docs/building_icons.py` makes that from the top tier). The building exists and can be built; it has
  no art and no plot in the city picture, so it is reached from the Town Hall's window. Where it stands
  in the picture is still open — the ground has nine plots and the Wall has none either.

## Espionage (designed in ARCHITECTURE.md → Army → Espionage and the Cave)

- **Reports**: a `report` table owned by a player, a list to read them in, an unread mark in the top bar.
  Espionage cannot ship without it, and battle reports belong in the same place.
- **The spy mission itself**: the `ESPIONAGE` movement kind exists and the bar draws it, but nothing can
  send one and one that lands only turns around. Still owed: paying the silver out of the Cave, one
  mission per target city at a time, and resolving it against the target's Cave balance.
- **Remove the Scout unit**: a migration for the rows that exist (`city_unit`, `city_study`,
  `city_recruit_order`), its place on the Academy ladder, and its medallion.

## Smaller things

- The bar's improvements the user has in mind for the Marches and Arrivals sections.
- Combat numbers are a first cut: the arm split, the Wall factor and the loss curve want tuning against
  Tribal Wars rather than being merely plausible.
- Rams and catapults do no damage yet; a Nobleman does not lower loyalty.
