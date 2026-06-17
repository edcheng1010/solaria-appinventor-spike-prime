> *Unofficial — independent open-source project, not affiliated with the LEGO Group or the Massachusetts Institute of Technology. See [NOTICE](../NOTICE) for trademark and licensing details.*

> **Terminology note:** "Phase N" in this document = this repo's internal development milestones (equivalently **Epic SPIKE-N**), distinct from the ecosystem-level **Generations (Gen 1–4)** in [solaria-hub](https://github.com/edcheng1010/solaria-hub/blob/main/ROADMAP.md). Historical labels are retained for traceability.

# The Definitive Claude Code Playbook

> **⚠️ Historical document (April 2026).** This describes the original, now-completed
> bring-up of the protocol layer. Its prompts reference the old
> `io.github.appinventor.legospike*` packages (since merged and renamed to the single
> **`solaria.appinventor.spikeprime`** package) and include `git push` steps that no
> longer reflect the current workflow (commit locally; do not push). Retained for
> traceability — do not follow its steps verbatim.

**Date:** April 26, 2026
**Project:** LEGO SPIKE Prime App Inventor Extension
**Author:** Manus AI

This is the single, definitive guide for Edward to follow. Do not use any previous guides. Follow these steps exactly in order.

---

## Step 1: Prepare Your Environment

1. Open Command Prompt on your Windows machine.
2. Navigate to the project directory:
   ```cmd
   cd C:\Projects\solaria-appinventor-spike-prime
   ```
3. Pull the latest changes (this includes the critical UUID fix I just made):
   ```cmd
   git pull origin main
   ```
4. Launch Claude Code:
   ```cmd
   claude
   ```

---

## Step 2: The First Prompt (COBS Encoding)

Copy and paste this exact text into Claude Code:

> "Read `docs/IMPLEMENTATION_PLAN.md`, `docs/BYTE_LEVEL_PROTOCOL.md`, and `docs/deep_analysis/04_cobs_test_vectors.md`. I need you to implement the custom COBS encoding algorithm in `io.github.appinventor.legospike.COBSEncoder.java`. It must escape 0x00, 0x01, and 0x02. Use the exact constants specified in the byte-level protocol doc. After writing the class, write a JUnit test class that verifies your implementation against the three test vectors provided in `04_cobs_test_vectors.md`. Do not proceed to any other task until these tests pass."

**What to do next:**
1. Claude Code will propose changes. Type **y** to approve them.
2. Claude Code will run the tests.
3. If the tests fail, tell Claude Code to fix them.
4. If the tests pass, compile the project to be safe: `ant extensions`
5. Commit the changes: `git add . && git commit -m "Implement COBS encoding" && git push origin main`

---

## Step 3: The Second Prompt (CRC32)

Only after Step 2 is complete and committed, copy and paste this exact text into Claude Code:

> "Now implement the CRC32 checksum logic in `io.github.appinventor.legospike.SpikeCRC32.java`. According to the protocol docs, the data MUST be padded to 4-byte alignment with 0x00 bytes before calculating the CRC. It must also support a running CRC (using the previous CRC as a seed). Write a unit test to verify this behavior."

**What to do next:**
1. Approve changes (**y**).
2. Ensure tests pass.
3. Compile: `ant extensions`
4. Commit: `git add . && git commit -m "Implement CRC32" && git push origin main`

---

## Step 4: The Third Prompt (Message Framing)

Only after Step 3 is complete and committed, copy and paste this exact text into Claude Code:

> "Implement the message framing and XOR obfuscation in `io.github.appinventor.legospike.MessageFramer.java`. It needs a `pack` method that takes raw bytes, applies COBS encoding, XORs every byte with 0x03, and then adds the 0x01 prefix and 0x02 suffix. It also needs an `unpack` method that does the reverse. Write a unit test to verify that `unpack(pack(data))` equals the original data."

**What to do next:**
1. Approve changes (**y**).
2. Ensure tests pass.
3. Compile: `ant extensions`
4. Commit: `git add . && git commit -m "Implement Message Framing" && git push origin main`

---

## Step 5: Stop and Report

Once you have completed Steps 1 through 4, stop using Claude Code. Come back to Manus and tell me:
*"I have completed Phase 1 (COBS, CRC32, Message Framing). All tests pass."*

I will then give you the exact prompts for Phase 2 (BLE Connection & Handshake), which requires physical testing with the hub.
