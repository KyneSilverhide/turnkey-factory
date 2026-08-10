Description

Turnkey Factory turns the chore of building into a one-click job. Place the Factory Controller, dial in its size and style, and watch a real industrial building rise around it — walls, windows, doors, a roof (flat or pitched), cornices, columns, lighting, exterior decor... all pulled straight from the chests you've linked to it. No cheating: every block placed consumes a real resource, and construction pauses cleanly if something runs out.

Need to prep the ground first (or level off what's sticking up)? The Ground Leveler handles that: draw a zone, set a target height, drop a shovel in, and it automatically clears and fills the terrain using the same linked-inventory network — without ever touching your own builds. The Ground Texturizer and Lamplighter finish the job, blending the surrounding terrain into a natural mosaic and lining the approach with lampposts.

Want to defend what you just built? Drop a Turret Base, mount a Machine Gun or Flamethrower on top, wire in some redstone, and it holds the perimeter on its own — coal-fired, or Create-powered straight off your rotation network.

In a hurry and don't even want to plan a base? The Starter House Kit stamps a fully furnished cabin around itself the instant you press the button — no materials, no queue, just a roof over your head.

Create-compatible: if Create (and Create Deco) is installed, the factory dresses itself up with chain conveyors, silos, gauges, cage lamps and catwalks for an even more industrial look, and the turret can run off a kinetic network instead of coal. No hard dependency — everything works great in pure vanilla too.

Features


Factory Controller
Adjustable dimensions (odd width/length 7–31, height 7–32) and ghost-building offset (X/Y/Z, ±15)
Two material themes (Stone / Brick) and two roof shapes (flat / pitched, with dormers and gables)
Full procedural shell generation: walls, windows, front/back doors, cornice, corner and facade columns
Automatic, varied exterior decoration: crate/barrel/chest clusters, climbing vines, wall-mounted AC units, silos, technical greebles, hanging lights
Ghost preview with obstruction detection (protects your builds, only clears natural terrain)
Auto-detected linked inventory network: the build pulls resources from it live and pauses cleanly if a material runs out
Materials list with required/available quantities: in-GUI panel, exportable written book, and Create Clipboard export (checkable list)
Deep optional Create integration (fluid tanks, gauges, cage lamps, catwalks) with full vanilla fallback when Create isn't installed


Ground Leveler
Adjustable rectangular zone, X/Z offset, target height and fill depth
Runs on a shovel (durability consumed per block), stops cleanly if it breaks
Clears everything above the target height without ever touching a block placed by a player
Fills gaps with material from linked inventories (any block works, weighted by availability), recycling cleared debris as fill first
Visual ghost (grid + red cells) and a live estimate of fill needed vs. already covered


Ground Texturizer
Adjustable radius (2–64 blocks), works outward from itself in concentric rings
Two palettes: Stone (cobblestone / gravel / andesite / stone, equal mix, with an optional patch of coarse dirt and vegetation) or Dirt (dirt / podzol / rooted dirt, equal mix)
Runs on the tool that matches the palette — a pickaxe for Stone, a shovel for Dirt — durability consumed as it works
Pulls material from linked inventories; only re-textures natural ground, so existing builds are left alone
Ghost preview shows exactly which cells are about to change before you hit Start


Lamplighter
Adjustable radius (8–64 blocks) and grid spacing (6–32 blocks)
Plants a lamppost on solid ground at every grid point around itself — no tool required
Each lamppost costs 1 torch, 1 iron ingot and 1 log (any wood species — automatically detected from what's in your linked inventories)
Lights itself up while idle, so the site isn't dark before it's even started working


Turret Defense System
Two-block machine: a Turret Base (targeting, power, ammo, redstone and its own interface) with a weapon mounted on top — weapons are interchangeable between bases
Two base variants: the coal-fired Turret Base burns any furnace fuel pulled from linked inventories; the Create-compatible Kinetic Turret Base draws power from a rotational network instead (shaft from below, or a Cogwheel on the side) and fires faster the harder the network spins
Both need a redstone signal to fire — there's no on/off switch in the GUI, it's wired like any other machine
Adjustable range (4–32 blocks) and target filters (Hostile / Neutral / Player); never targets tamed animals, and never targets whoever placed the turret even with Player targeting on
Hitscan targeting — instant line-of-sight shots, nothing to dodge
Machine Gun: fires ammunition pulled from linked inventories, with four tiers of damage —
    Copper nugget: half damage
    Iron nugget: full damage
    Armor-Piercing Slug (crafted): double damage
    Incendiary Slug (crafted): double damage, and sets the target on fire
Flamethrower: draws lava from an 8-bucket tank built into the base instead of ammunition — lower damage per hit, but ignites and slows everything in its cone; fill the tank by hand with a bucket or pipe lava straight in with Create


Starter House Kit
One click, one furnished cabin: bed, chests and barrels, furnace and smoker, crafting table, bookshelf and loom, lanterns, and a railed balcony
No materials and no build queue — the whole structure is stamped the instant you press Build
The door always faces the direction you were facing when you placed the block
The kit block is consumed by its own construction — it's a one-shot, not a machine you keep around
