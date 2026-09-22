# WorldBinder 1.4.0 Runtime Audit

Runtime: Minecraft 26.3, Java 25, Fabric Loader 0.19.5, Fabric API 0.160.7+26.3.
The cleanup base is c61b975. Capture, cache, recovery and export implementation classes are unchanged by the movement audit.

## Shared Configuration Contract

All 14 enum entries have independent OFF defaults in `MovementSettings`, JSON persistence through `WorldBinderConfig`, bounded finite values, Settings toggles, sliders where applicable and matching English/German labels and tooltips. The Settings screen uses the cleanup's shared `WbIntegerSlider` with tenths for decimal values. Config replacement is published through a volatile reference for the integrated server.

## Runtime Paths

| Tool | Runtime implementation and release behavior |
| --- | --- |
| Speed | `MovementAttributes`: transient movement-speed modifier; removal restores the existing base and other mods' modifiers. |
| Fly | `FlightMotion` through `MovementController.travel`: collision-aware horizontal/vertical motion, independent speed; no ability flags are written, no creative permissions remain. Open menus ignore input. |
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

## Registered Mixins and 26.3 Targets

- `MovementLivingMixin` -> `LivingEntity`: `travel(Vec3)` HEAD/RETURN, `jumpFromGround()` HEAD, `handleOnClimbable(Vec3)` RETURN, `canStandOnFluid(FluidState)` HEAD.
- `MovementPlayerMixin` -> `Player`: `tick()` HEAD, `causeFallDamage(double,float,DamageSource)` HEAD, `maybeBackOffFromEdge(Vec3,MoverType)` RETURN.
- `MovementWaterMixin` -> `LiquidBlock`: `getCollisionShape(BlockState,BlockGetter,BlockPos,CollisionContext)` RETURN. Only entity-specific exposed water surfaces are added; original shapes are preserved by union.

All three are in `worldbinder.mixins.json`, with required injection matching. Signatures and invocation order were inspected in the 26.3 bytecode; automated client startup verifies transformation. The earlier fixed-height `getLiquidCollisionShape` injection has been replaced, not left as a second competing water hook. The new block hook also handles flowing water, which vanilla's source-only path skips.

## Messages and Export Targets

All ordinary WorldBinder status/success/warning/error callers use `Chat`, whose central dispatcher selects Chat, Toast, both or neither. `WorldBinderNotifications` delivery calls occur only in this dispatcher; HUD preview, queue rendering/clearing and logging are not alternative message routes. Old preference migration is retained.

26.3 remains the build/runtime target. 26.2 is retained as an export entry with DataVersion 4903, verified from Mojang's client `version.json` (client SHA-1 `2dc72797acbc1b63fc16a11c4ac393605f453754`). Older export entries are unchanged. Historical Publish files keep their original release versions.

## Verification and Limits

Run `gradlew clean build runClientTest` with Java 25. The client test is fully automated and isolated under `build/client-test`; it does not join remote servers or require manual input. It covers all toggle/slider persistence, bounds, message modes and the movement regression cases, plus a small capture/cache/recovery/export roundtrip. Test classes are excluded from the release jar.

No manual ingame testing was performed for this audit. Long-running captures, real crash/rejoin recovery, remote-server behavior and third-party movement-mod interactions are not ingame tested. Automated finite fixtures are not a guarantee for every terrain combination. Existing Loom run-configuration deprecation warnings concern future Gradle versions, not a failed 26.3 compile.
