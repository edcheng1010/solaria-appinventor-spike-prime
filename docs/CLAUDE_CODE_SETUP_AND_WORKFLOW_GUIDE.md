# Claude Code Setup and Three-Party Workflow Guide

**Date:** April 25, 2026
**Project:** LEGO SPIKE Prime App Inventor Extension
**Author:** Manus AI (Project Manager)

---

## Part 1: Claude Code Installation on Windows

### Step 1: Install Node.js (if not already installed)

Claude Code requires Node.js 18 or later. Open Command Prompt and check:

```cmd
node --version
```

If not installed or below v18, download from: https://nodejs.org/en/download/ (choose the LTS version).

### Step 2: Install Claude Code

Open Command Prompt (Run as Administrator) and run:

```cmd
npm install -g @anthropic-ai/claude-code
```

### Step 3: Subscribe to Claude Pro or Max

Claude Code requires a Claude subscription. Go to: https://claude.ai/settings/billing

| Plan | Price | What You Get |
|------|-------|-------------|
| **Pro** | $20/month | Recommended starting point. Flat rate, includes Claude Code usage. |
| **Max** | $100/month | Higher limits. Only needed if you hit Pro rate limits frequently. |

### Step 4: Authenticate Claude Code

In Command Prompt, run:

```cmd
claude
```

This will open a browser window to authenticate with your Anthropic account. Follow the prompts to log in. Once authenticated, Claude Code is ready to use.

### Step 5: Clone the GitHub Repository

```cmd
cd C:\Projects
git clone https://github.com/edcheng1010/appinventor-lego-spike-prime-extension.git
cd appinventor-lego-spike-prime-extension
```

When prompted for credentials, use:
- **Username:** edcheng1010
- **Password:** Your Personal Access Token (the one we generated: `github_pat_11ALVERDI0...`)

**Important:** Do NOT use your GitHub password. Use the PAT token instead.

### Step 6: Launch Claude Code in the Project

```cmd
cd C:\Projects\appinventor-lego-spike-prime-extension
claude
```

Claude Code will automatically detect and read the `CLAUDE.md` file, which contains all the project context, architectural decisions, and rules from our 17-task history. This is the persistent memory that prevents context loss.

---

## Part 2: Key Claude Code Commands

| Command | What It Does |
|---------|-------------|
| `claude` | Start Claude Code in the current directory |
| `/help` | Show all available commands |
| `/status` | Show current session status |
| `/clear` | Clear conversation history (context stays via CLAUDE.md) |
| `/compact` | Compress conversation to save context window |
| `/cost` | Show token usage and cost for current session |
| `Ctrl+C` | Cancel current operation |
| `Escape` (x2) | Exit Claude Code |

### Important Workflow Commands

When Claude Code proposes a file change, it will show you a diff and ask for approval. You can:
- Type **y** to accept the change
- Type **n** to reject the change
- Type **e** to edit the proposed change before applying

This is the critical difference from Manus: **Claude Code never overwrites your code without your explicit approval.**

---

## Part 3: Three-Party Workflow

### Roles and Responsibilities

| Role | Platform | Responsibilities |
|------|----------|-----------------|
| **Manus** (Project Manager) | Manus.im | Research, documentation, architecture oversight, slides, protocol research, testing checklists, progress tracking |
| **Edward** (Owner/Architect/Tester) | Physical devices + GitHub | Requirements, architecture decisions, physical testing with Android + SPIKE Prime, approval gates, final sign-off |
| **Claude Code** (Developer) | Local Windows terminal | Code implementation, compilation, bug fixes, refactoring. Reads CLAUDE.md for context. All changes require Edward's approval. |

### Communication Flow

```
Edward (Requirements/Feedback)
    │
    ├──► Manus (Research, Planning, Documentation)
    │       │
    │       └──► Updates ARCHITECTURE.md / CLAUDE.md on GitHub
    │               │
    │               └──► Claude Code reads updated files
    │
    └──► Claude Code (Implementation)
            │
            └──► Commits to GitHub
                    │
                    └──► Manus reviews progress, updates plans
```

### Standard Development Cycle

**Phase A: Planning (Manus)**
1. Edward describes a high-level requirement to Manus
2. Manus researches, creates a detailed implementation plan
3. Manus updates `ARCHITECTURE.md` and `CLAUDE.md` with the plan
4. Manus pushes updates to GitHub

**Phase B: Implementation (Claude Code)**
1. Edward pulls latest from GitHub: `git pull origin main`
2. Edward launches Claude Code: `claude`
3. Edward gives Claude Code the specific task (e.g., "Implement UUID verification after BLE connection as described in ARCHITECTURE.md Section 4.2")
4. Claude Code reads CLAUDE.md, proposes changes, Edward reviews and approves each change
5. Edward compiles and tests on physical devices
6. Edward commits working code: `git add . && git commit -m "description" && git push origin main`

**Phase C: Testing and Feedback (Edward)**
1. Edward tests on Android device with SPIKE Prime hub
2. Edward reports results back to Manus with specific observations
3. Manus analyzes results, updates plans, and prepares next steps
4. Cycle repeats

### Git Workflow

```
main (stable, tested code only)
  │
  └── feature/uuid-verification (Claude Code works here)
        │
        └── Merge to main only after Edward tests and approves
```

**Before each Claude Code session:**
```cmd
cd C:\Projects\appinventor-lego-spike-prime-extension
git pull origin main
claude
```

**After each successful test:**
```cmd
git add .
git commit -m "Descriptive message about what changed"
git push origin main
```

---

## Part 4: How to Give Tasks to Claude Code

### Good Task Prompts (Specific, Bounded)

```
"Read ARCHITECTURE.md and implement the UUID verification logic described in 
Section 4.2. After connecting to a BLE device, check if it exposes the SPIKE 
Prime service UUID 0000FD02-0000-1000-8000-00805F9B34FB. If not, disconnect 
and fire ErrorOccurred. Only modify BluetoothInterfaceImpl.java."
```

```
"Fix the null pointer exception in LegoSpikePrime.java line 342 where 
connectedDevice can be null when DisconnectFromHub is called before any 
connection is established. Do not change any other logic."
```

### Bad Task Prompts (Too Vague, Risky)

```
"Fix all the bugs in the project"  ← Too broad, will overwrite things
"Refactor the entire codebase"     ← Will break working code
"Make it work"                     ← No specific guidance
```

### Rules for Claude Code Sessions

1. **One feature per session.** Do not ask Claude Code to implement multiple unrelated features at once.
2. **Always specify which files to modify.** Say "Only modify BluetoothInterfaceImpl.java" to prevent unintended changes.
3. **Review every diff.** Never blindly accept changes.
4. **Compile after every change.** Do not accumulate multiple changes before testing.
5. **Commit after every successful test.** This gives you rollback points.

---

## Part 5: Compilation Instructions (Windows Command Prompt)

### Prerequisites

1. **Java Development Kit (JDK 8 or 11):** Download from https://adoptium.net/
   - After installation, verify: `java -version` and `javac -version`
   - Set `JAVA_HOME` environment variable to the JDK installation directory

2. **Apache Ant:** Download from https://ant.apache.org/bindownload.cgi
   - Extract to `C:\apache-ant`
   - Add `C:\apache-ant\bin` to your system `PATH`
   - Verify: `ant -version`

3. **App Inventor Extension Build Tools:**
   - The standard approach is to use the App Inventor extension template from: https://github.com/nicholasgasior/ai2-extension-template
   - Or use the Rush extension builder: https://github.com/nicholasgasior/ai2-extension-template

### Compilation Steps

```cmd
cd C:\Projects\appinventor-lego-spike-prime-extension
ant extensions
```

The compiled `.aix` file will be in the `out/` directory.

### If Using Rush (Alternative Build Tool)

Rush is a simpler build tool specifically for App Inventor extensions:

```cmd
pip install rush-cli
cd C:\Projects\appinventor-lego-spike-prime-extension
rush build
```

---

## Part 6: Debugging on Android Device

### Setup for Logcat Debugging

1. **Enable Developer Options** on your Android device:
   - Go to Settings > About Phone > Tap "Build Number" 7 times
   - Go back to Settings > Developer Options > Enable "USB Debugging"

2. **Install ADB (Android Debug Bridge):**
   - Download Android SDK Platform-Tools: https://developer.android.com/tools/releases/platform-tools
   - Extract to `C:\platform-tools`
   - Add `C:\platform-tools` to your system `PATH`
   - Verify: `adb version`

3. **Connect your Android device via USB** and authorize the debugging connection on the device.

### Viewing Extension Logs

Open a Command Prompt and run:

```cmd
adb logcat -s LegoSpikePrime:D BluetoothInterfaceImpl:D
```

This filters logs to show only messages from the extension's log tags. You will see:
- BLE scanning events
- Device discovery (RSSI values, device names)
- Connection attempts and results
- Service discovery and UUID verification
- Data sent/received to/from SPIKE Prime
- Error messages and stack traces

### Useful ADB Commands

| Command | Purpose |
|---------|---------|
| `adb devices` | List connected devices |
| `adb logcat -c` | Clear the log buffer |
| `adb logcat -s LegoSpikePrime:D` | Filter logs by tag |
| `adb logcat > debug_log.txt` | Save all logs to a file |
| `adb logcat -d > snapshot.txt` | Dump current log buffer to file |

### Testing Checklist

Before each test session, verify:

- [ ] SPIKE Prime hub is powered on and not connected to another device
- [ ] Android device has Bluetooth enabled
- [ ] Android device has Location permission granted to MIT AI2 Companion
- [ ] Latest `.aix` file is imported into the App Inventor project
- [ ] ADB logcat is running to capture debug output

---

## Part 7: Your Personal Access Token

**Token Name:** lego-spike-prime-extension-dev
**Expires:** July 23, 2026
**Scope:** appinventor-lego-spike-prime-extension (Read and Write)

Use this token instead of your password when Git asks for credentials:
```
Username: edcheng1010
Password: <paste your Personal Access Token here>
```

To avoid entering it every time, configure Git credential storage:
```cmd
git config --global credential.helper store
```

Then the next time you enter your credentials, they will be saved.

**Important:** Remember to regenerate this token before it expires on July 23, 2026.

---

## Part 8: Quick Reference Card

### Daily Workflow

```cmd
:: 1. Pull latest changes
cd C:\Projects\appinventor-lego-spike-prime-extension
git pull origin main

:: 2. Start Claude Code
claude

:: 3. Give Claude Code a specific task
:: (type your task in the Claude Code prompt)

:: 4. Review and approve changes

:: 5. Compile
ant extensions

:: 6. Test on device (import .aix into App Inventor)

:: 7. If working, commit and push
git add .
git commit -m "Description of changes"
git push origin main
```

### When Something Goes Wrong

```cmd
:: Undo last uncommitted changes
git checkout -- .

:: Undo last commit (keep changes)
git reset --soft HEAD~1

:: View what changed
git diff

:: View commit history
git log --oneline -10
```
