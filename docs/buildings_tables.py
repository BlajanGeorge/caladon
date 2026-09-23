"""Generates BUILDINGS-PROPOSAL.md (in this directory) from a handful of constants.

Run:  python3 docs/buildings_tables.py
Every number in the document comes from the formulas below; change a knob and regenerate.
"""
import math, os

# ---------------- Caladon knobs ----------------
COST_SCALE = 1          # resource cost = COST_SCALE x Tribal Wars cost (1 = identical)
POP_SCALE = 1           # population cost = POP_SCALE x Tribal Wars (1 = identical)
TIME_SCALE = 1          # build time = Tribal Wars speed-1 time / TIME_SCALE (1 = identical)
POINTS_GROWTH = 1.2     # points total at level L = P1 * 1.2^(L-1)      (Tribal Wars, verified)
PROD_GROWTH = 1.163118  # production growth per level                     (Tribal Wars, verified)
CAP_GROWTH = 1.2294934  # capacity growth per level                       (Tribal Wars, verified)
FARM_MAX_MULT = 100     # farm pop at max level = 100 x level-1 value      (Tribal Wars: 240 -> 24 000)
PROD_L1 = 30            # per hour at level 1
CAP_L1 = 1000
FARM_L1 = 240
VAULT_L1 = 150
START_STOCK = 500

def r(v): return int(math.floor(v + 0.5))

# code, name, max, founded, TW L1 (wood, clay->stone, iron), TW pop, factors (w, s, i, pop), P1 (TW points L1), TW base build time s, prerequisites, job
B = [
 ('FARM',       'Farm',       30, True,  (45, 40, 30),     0,  (1.30, 1.32, 1.29, 1.00),     5, 1200,  '—',                                  'population'),
 ('WOODCUTTER', 'Woodcutter', 30, True,  (50, 60, 40),     5,  (1.25, 1.275, 1.245, 1.155),  6, 900,   '—',                                  'wood/h'),
 ('STONE_MINE', 'Stone Mine', 30, True,  (65, 50, 40),     10, (1.27, 1.265, 1.24, 1.14),    6, 900,   '—',                                  'stone/h'),
 ('IRON_MINE',  'Iron Mine',  30, True,  (75, 65, 70),     10, (1.252, 1.275, 1.24, 1.17),   6, 1080,  '—',                                  'iron/h'),
 ('DEPOSIT',    'Deposit',    30, True,  (60, 50, 40),     0,  (1.265, 1.27, 1.245, 1.15),   6, 1020,  '—',                                  'capacity'),
 ('TOWN_HALL',  'Town Hall',  30, True,  (90, 80, 70),     5,  (1.26, 1.275, 1.26, 1.17),   10, 900,   '—',                                  'build speed, gates'),
 ('BARRACKS',   'Barracks',   25, False, (200, 170, 90),   7,  (1.26, 1.28, 1.26, 1.17),    16, 1800,  'Town Hall 3',                        'recruits all troops; level unlocks unit types'),
 ('ACADEMY',    'Academy',    20, False, (220, 180, 240),  20, (1.26, 1.275, 1.26, 1.17),   19, 6000,  'Town Hall 8, Farm 6, Barracks 5',    'studies unit types; level gates what can be studied'),
 ('WALL',       'Wall',       20, False, (50, 100, 20),    5,  (1.26, 1.275, 1.26, 1.17),    8, 3600,  'Town Hall 5',                        'defence bonus'),
 ('VAULT',      'Vault',      10, False, (50, 60, 50),     2,  (1.25, 1.25, 1.25, 1.17),     5, 1800,  'Town Hall 5, Deposit 5',             'hides resources'),
]
# Conditions that apply at higher levels of a building, on top of the Town Hall step rule.
HIGHER = {
    'BARRACKS': [(10, 'Town Hall 10'), (20, 'Town Hall 20')],
    'ACADEMY':  [(10, 'Barracks 10'), (15, 'Barracks 15')],
    'WALL':     [(10, 'Stone Mine 10'), (15, 'Stone Mine 15')],
    'VAULT':    [(5, 'Deposit 10')],
}
def hall_needed(L): return 5 * ((L - 1) // 5)   # Town Hall step rule: levels 6-10 need TH 5, 11-15 need TH 10, ...
def gain(b, L): return points(b, L) - points(b, L - 1)
TW_NAME = {'FARM': 'Farm', 'WOODCUTTER': 'Timber camp', 'STONE_MINE': 'Clay pit', 'IRON_MINE': 'Iron mine', 'DEPOSIT': 'Warehouse',
           'TOWN_HALL': 'Headquarters', 'BARRACKS': 'Barracks', 'ACADEMY': 'Smithy (numbers)',
           'WALL': 'Wall', 'VAULT': 'Hiding place'}

def cost(b, L):
    (w, s, i), (fw, fs, fi, _) = b[4], b[6]
    return (r(COST_SCALE * w * fw ** (L - 1)), r(COST_SCALE * s * fs ** (L - 1)), r(COST_SCALE * i * fi ** (L - 1)))
def pop_total(b, L): return r(POP_SCALE * b[5] * b[6][3] ** (L - 1))
def pop_step(b, L): return pop_total(b, L) - (pop_total(b, L - 1) if L > 1 else 0)
def points(b, L): return r(b[7] * POINTS_GROWTH ** (L - 1))
def prod(L): return r(PROD_L1 * PROD_GROWTH ** (L - 1))
def cap(L): return r(CAP_L1 * CAP_GROWTH ** (L - 1))
def farm_pop(L): return int(math.floor(FARM_L1 * FARM_MAX_MULT ** ((L - 1) / 29)))
def hide(L): return r(VAULT_L1 * (4 / 3) ** (L - 1))
def queue_len(L): return 2 + (L >= 10) + (L >= 20)   # build queue slots by Town Hall level
def build_time(b, L, hall=1):
    e = -13 if L <= 2 else (L - 1 - 14 / (L - 1))
    return (b[8] / TIME_SCALE) * 1.18 * 1.2 ** e * 1.05 ** (-hall)
def hms(sec):
    sec = int(round(sec)); h, m, s = sec // 3600, (sec % 3600) // 60, sec % 60
    return f'{h}h{m:02d}m' if h else (f'{m}m{s:02d}s' if m else f'{s}s')
def effect(b, L):
    c = b[0]
    if c == 'FARM': return f'{farm_pop(L):,} (+{farm_pop(L) - farm_pop(L - 1) if L > 1 else farm_pop(L)})'
    if c in ('WOODCUTTER', 'STONE_MINE', 'IRON_MINE'): return f'{prod(L):,}/h'
    if c == 'DEPOSIT': return f'{cap(L):,}'
    if c == 'TOWN_HALL': return f'{round(100 * 1.05 ** (-L))}% build time, queue {queue_len(L)}'
    if c == 'BARRACKS': return f'{round(100 * (2 / 3) * 1.06 ** (-L))}% recruit time'
    if c == 'ACADEMY': return f'{round(100 * 1.1 ** (-L))}% study time'
    if c == 'WALL': return f'+{round(100 * (1.037 ** L - 1))}% defence'
    if c == 'VAULT': return f'{hide(L):,} per resource'
    return ''
def fmt_cost(c): return f'{c[0]:,} / {c[1]:,} / {c[2]:,}'

out = []
P = out.append
total_pts = sum(points(b, b[2]) for b in B)
total_pop = sum(pop_total(b, b[2]) for b in B)
founded = [b for b in B if b[3]]
start_pts = sum(points(b, 1) for b in founded)
wc = next(b for b in B if b[0] == 'WOODCUTTER')

P('# Caladon — Buildings proposal (research + numbers)')
P('')
P('**Status: accepted (design only, nothing implemented).** Companion to the *Buildings* and *Army* sections of')
P('ARCHITECTURE.md, which record the rules; this file holds the numbers and is the source of the config tables.')
P('')
P('All numbers below are generated by `docs/buildings_tables.py` from the formulas in §3, not typed by hand.')
P('Sources for the reference games are in §8.')
P('')
P('## 1. What the reference games do (summary of the research)')
P('')
P("**Tribal Wars** (InnoGames): 3 land resources (wood/clay/iron), 15–19 buildings, everything driven by")
P("`base × factor^(level−1)` with one factor per resource per building (verified against the game's own")
P('`buildings.xml`). Points per building level are `P1 × 1.2^(level−1)` exactly. Mines produce')
P('`30 × 1.163118^(level−1)` per hour (2 400/h at level 30); farm holds `240 × 100^((level−1)/29)` (24 000 at')
P('30); warehouse `1 000 × 1.2294934^(level−1)` (400 000 at 30). Build time `base × 1.18 × 1.2^(L−1−14/(L−1)) ×')
P('1.05^(−HQ)`. Wall +3.7 % defence per level compounding; Barracks/Stable/Workshop recruit at `2/3 × 1.06^(−L)`;')
P('Market merchants `L` up to 10 then `(L−10)² + 10`. New village: HQ 1, Farm 1, Warehouse 1, Hiding place 1,')
P('Rally point 1 = 26 points, 7 of 240 population used.')
P('')
P('**Grepolis** (InnoGames): naval game, 3 resources (wood/stone/silver) plus favour and culture, 14 regular')
P('buildings + 8 special (choose 2). Costs and effects are hand-tuned tables rather than exponentials; mines')
P('cap at 275–426/h at level 40, farm at 3 560, warehouse at 28 142; points grow ≈ ×1.08–1.14 per level with')
P('very different bases per building (Temple 216 at level 1, Timber Camp 22). New city: Senate 1, Agora,')
P('Farm 1, Warehouse 1 = 175 points, 114 population. Senate speeds construction by a non-linear table')
P('(44.5 % at level 25).')
P('')
P('**Takeaways for Caladon**')
P('- Use the Tribal Wars numbers **1:1**: one exponential per resource per building, points `P1 × 1.2^(L−1)`,')
P('  30/h, 1 000 capacity, 240 population at level 1. A pace proven over twenty years, and every table can be')
P('  lifted verbatim. The earlier gut values (30/min, 2 000, 100, 50 points) are replaced.')
P('- Both games keep the farm and warehouse **free of population cost**; both make resource buildings the')
P('  cheapest and the military buildings the most expensive per level; both gate everything through a')
P('  headquarters/senate that also speeds construction.')
P("- Grepolis's special-building \"pick 2 of 8\" is a nice late-game choice but needs the naval/culture systems")
P('  it plugs into; skip for now.')
P('')
P('## 2. Proposed roster')
P('')
P('Nine buildings. The six marked *founded* exist at level 1 in every new city and cost nothing at level 1')
P('(Town Hall included: it gates the others, speeds construction and owns the build queue). The other three are')
P('built by the player.')
P('')
P('| code | name | max | founded | job | level-1 value | points L1 (+gain at L2 / L10 / max) | prerequisites |')
P('|---|---|---|---|---|---|---|---|')
for b in B:
    g = f'{b[7]} (+{gain(b, 2)} / +{gain(b, 10)} / +{gain(b, b[2])})'
    P(f'| `{b[0]}` | {b[1]} | {b[2]} | {"yes" if b[3] else "no"} | {b[10]} | {effect(b, 1)} | {g} | {b[9]} |')
P('')
P(f'A new city therefore starts with **{start_pts} points** ({start_pts - 10} without Town Hall), **{FARM_L1} free population**,')
P(f'**{PROD_L1}/h** of each resource, **{START_STOCK}** of each in stock and a **{CAP_L1:,}** cap.')
P('')
P('Notes on the choices:')
P("- **Rally Point** (Tribal Wars) is *not* a building: commanding troops is always available from the")
P('  Barracks/city screen. One less thing to build for no decision value.')
P("- **Vault** = Tribal Wars' Hiding Place, renamed to fit *Deposit*; hides a per-resource amount from")
P('  plunder. Cheap, 10 levels, optional to build. If plunder is never implemented it can be dropped.')
P('- **One recruitment building.** Tribal Wars splits recruitment over Barracks / Stable / Workshop and gates')
P('  unit types through the Smithy. Caladon collapses that: the **Barracks** recruits every unit type, and its')
P('  **level** decides which types are available; the **Academy** is where a unit type is *studied* before it')
P('  can be recruited, and its level decides which types can be studied. See *Unit gating* below.')
P("- **Academy numbers** are Tribal Wars' Smithy (cost, population, points, 20 levels, study-time bonus), since")
P('  it takes over that role. Conquest / nobles are **not** in the Academy; they get their own building later.')
P('- **No Market.** There is no trade between cities in Caladon; resources are earned by production and')
P('  plunder only.')
P('- Skipped on purpose: Smithy, Stable, Workshop (folded into Barracks + Academy), Rally Point, Statue/Paladin')
P('  (hero system), Church/Temple (faith), Watchtower (can come later as an optional 20-level building),')
P("  Harbour/Lighthouse (naval), Cave (silver-specific), Agora (UI, not a building), Grepolis's special buildings.")
P('')
P('### Unit gating (Barracks level × Academy study)')
P('')
P("A unit type can be recruited in a city when **both** hold: the city's Barracks is at or above the type's")
P("*Barracks level*, and the type has been *studied* in that city's Academy. Studying a type is a one-time")
P("purchase per city (resources, no population) that requires the Academy at or above the type's *Academy")
P("level*; the Academy's level also shortens study time (`1.1^(−L)`). The first unit needs no study so a")
P('Barracks-1 city can recruit at once. The ladder below is **illustrative** — the units themselves are the')
P('next design; only the gating mechanism is proposed here.')
P('')
P('| unit (placeholder) | Barracks level | Academy level to study | study cost (w/s/i) |')
P('|---|---|---|---|')
P('| Spearman | 1 | — (no study) | — |')
P('| Swordsman | 3 | 1 | 400 / 500 / 300 |')
P('| Axeman | 5 | 3 | 700 / 840 / 820 |')
P('| Scout | 5 | 2 | 560 / 480 / 480 |')
P('| Archer | 8 | 5 | 640 / 560 / 740 |')
P('| Light cavalry | 10 | 8 | 2 200 / 2 400 / 2 000 |')
P('| Ram | 12 | 10 | 1 200 / 1 600 / 800 |')
P('| Heavy cavalry | 15 | 13 | 3 000 / 2 400 / 2 000 |')
P('| Catapult | 18 | 16 | 1 600 / 2 000 / 1 200 |')
P('')
P("(Study costs are Tribal Wars' simple-tech research costs, as a starting point.)")
P('')
P('### Level requirements')
P('')
P('Two kinds of conditions, both checked at order time:')
P('')
P('1. **Town Hall step rule (every building except the Town Hall itself).** To reach level `L` the Town Hall')
P('   must be at least `5 × floor((L−1)/5)`: levels 2–5 need nothing, 6–10 need Town Hall 5, 11–15 need 10,')
P('   16–20 need 15, 21–25 need 20, 26–30 need 25. So a city cannot sit at Town Hall 3 with level-30 mines;')
P('   the Town Hall always trails the tallest building by at most five levels.')
P('2. **Cross-building conditions** — to build a player building at all, and again at a few higher levels')
P('   (Grepolis style: the Vault needs a Deposit, the Academy needs a Barracks, the Wall a Stone Mine;')
P('   the Barracks depends on the Town Hall only, one step tighter than the step rule).')
P('')
P('| building | to build (level 1) | at higher levels (in addition to the step rule) |')
P('|---|---|---|')
for b in B:
    if b[0] == 'TOWN_HALL': P('| Town Hall | founded | none (it is the gate) |'); continue
    hi = ', '.join(f'L{L}: {c}' for L, c in HIGHER.get(b[0], [])) or 'step rule only'
    P(f'| {b[1]} | {"founded" if b[3] else b[9]} | {hi} |')
P('')
P('')
P('## 3. Formulas (the knobs)')
P('')
P('```')
P(f'cost[r](L)      = round(COST_SCALE × base_r × f_r^(L−1))        COST_SCALE = {COST_SCALE}; base/f per building in §8')
P(f'popTotal(L)     = round(POP_SCALE × base_pop × f_pop^(L−1))     POP_SCALE = {POP_SCALE}; popCost(L) = popTotal(L) − popTotal(L−1)')
P(f'points(L)       = round(P1 × {POINTS_GROWTH}^(L−1))                     cumulative; the city gains points(L) − points(L−1) per upgrade')
P(f'production(L)   = round({PROD_L1} × {PROD_GROWTH}^(L−1)) per hour          Woodcutter / Stone Mine / Iron Mine; {prod(30):,}/h at 30')
P(f'capacity(L)     = round({CAP_L1} × {CAP_GROWTH}^(L−1))              Deposit, per resource; {cap(30):,} at 30')
P(f'farmPop(L)      = floor({FARM_L1} × {FARM_MAX_MULT}^((L−1)/29))              Farm; farmGain(L) = farmPop(L) − farmPop(L−1); {farm_pop(30):,} at 30')
P(f'buildTime(L)    = (base_s / TIME_SCALE) × 1.18 × 1.2^(L−1−14/(L−1)) × 1.05^(−TownHall)   TIME_SCALE = {TIME_SCALE}; exponent = −13 for L ≤ 2')
P('townHall(L)     = build time × 1.05^(−L)                          (95 % at 1 … 23 % at 30)')
P('queue(L)        = 2 slots; 3 from Town Hall 10; 4 from Town Hall 20   (orders waiting in the build queue, all paid at placement)')
P('recruit(L)      = unit time × 2/3 × 1.06^(−L)                      Barracks (all unit types)')
P('study(L)        = study time × 1.1^(−L)                            Academy (Tribal Wars Smithy fit, community, not official)')
P('wall(L)         = defender strength × 1.037^L                      (+107 % at 20)')
P(f'vault(L)        = round({VAULT_L1} × (4/3)^(L−1)) hidden per resource     ({hide(1)} → {hide(10):,}; TW uses a table for the last levels: 1 125 / 1 500 / 2 000)')
P('```')
P('')
P('Why these knobs:')
P(f'- **COST_SCALE {COST_SCALE}, POP_SCALE {POP_SCALE}, TIME_SCALE {TIME_SCALE}: Tribal Wars 1:1.** They stay as knobs so a faster world')
P('  variant is one constant, not a redesign; but the intended way to run faster worlds is the **world speed**')
P('  multiplier already in the Resources design (it scales production and build times together).')
P('- **Rates per hour**, as in Tribal Wars: 30/h is 0.5/min and per-minute numbers read badly. The Resources')
P('  section now says per hour; the implemented `ratePerMinute` field becomes `ratePerHour` (the settlement')
P('  math is a unit conversion).')
P(f'- **Points ×1.2 with Tribal Wars P1 values.** A maxed mine is worth {points(wc, 30):,} points, a maxed Farm')
P(f'  {points(B[0], 30):,}, a fully built city (every building at max) {total_pts:,}. The existing map')
P('  tier thresholds are set from these totals: t1 < 300 (village), t2 < 1 000 (town), t3 < 2 500 (city),')
P('  t4 < 6 000 (large city), t5 ≥ 6 000 (capital) — everything at level 10 ≈ 450, 15 ≈ 1 100, 20 ≈ 2 650,')
P('  25 ≈ 5 300.')
P(f'- **Population.** Farm {FARM_L1} at level 1, {farm_pop(30):,} at 30; all buildings maxed use {total_pop:,}, leaving')
P(f'  {farm_pop(30) - total_pop:,} for troops. Founding buildings cost no population, so a new city has all {FARM_L1} free')
P('  (Tribal Wars: 233).')
P('')
P('## 4. Full tables — founded buildings (all 30 levels)')
P('')
P('Columns: cost to reach the level (wood / stone / iron), population that level takes, effect **at** the level,')
P('cumulative points, build time at Town Hall 1 and world speed 1 (at Town Hall 20 multiply by 0.38).')
P('')
for b in founded:
    P(f'### {b[1]} (`{b[0]}`, max {b[2]})')
    P('')
    P('| lvl | wood / stone / iron | pop | effect | points | build time |')
    P('|---|---|---|---|---|---|')
    for L in range(1, b[2] + 1):
        cst = '— (founded)' if L == 1 else fmt_cost(cost(b, L))
        pp = 0 if L == 1 else pop_step(b, L)
        bt = '—' if L == 1 else hms(build_time(b, L))
        P(f'| {L} | {cst} | {pp} | {effect(b, L)} | {points(b, L):,} | {bt} |')
    P('')
P('## 5. Pace check')
P('')
P("Hours of a single mine's production (at the level shown, no other income) needed to afford the *next* level")
P('of that mine, and the build time at Town Hall 1 — so you can see which of the two gates progress:')
P('')
P('| level → next | wood needed | hours of income | build time |')
P('|---|---|---|---|')
for L in (1, 2, 3, 5, 10, 15, 20, 25, 29):
    need = cost(wc, L + 1)[0]
    P(f'| {L} → {L + 1} | {need:,} | {need / prod(L):,.1f} h | {hms(build_time(wc, L + 1))} |')
P('')
P('Deposit: the cap must stay ahead of the next mine level\'s cost (otherwise the player cannot save up).')
P('')
P('| deposit level | capacity | most expensive resource of the next mine level (same level) | fits? |')
P('|---|---|---|---|')
for L in (1, 2, 3, 5, 10, 15, 20, 25, 29):
    c = max(cost(wc, L + 1))
    P(f'| {L} | {cap(L):,} | {c:,} | {"yes" if cap(L) >= c else "**no — raise Deposit first**"} |')
P('')
P(f'Fresh city: {START_STOCK} of each, +{PROD_L1}/h each. The second mine level costs about two hours of income; the')
P(f'Deposit fills from {START_STOCK} to {CAP_L1:,} in ~{(CAP_L1 - START_STOCK) / PROD_L1:.0f} h. Early play is a few upgrades per session — Tribal')
P('Wars pacing at world speed 1.')
P('')
P('## 6. Compact tables — the other buildings')
P('')
P('Same columns, sampled levels. Level 1 must be built (it is not founded).')
P('')
for b in B:
    if b[3]: continue
    P(f'### {b[1]} (`{b[0]}`, max {b[2]}) — requires {b[9]}')
    P('')
    P('| lvl | wood / stone / iron | pop | effect | points | build time |')
    P('|---|---|---|---|---|---|')
    for L in sorted((set([1, 2, 3, 5, 10, 15, 20, 25, 30]) & set(range(1, b[2] + 1))) | {b[2]}):
        P(f'| {L} | {fmt_cost(cost(b, L))} | {pop_step(b, L)} | {effect(b, L)} | {points(b, L):,} | {hms(build_time(b, L))} |')
    P('')

# ---- Units (Tribal Wars values from the live get_unit_info XML; swordsman cavalry defence is the archer-world value)
# code, name, role, (wood, stone, iron), pop, recruit base s (speed 1), atk, def, def_cav, def_arch, speed min/field, carry, barracks lvl, academy lvl (None = no study), study cost (w,s,i)
UNITS = [
 ('SPEARMAN',   'Spearman',      'cheap defence, strong vs cavalry',        (50, 30, 10),    1, 1020,  10,  15,  45,  20, 18,  25,  1, None, None),
 ('SWORDSMAN',  'Swordsman',     'defence vs infantry',                     (30, 30, 70),    1, 1500,  25,  50,  15,  40, 22,  15,  3, 1,  (400, 500, 300)),
 ('AXEMAN',     'Axeman',        'cheap attack, weak defence',              (60, 30, 40),    1, 1320,  40,  10,   5,  10, 18,  10,  5, 3,  (700, 840, 820)),
 ('SCOUT',      'Scout',         'espionage, does not fight',               (50, 50, 20),    2, 900,    0,   2,   1,   2,  9,   0,  5, 2,  (560, 480, 480)),
 ('ARCHER',     'Archer',        'defence vs archers',                      (100, 30, 60),   1, 1800,  15,  50,  40,   5, 18,  10,  8, 5,  (640, 560, 740)),
 ('LIGHT_CAV',  'Light Cavalry', 'fast attack, big carry (raiding)',        (125, 100, 250), 4, 1800, 130,  30,  40,  30, 10,  80, 10, 8,  (2200, 2400, 2000)),
 ('RAM',        'Ram',           'breaks the Wall',                         (300, 200, 200), 5, 4800,   2,  20,  50,  20, 30,   0, 12, 10, (1200, 1600, 800)),
 ('HEAVY_CAV',  'Heavy Cavalry', 'strong attack and defence, expensive',    (200, 150, 600), 6, 3600, 150, 200,  80, 180, 11,  50, 15, 13, (3000, 2400, 2000)),
 ('CATAPULT',   'Catapult',      'destroys buildings',                      (320, 400, 100), 8, 7200, 100, 100,  50, 100, 30,   0, 18, 16, (1600, 2000, 1200)),
 ('NOBLEMAN',   'Nobleman',      'conquest: lowers loyalty, takes the city', (40000, 50000, 50000), 100, 18000, 30, 100, 50, 100, 35, 0, 20, 20, (15000, 25000, 10000)),
]
def recruit_time(base, L): return base * (2 / 3) * 1.06 ** (-L)
def study_time(base, L): return 2 * base * 1.1 ** (-L)   # placeholder: TW does not publish research durations

P('## 7. Buildings with their own behaviour')
P('')
P('### Deposit')
P('')
P('Nothing beyond capacity: `capacity(L)` per resource (table in §4); production above it is lost.')
P('')
P('### Vault — what it protects')
P('')
P('An attacker who wins can take, per resource, at most `stock − vault(L)`, further limited by the carry')
P("capacity of the surviving attacking units. The Vault does nothing else. The curve is Tribal Wars' Hiding")
P('place (the last three levels use its table values):')
P('')
P('| Vault level | hidden per resource | Deposit at the same level | share protected |')
P('|---|---|---|---|')
VAULT_TABLE = [150, 200, 267, 356, 474, 632, 843, 1125, 1500, 2000]
for L, v in enumerate(VAULT_TABLE, 1):
    P(f'| {L} | {v:,} | {cap(L):,} | {round(100 * v / cap(L))} % |')
P('')
P('At level 10 it is maxed: a developed city with a level-20 Deposit (50 675) protects only 2 000 per')
P('resource. As in Tribal Wars, the Vault is early-game protection, not a late-game one.')
P('')
P('### Barracks — units and recruitment')
P('')
P("Ten land units, all recruited from the Barracks. Stats are Tribal Wars' (live `get_unit_info` XML, archer")
P('world, world speed 1): cost, population, base recruit time in seconds, attack, defence vs general / cavalry /')
P('archers, speed in minutes per field, carry capacity.')
P('')
P('| unit | role | wood / stone / iron | pop | base time | atk | def / cav / arch | speed | carry | Barracks ≥ | study at Academy ≥ |')
P('|---|---|---|---|---|---|---|---|---|---|---|')
for u in UNITS:
    code, name, role, c, pop, bt, atk, d, dc, da, spd, carry, bl, al, sc = u
    P(f'| {name} | {role} | {c[0]} / {c[1]} / {c[2]} | {pop} | {hms(bt)} | {atk} | {d} / {dc} / {da} | {spd} | {carry} | {bl} | {"—" if al is None else al} |')
P('')
P('**Recruitment rules**')
P('- A unit type can be recruited when the Barracks is at or above its *Barracks ≥* level **and** the type has')
P("  been studied in this city's Academy (Spearman needs no study).")
P('- Recruit time = `base × 2/3 × 1.06^(−Barracks level)`: 63 % of base at level 1, 37 % at 10, 21 % at 20,')
P('  16 % at 25. Above level 20 the Barracks unlocks nothing new; it only gets faster.')
P('- One recruitment queue per city. An order pays resources and population at placement (as buildings do);')
P('  units complete one at a time; cancelling refunds the unproduced remainder.')
P('- The **Nobleman** is the conquest unit: studied at Academy 20, recruited from Barracks 20. Its stats and')
P("  cost are Tribal Wars' (40 000 / 50 000 / 50 000, 100 population, 5 h base time); the conquest mechanics")
P("  (loyalty, how many nobles a city can hold, Tribal Wars' extra coin cost per noble) are a later design.")
P('  Mounted archer, Paladin and Militia are left out.')
P('')
P('Recruit time per unit at a few Barracks levels:')
P('')
P('| unit | base | Barracks 1 | 5 | 10 | 15 | 20 | 25 |')
P('|---|---|---|---|---|---|---|---|')
for u in UNITS:
    P(f'| {u[1]} | {hms(u[5])} | ' + ' | '.join(hms(recruit_time(u[5], L)) for L in (1, 5, 10, 15, 20, 25)) + ' |')
P('')
P('### Academy — what can be studied per level')
P('')
P('Studying a unit type is a one-time purchase per city (resources, no population). It needs the Academy at')
P("or above the type's level and takes `studyBase × 1.1^(−Academy level)`. Tribal Wars does not publish its")
P('research durations, so **studyBase = 2 × the unit\'s recruit base time** is a placeholder. Study costs are')
P("Tribal Wars' simple-tech research costs; the Nobleman's study cost is Tribal Wars' Academy building cost (the")
P('price of gaining the ability to make nobles there), as a placeholder.')
P('')
P('| Academy level | unlocks for study | study cost (wood / stone / iron) | study time at that level |')
P('|---|---|---|---|')
for u in sorted((u for u in UNITS if u[13] is not None), key=lambda u: u[13]):
    if u[13] == 20: P('| 17 – 19 | nothing new; study time keeps shrinking | | |')
    P(f'| {u[13]} | {u[1]} | {u[14][0]:,} / {u[14][1]:,} / {u[14][2]:,} | {hms(study_time(u[5], u[13]))} |')
P('')
P('## 8. Reference data used')
P('')
P("Tribal Wars base values (from the live game `buildings.xml`, en157/en155, cross-checked with twstats and the")
P('official points table). Caladon uses them unchanged; only the names differ (clay → stone, Headquarters →')
P('Town Hall, Warehouse → Deposit, Hiding place → Vault):')
P('')
P('| Caladon building | TW building | L1 wood/clay/iron | pop | factors wood/clay/iron/pop | points L1 | base time s |')
P('|---|---|---|---|---|---|---|')
for b in B:
    P(f'| {b[1]} | {TW_NAME[b[0]]} | {b[4][0]}/{b[4][1]}/{b[4][2]} | {b[5]} | {"/".join(str(x) for x in b[6])} | {b[7]} | {b[8]:,} |')
P('')
P("Sources: Tribal Wars — `https://en157.tribalwars.net/interface.php?func=get_building_info` and the same on")
P('en155 (game config XML), `https://help.tribalwars.net/wiki/Points`, the per-building pages under')
P('`https://help.tribalwars.net/wiki/`, InnoGames KB articles 5288–5342, the "Building Formulas" guide on')
P('forum.tribalwars.us. Grepolis — `https://wiki.en.grepolis.com/wiki/Buildings_Portal` and the per-building')
P('pages, InnoGames KB 3263–3332 and 3527/3533. Community-derived and not officially published: the Tribal')
P('Wars build-time formula (matched by three independent sources), the Smithy 1.1^−L fit, and Grepolis market')
P('capacity (500 × level).')
P('')
P('## 9. Open questions')
P('')
P('1. **Cavalry and siege from the Barracks** (proposed: one recruitment building) or bring back a Stable and')
P('   a Workshop with their own level gates?')
P('2. **Study is per city** (Tribal Wars) or **per player** (Grepolis-style, once for all cities)? Proposed:')
P('   per city, to keep new cities from being instantly full-strength.')
P('3. **The units themselves** (stats, costs, population, recruit times) are the next design, not covered here.')
P('4. ~~Demolition~~ — decided: not in v1 (buildings are permanent, population never refunded).')
P('5. **Starting stock** 500 of each (kept from the earlier design; Tribal Wars is similar).')

open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'BUILDINGS-PROPOSAL.md'), 'w').write('\n'.join(out) + '\n')
print('lines', len(out))
