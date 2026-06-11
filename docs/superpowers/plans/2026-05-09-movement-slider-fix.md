# Movement Slider Responsiveness Fix

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `StartMovingWithSteering` and `StartMoving` respond smoothly and immediately to rapid slider updates, matching the reliability of the Motors class.

**Architecture:** Two root causes. (1) Hub Python calls `motor_pair.pair()` before every movement command — when motors are already running, this interrupts and reconfigures the pair mid-motion, breaking subsequent calls. Fix: replace with `motor.run()` directly on each motor (identical to what the working Motors class does). (2) `PositionChanged` on sliders fires many times/second, flooding BLE. Fix: 50ms throttle in Java (20 Hz cap), ensuring the hub is never overwhelmed while still getting the final slider value on release.

**Tech Stack:** Java (LegoSpikeMovement.java), MicroPython (hub_controller.py + HUB_CONTROLLER_PROGRAM embedded in LegoSpikeConnectivity.java), Apache Ant build system.

---

## Background: Why Motors Works and Movement Doesn't

`motor.run(port, speed)` in SPIKE Prime Python: sets a motor to run continuously at a given speed. Calling it again while running **immediately updates the speed** — no state machine, no reconfiguration.

`motor_pair.pair() + motor_pair.move()`: `motor_pair.pair()` reconfigures which physical ports form PAIR_1. When called while PAIR_1 is mid-motion, it interrupts the movement. The subsequent `motor_pair.move()` may not execute cleanly until the pair settles. Result: first call works, subsequent calls race against the reconfiguration.

**Fix**: calculate per-motor velocities from steering+speed in Python and call `motor.run()` directly — zero overhead, immediate update, identical behaviour to the Motors class.

---

## Files

| File | Change |
|---|---|
| `src/resources/hub_controller.py` | Replace motor_pair with motor.run() in FWD/BWD/STEER/STOP handlers |
| `src/io/github/appinventor/legospikeprime/LegoSpikeConnectivity.java` | Same change in embedded HUB_CONTROLLER_PROGRAM string |
| `src/io/github/appinventor/legospikeprime/LegoSpikeMovement.java` | Add 50ms throttle field + throttle logic to StartMovingWithSteering and StartMoving |

---

## Task 1: Fix hub_controller.py — Replace motor_pair with motor.run()

**Files:**
- Modify: `src/resources/hub_controller.py` (the MOV handler block, currently lines ~112–136)

The differential steering calculation for `motor.run()`:
- `s` = steering, -100 to +100 (positive = turn right)
- `v` = signed velocity deg/s (positive = forward, negative = backward)
- Positive steering: left motor at `v`, right motor at `v * (100-s) // 100`
- Negative steering: left motor at `v * (100+s) // 100`, right motor at `v`
- Steering 0: both at `v`
- This produces identical behaviour to `motor_pair.move(PAIR_1, steering, velocity=v)` without the reconfiguration overhead.

- [ ] **Step 1: Open hub_controller.py and locate the MOV handler**

The block to replace starts with `# Ports are embedded in every command` and contains FWD/BWD/STEER/STOP sub-handlers using `motor_pair`.

- [ ] **Step 2: Replace the entire MOV handler block**

Find this block (lines ~112–136):
```python
        # --- Movement ---
        # Ports are embedded in every command (MOV:FWD:A:B:050) so mid-program
        # port changes take effect immediately — same pattern as Motors.
        elif cmd == 'MOV' and len(parts) >= 2:
            sub = parts[1].upper()
            if sub == 'FWD' and len(parts) >= 5:
                lp, rp = parts[2].upper(), parts[3].upper()
                if lp in PORTS and rp in PORTS:
                    motor_pair.pair(motor_pair.PAIR_1, PORTS[lp], PORTS[rp])
                    motor_pair.move(motor_pair.PAIR_1, 0, velocity=int(parts[4]) * 11)
            elif sub == 'BWD' and len(parts) >= 5:
                lp, rp = parts[2].upper(), parts[3].upper()
                if lp in PORTS and rp in PORTS:
                    motor_pair.pair(motor_pair.PAIR_1, PORTS[lp], PORTS[rp])
                    motor_pair.move(motor_pair.PAIR_1, 0, velocity=-(int(parts[4]) * 11))
            elif sub == 'STEER' and len(parts) >= 6:
                lp, rp = parts[2].upper(), parts[3].upper()
                if lp in PORTS and rp in PORTS:
                    motor_pair.pair(motor_pair.PAIR_1, PORTS[lp], PORTS[rp])
                    motor_pair.move(motor_pair.PAIR_1, int(parts[4]), velocity=int(parts[5]) * 11)
            elif sub == 'STOP':
                if len(parts) >= 4:
                    lp, rp = parts[2].upper(), parts[3].upper()
                    if lp in PORTS and rp in PORTS:
                        motor_pair.pair(motor_pair.PAIR_1, PORTS[lp], PORTS[rp])
                motor_pair.stop(motor_pair.PAIR_1)
```

Replace with:
```python
        # --- Movement ---
        # Uses motor.run() directly (same as Motors class) — no motor_pair
        # reconfiguration overhead, so repeated calls update speed immediately.
        elif cmd == 'MOV' and len(parts) >= 2:
            sub = parts[1].upper()
            if sub == 'FWD' and len(parts) >= 5:
                lp, rp = parts[2].upper(), parts[3].upper()
                if lp in PORTS and rp in PORTS:
                    v = int(parts[4]) * 11
                    motor.run(PORTS[lp], v)
                    motor.run(PORTS[rp], v)
            elif sub == 'BWD' and len(parts) >= 5:
                lp, rp = parts[2].upper(), parts[3].upper()
                if lp in PORTS and rp in PORTS:
                    v = int(parts[4]) * 11
                    motor.run(PORTS[lp], -v)
                    motor.run(PORTS[rp], -v)
            elif sub == 'STEER' and len(parts) >= 6:
                lp, rp = parts[2].upper(), parts[3].upper()
                if lp in PORTS and rp in PORTS:
                    s = int(parts[4])        # steering -100..+100
                    v = int(parts[5]) * 11   # signed velocity (neg = backward)
                    # Differential: positive s turns right (reduce right motor)
                    if s >= 0:
                        lv = v
                        rv = v * (100 - s) // 100
                    else:
                        lv = v * (100 + s) // 100
                        rv = v
                    motor.run(PORTS[lp], lv)
                    motor.run(PORTS[rp], rv)
            elif sub == 'STOP':
                if len(parts) >= 4:
                    lp, rp = parts[2].upper(), parts[3].upper()
                    if lp in PORTS:
                        motor.stop(PORTS[lp])
                    if rp in PORTS:
                        motor.stop(PORTS[rp])
```

- [ ] **Step 3: Verify motor_pair is no longer referenced in the MOV handler**

```bash
grep -n "motor_pair" src/resources/hub_controller.py
```

Expected: zero occurrences (motor_pair import can stay in `import hub, motor, motor_pair, time` for now — unused import is harmless).

- [ ] **Step 4: Also update the comment block at the top of hub_controller.py**

Find and replace the MOV section of the comment header (lines ~8–12):
```python
#   MOV:FWD:050        move forward at 50%
#   MOV:BWD:050        move backward at 50%
#   MOV:STEER:+50:075  steer +50 (right) at speed 75%
#   MOV:STOP           stop movement
```

Replace with:
```python
#   MOV:FWD:A:B:050       move forward, left=A right=B at 50% — uses motor.run()
#   MOV:BWD:A:B:050       move backward
#   MOV:STEER:A:B:+50:075 steer +50 (right) at speed 75% — differential motor.run()
#   MOV:STOP:A:B          stop movement
```

---

## Task 2: Fix embedded HUB_CONTROLLER_PROGRAM in LegoSpikeConnectivity.java

**Files:**
- Modify: `src/io/github/appinventor/legospikeprime/LegoSpikeConnectivity.java` (the HUB_CONTROLLER_PROGRAM string constant, lines ~123–145)

The embedded string is the version that actually gets uploaded to the hub. It must stay in sync with hub_controller.py.

- [ ] **Step 1: Locate and replace the MOV handler in the embedded string**

Find this block (approximately lines 123–144 in LegoSpikeConnectivity.java):
```java
        "        elif cmd == 'MOV' and len(parts) >= 2:\n" +
        "            sub = parts[1].upper()\n" +
        "            if sub == 'FWD' and len(parts) >= 5:\n" +
        "                lp,rp = parts[2].upper(),parts[3].upper()\n" +
        "                if lp in PORTS and rp in PORTS:\n" +
        "                    motor_pair.pair(motor_pair.PAIR_1,PORTS[lp],PORTS[rp])\n" +
        "                    motor_pair.move(motor_pair.PAIR_1,0,velocity=int(parts[4])*11)\n" +
        "            elif sub == 'BWD' and len(parts) >= 5:\n" +
        "                lp,rp = parts[2].upper(),parts[3].upper()\n" +
        "                if lp in PORTS and rp in PORTS:\n" +
        "                    motor_pair.pair(motor_pair.PAIR_1,PORTS[lp],PORTS[rp])\n" +
        "                    motor_pair.move(motor_pair.PAIR_1,0,velocity=-(int(parts[4])*11))\n" +
        "            elif sub == 'STEER' and len(parts) >= 6:\n" +
        "                lp,rp = parts[2].upper(),parts[3].upper()\n" +
        "                if lp in PORTS and rp in PORTS:\n" +
        "                    motor_pair.pair(motor_pair.PAIR_1,PORTS[lp],PORTS[rp])\n" +
        "                    motor_pair.move(motor_pair.PAIR_1,int(parts[4]),velocity=int(parts[5])*11)\n" +
        "            elif sub == 'STOP':\n" +
        "                if len(parts)>=4:\n" +
        "                    lp,rp=parts[2].upper(),parts[3].upper()\n" +
        "                    if lp in PORTS and rp in PORTS: motor_pair.pair(motor_pair.PAIR_1,PORTS[lp],PORTS[rp])\n" +
        "                motor_pair.stop(motor_pair.PAIR_1)\n" +
```

Replace with:
```java
        "        elif cmd == 'MOV' and len(parts) >= 2:\n" +
        "            sub = parts[1].upper()\n" +
        "            if sub == 'FWD' and len(parts) >= 5:\n" +
        "                lp,rp = parts[2].upper(),parts[3].upper()\n" +
        "                if lp in PORTS and rp in PORTS:\n" +
        "                    v = int(parts[4])*11\n" +
        "                    motor.run(PORTS[lp],v); motor.run(PORTS[rp],v)\n" +
        "            elif sub == 'BWD' and len(parts) >= 5:\n" +
        "                lp,rp = parts[2].upper(),parts[3].upper()\n" +
        "                if lp in PORTS and rp in PORTS:\n" +
        "                    v = int(parts[4])*11\n" +
        "                    motor.run(PORTS[lp],-v); motor.run(PORTS[rp],-v)\n" +
        "            elif sub == 'STEER' and len(parts) >= 6:\n" +
        "                lp,rp = parts[2].upper(),parts[3].upper()\n" +
        "                if lp in PORTS and rp in PORTS:\n" +
        "                    s=int(parts[4]); v=int(parts[5])*11\n" +
        "                    lv=v if s>=0 else v*(100+s)//100\n" +
        "                    rv=v*(100-s)//100 if s>=0 else v\n" +
        "                    motor.run(PORTS[lp],lv); motor.run(PORTS[rp],rv)\n" +
        "            elif sub == 'STOP':\n" +
        "                if len(parts)>=4:\n" +
        "                    lp,rp=parts[2].upper(),parts[3].upper()\n" +
        "                    if lp in PORTS: motor.stop(PORTS[lp])\n" +
        "                    if rp in PORTS: motor.stop(PORTS[rp])\n" +
```

- [ ] **Step 2: Verify no motor_pair references remain in the MOV section**

```bash
grep -n "motor_pair" src/io/github/appinventor/legospikeprime/LegoSpikeConnectivity.java
```

Expected: zero occurrences (the import line `import hub, motor, motor_pair, time` is fine — unused in MOV but harmless).

---

## Task 3: Add 50ms throttle to StartMovingWithSteering and StartMoving in Java

**Files:**
- Modify: `src/io/github/appinventor/legospikeprime/LegoSpikeMovement.java`

App Inventor's `PositionChanged` fires many times per second while the student drags a slider. Without throttling, every tiny position change generates a BLE write. The hub's TunnelMessage queue fills up with stale commands; it processes them in order, so the robot responds to old positions long after they were set. 50ms (20 Hz) gives smooth control without overloading BLE.

The throttle discards intermediate commands but **always sends the final position** — App Inventor fires one last `PositionChanged` when the slider is released, which will be sent because 50ms has elapsed since the last send.

- [ ] **Step 1: Add the throttle field to LegoSpikeMovement.java**

Find the field declarations section (currently around line 38–42):
```java
    private String leftPort      = "A";
    private String rightPort     = "B";
    private String direction     = "Forward";
    private int    movementSpeed = 50;
```

Add one field after `movementSpeed`:
```java
    private String leftPort      = "A";
    private String rightPort     = "B";
    private String direction     = "Forward";
    private int    movementSpeed = 50;
    private long   lastMoveSentMs = 0;
    private static final long MOVE_THROTTLE_MS = 50; // 20Hz max to prevent BLE flood
```

- [ ] **Step 2: Apply throttle in StartMovingWithSteering**

Find the current `StartMovingWithSteering` method:
```java
    @SimpleFunction(description =
        "Start moving with steering (–100 to +100, 0 = straight).")
    public void StartMovingWithSteering(int steering) {
        if (!checkConnected()) return;
        steering = Math.max(-100, Math.min(100, steering));
        int speed = direction.equalsIgnoreCase("backward") ? -movementSpeed : movementSpeed;
        connectivity.sendCommand(
            String.format("MOV:STEER:%s:%s:%+d:%d", leftPort, rightPort, steering, speed));
    }
```

Replace with:
```java
    @SimpleFunction(description =
        "Start moving with steering (–100 to +100, 0 = straight).")
    public void StartMovingWithSteering(int steering) {
        if (!checkConnected()) return;
        long now = System.currentTimeMillis();
        if (now - lastMoveSentMs < MOVE_THROTTLE_MS) return;
        lastMoveSentMs = now;
        steering = Math.max(-100, Math.min(100, steering));
        int speed = direction.equalsIgnoreCase("backward") ? -movementSpeed : movementSpeed;
        connectivity.sendCommand(
            String.format("MOV:STEER:%s:%s:%+d:%d", leftPort, rightPort, steering, speed));
    }
```

- [ ] **Step 3: Apply throttle in StartMoving**

Find the current `StartMoving` method:
```java
    @SimpleFunction(description =
        "Start moving the drivebase using the configured Direction and speed.")
    public void StartMoving() {
        if (!checkConnected()) return;
        String cmd = direction.equalsIgnoreCase("forward")
            ? String.format("MOV:FWD:%s:%s:%03d", leftPort, rightPort, movementSpeed)
            : String.format("MOV:BWD:%s:%s:%03d", leftPort, rightPort, movementSpeed);
        connectivity.sendCommand(cmd);
    }
```

Replace with:
```java
    @SimpleFunction(description =
        "Start moving the drivebase using the configured Direction and speed.")
    public void StartMoving() {
        if (!checkConnected()) return;
        long now = System.currentTimeMillis();
        if (now - lastMoveSentMs < MOVE_THROTTLE_MS) return;
        lastMoveSentMs = now;
        String cmd = direction.equalsIgnoreCase("forward")
            ? String.format("MOV:FWD:%s:%s:%03d", leftPort, rightPort, movementSpeed)
            : String.format("MOV:BWD:%s:%s:%03d", leftPort, rightPort, movementSpeed);
        connectivity.sendCommand(cmd);
    }
```

Note: `StopMoving` is NOT throttled — a stop command must always go through immediately for safety.

---

## Task 4: Build, verify, commit

**Files:**
- No new files — all changes are to existing files.

- [ ] **Step 1: Run the protocol-layer unit tests**

From the project root:
```bash
cd C:/ClaudeCode_LegoSpikeProjects/appinventor-lego-spike-prime-extension
ant test
```

Expected output:
```
test:
     [java] JUnit version 4.13.2
     [java] ......................................................................................
     [java] Time: 0.0x
     [java] OK (86 tests)
BUILD SUCCESSFUL
```

- [ ] **Step 2: Build the full extension**

```bash
ant package
```

Expected output ends with:
```
[echo] SUCCESS  →  .../build/extensions/io.github.appinventor.legospikeprime.aix
BUILD SUCCESSFUL
```

If it fails, check the error for syntax issues in the Java or Python string edits.

- [ ] **Step 3: Verify the embedded string changed correctly**

```bash
grep -c "motor_pair.pair\|motor_pair.move" src/io/github/appinventor/legospikeprime/LegoSpikeConnectivity.java
```

Expected: `0` (no motor_pair pair/move calls remaining).

```bash
grep -c "motor.run" src/io/github/appinventor/legospikeprime/LegoSpikeConnectivity.java
```

Expected: `6` (two per FWD/BWD/STEER handler).

- [ ] **Step 4: Verify throttle field exists**

```bash
grep -n "lastMoveSentMs\|MOVE_THROTTLE_MS" src/io/github/appinventor/legospikeprime/LegoSpikeMovement.java
```

Expected: 4 lines (field declaration, constant declaration, 2 usages).

- [ ] **Step 5: Commit**

```bash
cd C:/ClaudeCode_LegoSpikeProjects/appinventor-lego-spike-prime-extension
git add src/resources/hub_controller.py \
        src/io/github/appinventor/legospikeprime/LegoSpikeConnectivity.java \
        src/io/github/appinventor/legospikeprime/LegoSpikeMovement.java
git commit -m "fix: replace motor_pair with motor.run() for smooth slider updates

Root cause: motor_pair.pair() called on every MOV command interrupted
any already-running movement before motor_pair.move() could re-start it,
so only the first call worked and subsequent slider updates were ignored.

Fix: calculate per-motor velocities from steering+speed and call
motor.run() directly on each wheel motor — identical to how the Motors
class works, with zero reconfiguration overhead and immediate speed
updates on every call.

Also add 50ms throttle (20Hz) to StartMovingWithSteering and StartMoving
to prevent BLE command floods from rapid slider PositionChanged events.
StopMoving is deliberately not throttled (safety).

Differential steering math:
  s >= 0 (turn right): lv = v, rv = v * (100-s) / 100
  s < 0  (turn left):  lv = v * (100+s) / 100, rv = v
  v is signed (negative = backward direction)"
```

---

## Self-Review

**Spec coverage:**
- ✅ "only uses the very first call" — fixed by removing motor_pair.pair() reconfiguration
- ✅ "subsequent updates to the sliders will not work" — fixed by motor.run() immediate update
- ✅ "slow and jerky motion" — fixed by (a) no pair reconfiguration stop/restart cycle, (b) 50ms throttle prevents command backlog
- ✅ "as stable as the motors class" — using the identical motor.run() API that Motors uses

**Placeholder scan:** None found — all steps have exact code and exact commands.

**Type consistency:** `lastMoveSentMs` (long), `MOVE_THROTTLE_MS` (long) — used consistently in both methods. `motor.run(PORTS[lp], v)` matches the existing Motors class usage pattern at line ~109 in hub_controller.py.

**Edge case — StopMoving with throttle:** NOT throttled by design (noted in Task 3). A stop must be immediate regardless of last-send time.

**Edge case — backward steering:** When `speed` is negative (backward direction), the differential math still works: `lv = -v, rv = -v * (100-s) // 100` for positive steering — both negative, right motor less negative = robot curves right while going backward. This is the expected behaviour.
