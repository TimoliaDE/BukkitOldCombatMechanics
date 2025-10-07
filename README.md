<!--
     This Source Code Form is subject to the terms of the Mozilla Public
     License, v. 2.0. If a copy of the MPL was not distributed with this
     file, You can obtain one at https://mozilla.org/MPL/2.0/.
-->

## OldCombatMechanics - Extension
This is an extension of the original **OldCombatMechanics** plugin ([link to original repository](https://github.com/kernitus/BukkitOldCombatMechanics)).

The following options have been added to the config 
(details for each can be found in the config file):

- `old-projectile-trajectory`
- `no-deflect-fire-projectile`
- `old-bow-damage`
- `fix-bow-shoot`
- `old-fall-damage`
- `old-water-placement`
- `damage-inside-wall`

In addition, the default values for the following config options have been adjusted to better match Minecraft 1.8 PvP mechanics:

- `attack-frequency`
- `shield-damage-reduction`
- `old-player-knockback`
- `old-fishing-knockback`
- `old-player-regen`
- `projectile-knockback`

This plugin requires Java 21 and supports the Minecraft Java versions 1.20.2–1.21.10.

### ⚠️ Important Note
In the `build/libs` folder, use **`OldCombatMechanics-v2.1.0-adapted-reobf.jar`** — it’s the only file (with the `.reobf.jar` ending) that will work correctly.
