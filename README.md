# for-meteor-addon

A [Meteor Client](https://meteorclient.com) addon with two modules: **SpeedMine** (packet mining
with automatic rebreak) and **Journal** (a client-side session log).

Built for Minecraft 1.21.11, Fabric Loader 0.18.2, Meteor Client `1.21.11-SNAPSHOT`.

## SpeedMine

Mining is done with packets instead of the vanilla block breaking loop: the click is cancelled, a
`START_DESTROY_BLOCK` packet is sent, the damage is accumulated client-side for the best tool in the
hotbar, and once the threshold is reached a tool swap plus `STOP_DESTROY_BLOCK` /
`ABORT_DESTROY_BLOCK` pair is sent. When the block is still there afterwards (server rejected the
break, block got restored, ghost block), the module keeps re-sending the break packets for a
configurable number of attempts.

### Settings

**General**

| Setting | Default | Description |
| --- | --- | --- |
| `range` | 4.5 | Maximum distance to the block being mined. |
| `speed` | 1.0 | Block damage required before the break packets are sent. |
| `double-break` | false | Mine two blocks at the same time. |
| `instant` | false | Send the break packets immediately after clicking. |
| `multitask` | false | Keep mining while using an item. |
| `auto-tool` | Silent | Swap to the best tool before the break packet (`None`, `Normal`, `Silent`). |

**Rebreak**

| Setting | Default | Description |
| --- | --- | --- |
| `rebreak` | true | Re-send the break packets while the block is still there. |
| `rebreak-delay` | 500 ms | Delay between rebreak attempts. |
| `rebreak-attempts` | 3 | How many times a single block is re-broken before giving up. |

**Render**

`render`, `shape-mode`, `lines` and `sides`. There is a single line color (no start/end gradient)
and no glass/silhouette related options.

## Journal

An original module that keeps a small log of the session while it is enabled and prints a summary
when you turn it off: time played, blocks mined, distance walked and deaths. It sends no packets
and changes no behaviour, so it is safe to leave on anywhere.

**Settings**

| Setting | Default | Description |
| --- | --- | --- |
| `track-distance` | true | Counts how far you have walked (teleports and respawns are ignored). |
| `track-mined` | true | Counts blocks you finished breaking. |
| `track-deaths` | true | Counts your deaths. |
| `print-on-disable` | true | Prints the summary in chat when the module is turned off. |

The module info string shows the mined block count while it is active.

## Build

```bash
JAVA_HOME=/path/to/jdk-21 ./gradlew build
```

The jar lands in `build/libs/for-meteor-addon-1.0.0.jar`. Drop it into `.minecraft/mods` next to
Meteor Client.

## Layout

```
src/main/java/piolunson/addon/SpeedMineAddon.java        addon entrypoint, category, module registration
src/main/java/piolunson/addon/modules/SpeedMine.java      the speed mine module
src/main/java/piolunson/addon/modules/Journal.java        the session journal module
src/main/resources/fabric.mod.json                        meteor entrypoint
```

## Credits

- Packet mining approach and the MIT-style packet sequence: **LeavesHack** by Leaves_awa
  (`PacketMine`, https://github.com/MrBZBZ/LeavesHack).
- Rebreak / remine behaviour and damage model: **shoreline-addon-public** by LYEddie
  (`SpeedMineII`, `AutoMine`, https://github.com/LYEddie/shoreline-addon-public).
- Meteor Client API: Meteor Development.

This is a reimplementation for Meteor 1.21.11, not a copy of either addon — no code from them is
bundled beyond the shared ideas and the Meteor API itself.
