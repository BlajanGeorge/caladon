"""Builds the small building icons the city view lists queues with.

Each icon is the building's top-tier art (`web/assets/sprites/b-<name>-3.png`, or the only tier there
is) scaled to fit a 96 px
square and centred, never cropped: the whole building shows however small the icon is drawn. Run from
the repository root after adding or replacing any top-tier sprite:

    python3 docs/building_icons.py
"""

from pathlib import Path

from PIL import Image

NAMES = [
    "town-hall", "farm", "woodcutter", "stone-mine", "silver-mine",
    "deposit", "barracks", "academy", "vault", "wall", "cave",
]
SIZE = 96
SPRITES = "web/assets/sprites"


def main() -> None:
    for name in NAMES:
        # The top tier, or the only one: the Cave looks the same however deep it goes.
        tier = next(t for t in (3, 2, 1) if Path(f"{SPRITES}/b-{name}-{t}.png").exists())
        art = Image.open(f"{SPRITES}/b-{name}-{tier}.png").convert("RGBA")
        w, h = art.size
        scale = min(SIZE / w, SIZE / h)
        small = art.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.LANCZOS)
        icon = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        icon.paste(small, ((SIZE - small.size[0]) // 2, (SIZE - small.size[1]) // 2))
        icon.save(f"{SPRITES}/icon-{name}.png")
        print(f"icon-{name}.png  tier {tier}  {art.size} -> {small.size} in {SIZE}x{SIZE}")


if __name__ == "__main__":
    main()
