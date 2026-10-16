# WorldBinder 1.6.0 Runtime Audit

Runtime: Minecraft 26.3, Java 25, Fabric Loader 0.19.5, Fabric API 0.160.7+26.3.
The cleanup base is c61b975. Capture, cache, recovery and export implementation classes are unchanged by the movement audit.

## Shared Configuration Contract

All 24 enum entries (14 from 1.4.1, 10 added in 1.6.0) have independent OFF defaults in `MovementSettings`, JSON persistence through `WorldBinderConfig`, bounded finite values, a category (`MovementTool.Category`), a value unit, Settings cards with toggles, sliders where applicable, an optional unbound toggle key and matching English/German labels, tooltips and HUD badges. The Settings screen uses the shared `WbIntegerSlider` with tenths for decimal values. Config replacement is published through a volatile reference for the integrated server.

`MovementController.enabled` additionally requires the master switch (`movementMasterEnabled`) and, when `movementDisableOnServers` is set, a non-multiplayer world. Every tool path below goes through that gate, so pausing restores vanilla behavior and removes all attribute modifiers on the next tick. `MovementProfile` only writes enabled flags and values into a `MovementSettings` draft.

## Runtime Paths

| Tool | Runtime implementation and release behavior |
| --- | --- |
| Speed | `MovementAttributes`: transient movement-speed modifier; removal restores the existing base and other mods' modifiers. |
| Fly | `FlightMotion.tick` tracks Creative-style double Jump while Fly is enabled; `MovementController.travel` uses the active flight state for collision-aware, smoothed horizontal/vertical motion with a Sprint boost. Landing, tool disable, player replacement and native creative flight clear the custom state. No ability flags are written. Open menus ignore input. |
| Spider | `spiderActive` requires forward input, current physical wall contact and horizontal collision, excluding water/lava/ladders/flight. `SurfaceMotion` applies an impulse only while active; normal physics resumes when contact ends. |
| Jesus | `WaterSurface` and `MovementWaterMixin`: per-query water-only shape at actual fluid height, only above exposed surfaces. Sneak, disabled state, passengers, spectators and flight do not add collision. Nothing is persisted on blocks or players. |
| No Fall | `MovementPlayerMixin.causeFallDamage`: local player's integrated-server damage path is cancelled while enabled; disabled falls use vanilla. No remote-server protection is promised. |
| Step | `MovementAttributes`: transient step-height modifier, removed when inactive; base value is never overwritten. |
| High Jump | Transient jump-strength modifier affects jumps, not continuous velocity. Ground jumps are suppressed during custom Fly or active Spider. |
| Glide | `SurfaceMotion.afterTravel`: clamps negative Y only; no effect on ascent, fluids, ladders, active Spider, creative/elytra/custom flight. |
| Fast Ladder | `handleOnClimbable` return hook changes movement before vanilla moves the entity: forward/Jump ascends, release descends, Sneak holds. Outside climbing/eligible input, vanilla return value is untouched. |
| Auto Sprint | `SurfaceMotion.updateSprint`: checks input, hunger, collision, sneaking and item use. Tracks only sprint it started, releasing it on disable/invalid conditions without cancelling an explicitly held sprint key. |
| Safe Walk | `maybeBackOffFromEdge` return hook trims unsupported grounded SELF movement; skips Sneak, passengers and flight and preserves vanilla's existing result. |
| Auto Jump | Forward grounded collision must contain an actual small obstacle with headroom. Step, active Spider, ladder climbing and manual jump take priority. |
| Water Speed | Extra acceleration only while swimming, not on a Jesus-supported surface or during flight. Does not write persistent movement parameters. |
| Air Control | Extra airborne steering, excluding fluids, climbing and flight; Glide may independently limit downward velocity. |
| Bunny Hop (1.6.0) | `SurfaceMotion.beforeTravel`: grounded forward movement without Sneak, fluids, climbing, Spider or a held Jump calls `jumpFromGround`; `AirMotion.afterGroundJump` scales horizontal speed by `1 + (value-1)*0.35`, capped at 1.4 blocks/tick. |
| Long Jump (1.6.0) | `jumpFromGround` RETURN hook (`AirMotion.afterGroundJump`): only sprint jumps get `1 + (value-1)*0.45` horizontal speed, capped at 1.4 blocks/tick; vertical speed is untouched. |
| Sneak Speed (1.6.0) | `MovementAttributes`: transient `SNEAKING_SPEED` multiplier while walking; vanilla clamps the attribute to its maximum. |
| No Slow (1.6.0) | `MovementAttributes`: transient `MOVEMENT_EFFICIENCY` modifier raising the value to 1.0 while walking. |
| Parkour (1.6.0) | `SurfaceMotion.atEdge`: grounded, moving at least 0.08 blocks/tick, current support below, no support 0.32 blocks ahead and headroom above → `jumpFromGround`. Auto Jump takes priority. |
| No Jump Delay (1.6.0) | `MovementAttributes` sets `LivingEntity.noJumpDelay` to 0 through the `LivingEntityAccessor` mixin while walking. |
| Jetpack (1.6.0) | `AirMotion.jetpack`: airborne, Jump held, not in fluids/climbing/Spider → Y velocity eases towards `0.11 * value` and fall distance resets while thrusting. Glide does not cap descent while Jetpack thrust is active. |
| Low Gravity (1.6.0) | `MovementAttributes`: transient `GRAVITY` multiplier `value - 1` (10-100%) while eligible; removal restores vanilla gravity. |
| Elytra Boost (1.6.0) | `MovementController.travel` while fall-flying: with Forward held, velocity eases towards `look * (0.45 + 0.35 * value)` by `0.035 * value` per tick; vanilla elytra physics continue to run. |
| Auto Surface (1.6.0) | `AirMotion.autoSurface`: in water, not sneaking, not sprint-swimming, not Jesus-supported and Jump not held → +0.04 Y velocity up to 0.12. |

## Registered Mixins and 26.3 Targets

- `MovementLivingMixin` -> `LivingEntity`: `travel(Vec3)` HEAD/RETURN, `jumpFromGround()` HEAD and RETURN (1.6.0 jump boost), `handleOnClimbable(Vec3)` RETURN, `canStandOnFluid(FluidState)` HEAD.
- `LivingEntityAccessor` (1.6.0) -> `LivingEntity`: setter accessor for `noJumpDelay`.
- `MovementPlayerMixin` -> `Player`: `tick()` HEAD, `causeFallDamage(double,float,DamageSource)` HEAD, `maybeBackOffFromEdge(Vec3,MoverType)` RETURN.
- `MovementWaterMixin` -> `LiquidBlock`: `getCollisionShape(BlockState,BlockGetter,BlockPos,CollisionContext)` RETURN. Only entity-specific exposed water surfaces are added; original shapes are preserved by union.

All three are in `worldbinder.mixins.json`, with required injection matching. Signatures and invocation order were inspected in the 26.3 bytecode; automated client startup verifies transformation. The earlier fixed-height `getLiquidCollisionShape` injection has been replaced, not left as a second competing water hook. The new block hook also handles flowing water, which vanilla's source-only path skips.

## Messages and Export Targets

All ordinary WorldBinder status/success/warning/error callers use `Chat`, whose central dispatcher selects Chat, Toast, both or neither. `WorldBinderNotifications` delivery calls occur only in this dispatcher; HUD preview, queue rendering/clearing and logging are not alternative message routes. Old preference migration is retained.

26.3 remains the build/runtime target. 26.2 is retained as an export entry with DataVersion 4903, verified from Mojang's client `version.json` (client SHA-1 `2dc72797acbc1b63fc16a11c4ac393605f453754`). Older export entries are unchanged. Historical Publish files keep their original release versions.

## Verification and Limits

Run `gradlew clean build runClientTest` with Java 25. The client test is fully automated and isolated under `build/client-test`; it does not join remote servers or require manual input. It covers all toggle/slider persistence for the 24 tools across the four category pages, bounds, message modes and the movement regression cases, the 1.6.0 additions (Sneak Speed, No Slow and Low Gravity attributes with restore, master switch pause/resume, Jetpack thrust, Long Jump sprint-only boost, profile application), live-map texture painting with a roaming capture, plus a small capture/cache/recovery/export roundtrip. Test classes are excluded from the release jar.

No manual ingame testing was performed for this audit. Long-running captures, real crash/rejoin recovery, remote-server behavior and third-party movement-mod interactions are not ingame tested. Automated finite fixtures are not a guarantee for every terrain combination. Existing Loom run-configuration deprecation warnings concern future Gradle versions, not a failed 26.3 compile.
