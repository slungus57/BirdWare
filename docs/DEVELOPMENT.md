# BirdWare developer guide

BirdWare is a client-side Fabric mod for **Minecraft Java 1.21.11** using **official Mojang mappings**
(Mojmap names: `Minecraft`, `LocalPlayer`, `GuiGraphics`, `ClientLevel`, `MultiPlayerGameMode`, `Identifier` —
`ResourceLocation` no longer exists in 1.21.11).

| Component | Version |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.141.6+1.21.11 |
| Fabric Loom | 1.14.10 (`fabric-loom` plugin, runs on Java 21) |
| Java | 21 |
| Gradle | 9.2.1 (wrapper) |

Build: `./gradlew build` → `build/libs/birdware-<version>.jar`.

---

## 1. Package layout

```
com.birdware
├── BirdWare / BirdWareClient / ModuleRegistry   bootstrap, manager access, module registration
├── event            EventBus, Event, CancellableEvent, @Subscribe, EventPriority
│   └── events       concrete events (see §3)
├── module           Module, Category, ModuleManager
│   └── modules.<category>   one class per module
├── setting          all setting types (§5)
├── config           ConfigManager (profiles), JsonFiles (atomic, corruption-safe IO), ConfigComponent
├── core             PacketManager, RotationManager, InventoryManager, TargetManager, ... (shared systems)
├── render           Render3D, Render2D, Projection, render types, icons, fonts
├── gui              ClickGUI and widgets; gui.theme = Theme/ThemeManager
├── hud              HUD element framework and HUD editor
├── notification     Notification, NotificationManager
├── command          CommandManager and commands
├── friend           FriendManager
├── util             ColorUtil, Animation, Easing, SmoothValue, MsTimer, TickTimer, KeyUtil, ChatUtil, RotationUtil, ...
└── mixin.<area>     mixins, one config per area (§8)
```

## 2. Accessing systems

`BirdWare.get()` returns the singleton (non-null after client init):

| Accessor | Purpose |
|---|---|
| `events()` | `EventBus` |
| `modules()` | `ModuleManager`: `get(Class)`, `get(String)`, `getByCategory`, `search`, `getConflicts` |
| `config()` | `ConfigManager`: profiles (`saveProfile/loadProfile/createProfile/renameProfile/deleteProfile/listProfiles`), `registerComponent` |
| `friends()` | `FriendManager`: `isFriend(Entity|String)`, `add/remove/toggle/list` |
| `themes()` | `ThemeManager`; current theme via static `ThemeManager.get()` |
| `notifications()` | `NotificationManager`: `info/success/warning/error(title, msg)`, `push(...)` |
| `packets()` | `PacketManager`: `send(packet)`, `sendDirect(packet)` (bypasses PacketEvent.Send) |
| `rotations()` | `RotationManager` (§6) |
| `inventory()` | `InventoryManager` (§6) |
| `targets()` | `TargetManager` (§6) |

Feature-specific managers created later (commands, waypoints, HUD) expose a static `get()` singleton in their own
package.

## 3. Events

Listeners are methods annotated with `@Subscribe` taking exactly one event parameter. Dispatch is by **exact class**
(`TickEvent.Pre` listeners do not get `TickEvent.Post`). Higher `priority` runs first.

Modules are subscribed only while enabled — **a disabled module never receives events**. Non-module systems call
`BirdWare.get().events().subscribe(this)` once.

| Event | When / thread | Notes |
|---|---|---|
| `TickEvent.Pre/Post` | start/end of client tick, **only in game** | `mc.player`/`mc.level` guaranteed non-null |
| `ClientTickEvent.Pre/Post` | every client tick, also in menus | player may be null |
| `MotionEvent.Pre` | start of `LocalPlayer.sendPosition` (inside player tick) | mutable yaw/pitch/onGround, applied only to the outgoing movement packet |
| `MotionEvent.Post` | after the movement packet was sent | do attacks/placements that need the new server rotation here |
| `PlayerMoveEvent` | start of `LocalPlayer.move` | replace the movement `Vec3` (speed, flight, phase) |
| `PacketEvent.Send` | before an outbound packet is written (client thread) | cancellable; `getPacket()` |
| `PacketEvent.Receive` | before an inbound packet is handled — **usually the netty thread** | cancellable; record data / `mc.execute(...)`, never touch world state directly |
| `PacketEvent.Sent` | after an outbound packet was handed to netty | |
| `KeyInputEvent` | raw key press/release/repeat, before vanilla | cancellable; `isScreenOpen()` |
| `MouseClickEvent` | raw mouse button, before vanilla | cancellable |
| `MouseScrollEvent` | mouse wheel, before vanilla | cancellable |
| `Render3DEvent` | each frame after terrain+entities (Fabric END_MAIN) | draw with `Render3D` |
| `Render2DEvent` | HUD pass (above vanilla HUD, below chat), only with a level and HUD visible | `getGraphics()`, `getPartialTick()` |
| `ScreenOpenEvent` | before `Minecraft.setScreen` | cancellable; screen may be null |
| `AttackEntityEvent` / `.Post` | before/after `MultiPlayerGameMode.attack` | cancellable (friend protection) |
| `ChatSendEvent` | user typed a non-command chat message | cancellable (client commands) |
| `ChatReceiveEvent` | before a message is added to the chat HUD | replace or cancel |
| `WorldChangeEvent` | `mc.level` changed (join, dimension change, leave) | old/new level, may be null |
| `GameJoinEvent` / `GameLeaveEvent` | connected / disconnected (client thread) | |
| `ModuleToggleEvent` | a module was toggled by the user | not posted during config loads |

Several hot events are **reused singletons** (`INSTANCE`): never store them.

New event types: add a class in `com.birdware.event.events` extending `Event` or `CancellableEvent` and post it
with `BirdWare.get().events().post(new MyEvent(...))`. Before allocating an expensive event in a hot path, check
`events().hasListeners(MyEvent.class)`.

## 4. Writing a module

```java
package com.birdware.module.modules.combat;

public final class TriggerBot extends Module {
	private final SettingGroup sgTargets = group("Targets");

	private final NumberSetting range = add(new NumberSetting("Range", "Maximum distance to the entity under the crosshair.", 3.0, 1.0, 6.0, 0.1).unit("m"));
	private final ModeSetting mode = add(new ModeSetting("Mode", "How hits are timed.", "Cooldown", "Cooldown", "Delay"));
	private final RangeSetting delay = add(new RangeSetting("Delay", "Random delay between hits.", 80, 120, 0, 1000, 5).unit("ms"))
		.visibleWhen(() -> mode.is("Delay"));
	private final BooleanSetting players = sgTargets.add(new BooleanSetting("Players", "Attack players.", true));

	public TriggerBot() {
		super("TriggerBot", "Automatically attacks the entity under your crosshair when your weapon is charged.", Category.COMBAT);
	}

	@Override
	protected void onDisable() {
		// release everything acquired (rotations, slots, buffers...)
	}

	@Override
	public String getInfo() {
		return mode.get();
	}

	@Subscribe
	private void onTick(TickEvent.Pre event) {
		// mc.player / mc.level are non-null here
	}
}
```

Rules:
- **Name** is the display name (`"KillAura"`). **Description** must explain concretely what it does.
- `onEnable`/`onDisable` may run outside a world (player null) — null-check. `onDisable` must undo every side
  effect: release rotations (`rotations().release(this)`), flush/clear packet buffers, restore hotbar slots, release
  forced key states, restore game options, clear targets.
- Override `onWorldLeave()` to clear world-bound state (targets, positions, buffers) on disconnect/dimension change.
- `getInfo()` returns the ArrayList suffix (mode, target count...) or null.
- Use `saveData/loadData(JsonObject)` for non-setting per-profile data (HUD positions etc.).
- Every module automatically has a "Module" settings group: keybind, bind mode (Toggle/Hold), show-in-ArrayList and
  toggle-notification. Do not add your own keybind setting for the module itself.
- Modules that should be on in a fresh profile implement `ConfigManager.DefaultEnabled` (marker interface).
- Register each module in `com.birdware.ModuleRegistry.registerAll` (done centrally — agents report their class
  names instead of editing that file).
- Access other modules with `BirdWare.get().modules().get(KillAura.class)`.

## 5. Settings

Declare as `private final` fields. `add(...)` puts a setting in the General group; `group("Name").add(...)` creates
named, collapsible groups. All types serialize to JSON, show in the ClickGUI and work with `.bw set`.

| Type | Value | Constructor |
|---|---|---|
| `BooleanSetting` | boolean | `(name, desc, default)` → `get()`/`isOn()` |
| `NumberSetting` | double | `(name, desc, default, min, max, step)` `.unit("ms")` → `getInt()/getFloat()/getDouble()` |
| `RangeSetting` | low/high pair | `(name, desc, low, high, min, max, step)` → `getLow()/getHigh()/random()` |
| `ModeSetting` | String | `(name, desc, default, modes...)` → `is("Mode")` |
| `EnumSetting<E>` | enum | `(name, desc, E.DEFAULT)` → `get()`, `is(E.X)`; override `toString()` or implement `EnumSetting.Nameable` for labels |
| `ColorSetting` | ARGB + rainbow | `(name, desc, 0xAARRGGBB)` → **always read with `argb()`** (honours rainbow) |
| `KeybindSetting` | key/mouse button | `(name, desc, KeybindSetting.Bind.NONE)` → `get().matchesKey(code)` |
| `StringSetting` | String | `(name, desc, default[, maxLength])` |
| `MultiSelectSetting` | Set<String> | `(name, desc, List options, List defaults)` → `isSelected("Players")` |
| `StringListSetting` | List<String> | `(name, desc, List defaults)` |
| `RegistryListSetting<T>` | registry ids | `(name, desc, BuiltInRegistries.BLOCK, List.of("minecraft:diamond_ore"))` → `contains(block)` O(1) |
| `ButtonSetting` | action | `(name, desc, "Label", runnable)` — not persisted |

Chain `.visibleWhen(() -> cond)` to hide dependent settings and `.onChange(v -> ...)` to react to changes.

## 6. Shared systems

### Rotations — `BirdWare.get().rotations()`
Never call `player.setYRot/setXRot` from modules. Request a rotation **every tick** while you need it:
```java
rotations.request(this, yaw, pitch, RotationManager.Priority.COMBAT, maxDegreesPerTick, silent);
```
The highest priority request wins; it is eased (speed limit + mouse-sensitivity GCD) and sent in the movement packet
(silent) or applied to the camera (non-silent). Check `rotations.isFacing(yaw, pitch, tolerance)` or
`isOwner(this)` in `MotionEvent.Post` before acting. `release(this)` on disable. Use `RotationUtil.calculate(from, to)`,
`RotationUtil.toPoint(vec)`, `RotationUtil.closestPoint(box)`, `RotationUtil.direction(yaw, pitch)`.

### Inventory — `BirdWare.get().inventory()`
Inventory indices: 0-8 hotbar, 9-35 main, 36-39 armor (feet→head, `InventoryManager.armorIndex(slot)`), 40 offhand.
- Search: `findHotbar/findMain/findAny(predicate)`, `findBest(scoreFn, from, to)`, `findBestHotbar`, `count`,
  `findEmpty`, `emptySlotCount`, `getStack(i)`.
- Hotbar ownership: `requestSlot(this, slot, Priority.X)` each tick while needed (higher priority preempts),
  `releaseSlot(this)` restores the previously selected slot. `isSlotOwner(this)`, `isSlotLocked()`.
- `select(slot)` (selects + syncs immediately), `withSlot(slot, action)` (silent switch-act-switch back in one tick).
- Clicks (work with any container open): `tryAcquireClicks(this, priority)` first (one module per tick, bounded
  clicks), then `swapToHotbar(i, hotbar)`, `swapToOffhand(i)`, `quickMove(i)`, `move(from, to)`, `drop(i, all)`,
  `quickMoveMenuSlot(menuSlot)`, `clickMenuSlot(menuSlot, button, ClickType)`, `toMenuSlot(i)`, `swapHands()`,
  `isCursorEmpty()`, `isPlayerMenuActive()`.
- Priorities: `InventoryManager.Priority.{LOW, TOOL, WEAPON, BUILD, CONSUME, HEAL, TOTEM}`.
- `ItemUtil`: `isSword/isAxe/isMeleeWeapon/isRangedWeapon`, `attackDamage/attackSpeed/dps`, `miningSpeed(stack,
  state)`, `durabilityFraction`, `armorSlot/isArmor/armorScore`, `isFood/food/isUnsafeFood/isGoldenApple/isTotem`,
  `hasEffect(stack, MobEffects.X)`, `isThrowablePotion`, `isPlaceableSolidBlock`, `enchantmentLevel(stack, Enchantments.X)`.

### Targets — `BirdWare.get().targets()`
- `TargetSettings` adds the standard target settings to a group:
  `new TargetSettings(group("Targets"), defaultRange, maxRange, withSortSetting)`; `apply(filter)`; `sort()`.
- `TargetFilter` (reusable, mutable) → `findBest(filter, sort)`, `findAll(filter, sort, outList)`, `count(filter)`,
  `getNearby()` (living entities within 64 m, refreshed each tick). Friends are excluded unless `filter.friends`.
- Announce the entity you are fighting with `setCombatTarget(this, entity)` every tick (TargetHUD reads
  `getCombatTarget()`); `clearCombatTarget(this)` on disable.
- `TargetManager.effectiveHealth(e)`, `RotationUtil.eyeDistanceSqToBox(e)` for reach checks.

### Blocks — `BlockUtil`
`findPlaceTarget(pos, strictVisibility)` → `PlaceTarget(neighbor, face, hitVec)`; `place(target, hand, swing)`;
`isReplaceable`, `isUnobstructed(pos, block)`, `reach()`, `canBreak`, `breakBlock(pos, face, swing)` (start/continue),
`closestFace`, `canSee(pos, point)`, `breakDelta(pos)`, `swing(hand, visible)`.

### Vanilla accessors (core mixins)
- `((MinecraftAccessor) mc).birdware$getRightClickDelay()/birdware$setRightClickDelay(int)`,
  `birdware$startAttack()` (vanilla left click), `birdware$startUseItem()` (vanilla right click).
- `((MultiPlayerGameModeAccessor) mc.gameMode).birdware$syncSelectedSlot()`, `birdware$get/setDestroyDelay`,
  `birdware$get/setDestroyProgress`, `birdware$getCarriedIndex()`.
- `((EntityAccessor) entity).birdware$setOnGroundRaw(boolean)`.

### Packets — `BirdWare.get().packets()`
`send(p)` (other modules see it) / `sendDirect(p)` (bypasses `PacketEvent.Send`; use to release buffered packets).

### Rendering
- **World**: inside `@Subscribe Render3DEvent`, call `Render3D.boxOutline/boxFilled/box/boxCorners/boxBottom/line/
  lineStrip/circle/tracer` with **world coordinates**; `seeThrough=true` ignores depth. `Render3D.lerpBox(entity)` /
  `lerpPos(entity)` give frame-interpolated positions. Line width is in pixels (`Render3D.defaultLineWidth()`).
- **World → screen**: `Projection.toScreen(x, y, z, vector3fOut)` (GUI coordinates, false when behind the camera),
  valid during `Render2DEvent` of the same frame.
- **2D**: see `docs/RENDER2D.md` (Render2D API: rounded rects, shadows, gradients, icons, text, items, scissor).
  GuiGraphics is deferred in 1.21.11: no RenderSystem state, ARGB ints everywhere (alpha must be non-zero for text).

### Chat
`ChatUtil.info/success/warning/error(String)`, `ChatUtil.send(Component)` (prefixed, any thread).

### Notifications
`BirdWare.get().notifications().info("Waypoint created", "Home at 120 64 -40")`.

### Timing
`MsTimer` (ms stopwatch), `TickTimer` (tick counter), `Animation` (eased 0..1, frame-rate independent),
`SmoothValue` (exponential follow), `Easing`.

## 7. Threading
- Everything runs on the client thread except `PacketEvent.Receive` (netty thread for most packets).
- In receive listeners only read the packet, cancel, or schedule with `mc.execute(...)`.
- Never block the client/render thread (file IO goes through `JsonFiles.writeAsync`).

## 8. Mixins
- Each implementation area has its own mixin config `birdware.<area>.mixins.json` → package
  `com.birdware.mixin.<area>`. Add your class names to the `client` array of **your assigned config only** (your task
  names it). Never edit another area's config or `birdware.mixins.json` (core).
- `defaultRequire = 1`: every injector must match exactly one target, otherwise the game fails to start. Copy
  method names and **full descriptors** from the decompiled source / `javap`; never guess.
- Prefer `@Inject`, `@ModifyReturnValue`, `@ModifyExpressionValue`, `@WrapOperation`, `@WrapWithCondition`,
  `@ModifyArg` (MixinExtras 0.5.5 ships with Fabric Loader). **Never** use `@Overwrite` or `@Redirect` (they conflict
  with other mods and with each other).
- Prefix every added member with `birdware$`. Use `@Unique` for added fields, accessor/invoker interfaces for private
  members (`@Accessor("fieldName")`, `@Invoker("methodName")`).
- Mixins only bridge into BirdWare: keep logic in modules; read module state via
  `BirdWare.get().modules().get(X.class)` and check `isEnabled()` first (cheap).
- Hot paths (render/entity/collision hooks): no allocation, early-out when the module is disabled.

## 9. Code standards
- No placeholders, no TODO/FIXME, no empty stubs, no fake functionality. Every setting must have an effect.
- Tabs for indentation, Javadoc on public classes and non-obvious methods, comments explain *why*.
- Defensive: null player/level, removed entities (`isRemoved()`), empty stacks, closed screens, disconnects.
- Performance: no per-frame allocation of collections in render paths, throttle scans (block scans across ticks,
  cache results), use squared distances, bound every queue/cache.
- Verify **every** Minecraft API against the decompiled sources before use.
