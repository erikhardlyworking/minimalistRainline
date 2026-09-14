#!/usr/bin/env python3
"""Observe unlock delivery with Rainline cached, without instrumentation keeping it active.

Requires an Android emulator, a debuggable Rainline install, no secure screen
lock, no placed Rainline widgets and unconfigured default location. Temporarily
enables the emulator's swipe lock, restoring its previous state on exit. Does
not change permissions, battery policy or freezer configuration.
"""
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", default="emulator-5554")
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        parser.error("Screen-control verification is restricted to an emulator")

    def adb(*command):
        return subprocess.check_output([args.adb, "-s", args.serial, *command],
                                       text=True, stderr=subprocess.STDOUT, timeout=15).strip()

    if adb("shell", "getprop", "ro.kernel.qemu") != "1":
        parser.error("Selected device is not an Android emulator")
    trust = adb("shell", "dumpsys", "trust")
    if re.search(r"deviceLocked=1|secure=1|secure=true", trust):
        parser.error("Use an unlocked test emulator with no PIN/password")
    widgets = adb("shell", "dumpsys", "appwidget")
    widget_section = widgets.split("Widgets:", 1)[-1].split("Hosts:", 1)[0]
    if "app.rainline" in widget_section:
        parser.error("Remove emulator Rainline widgets first; their alarms could unfreeze the process")
    prefs = ET.fromstring(adb("exec-out", "run-as", "app.rainline", "cat", "shared_prefs/widgets.xml"))
    for node in prefs:
        if node.get("name", "").startswith("widget.") and "lat" in json.loads(node.text or "{}"):
            parser.error("Use an emulator without a configured Rainline location to avoid network requests")
    disabled = adb("shell", "locksettings", "get-disabled")
    if disabled not in ("true", "false"):
        parser.error("Could not read the emulator lock-screen setting")

    def last_unlock():
        try:
            root = ET.fromstring(adb("exec-out", "run-as", "app.rainline", "cat", "shared_prefs/update-diagnostics.xml"))
            return next((int(n.get("value")) for n in root if n.get("name") == "unlock"), 0)
        except subprocess.CalledProcessError:
            return 0

    def process():
        text = adb("shell", "dumpsys", "activity", "processes", "app.rainline")
        section = text.split("OOM levels:", 1)[0]
        return {"cached": "cached=true" in section, "frozen": "isFrozen=true" in section}

    def queue():
        lines = adb("shell", "dumpsys", "activity", "broadcasts").splitlines()
        result = []
        for i, line in enumerate(lines):
            if re.search(r"^    \S+ \d+:app\.rainline/", line):
                end = next((j for j in range(i + 1, len(lines)) if re.match(r"^    \S", lines[j])), len(lines))
                result.extend(lines[i:min(end, i + 30)])
        return "\n".join(result)

    try:
        adb("shell", "locksettings", "set-disabled", "false")
        adb("shell", "am", "start", "-n", "app.rainline/.SettingsActivity")
        time.sleep(2)
        adb("shell", "input", "keyevent", "3")  # Home, with no instrumented process.
        adb("shell", "input", "keyevent", "223")
        # Android can retain the last activity as a previous process for over
        # a minute before it becomes cached and eligible for freezing.
        deadline = time.monotonic() + 150
        state = process()
        while not state["frozen"] and time.monotonic() < deadline:
            time.sleep(2)
            state = process()
        print("Before wake:", state, flush=True)
        if not state["frozen"]:
            raise RuntimeError("Emulator did not freeze Rainline naturally; this run is inconclusive")
        before = last_unlock()
        adb("shell", "input", "keyevent", "224")
        adb("shell", "wm", "dismiss-keyguard")
        time.sleep(15)
        after = last_unlock()
        print("15 seconds after unlock:", process(), "unlock delivered:", after > before, flush=True)
        print(queue(), flush=True)
        if after <= before:
            print("Reproduced: unlock is held while the app is cached.", flush=True)
        else:
            print("Unlock was delivered on this emulator run; Samsung behaviour may differ.", flush=True)
        adb("shell", "am", "start", "-n", "app.rainline/.SettingsActivity")
        deadline = time.monotonic() + 5
        while last_unlock() <= before and time.monotonic() < deadline:
            time.sleep(.25)
        print("After opening settings: unlock delivered:", last_unlock() > before, flush=True)
        if after <= before and last_unlock() <= before:
            raise RuntimeError("No unlock arrived after settings opened; inspect emulator screen-lock setup")
    finally:
        adb("shell", "input", "keyevent", "224")
        adb("shell", "wm", "dismiss-keyguard")
        adb("shell", "locksettings", "set-disabled", disabled)


if __name__ == "__main__":
    main()
