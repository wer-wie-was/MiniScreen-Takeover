#!/usr/bin/env python3
"""Patch only inspected PebbleOS source versions. Never invokes a compiler."""
import argparse,hashlib
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('pebbleos',type=Path);args=parser.parse_args()
root=args.pebbleos
files={'fw/services/activity/activity.c':'438db0e3976510ee2a3f55fa54552dab2f50e8992b6fb32b43b62d6497395d70','fw/services/activity/activity_metrics.c':'bfc5778d5186013b37946ebe40359f7fc0b9df1a02f0ac70586ad5417759ceae'}
for name,expected in files.items():
 file=root/name
 if 'activity_takeover_get_metric' in file.read_text():raise SystemExit('Patch already present; use a clean source tree.')
 if hashlib.sha256(file.read_bytes()).hexdigest()!=expected:raise SystemExit('Source revision changed: '+name+'. Review the integration before patching.')
source=Path(__file__).resolve().parents[1]/'firmware/takeover_health.inc'
f=root/'fw/services/activity/activity.c';s=f.read_text();s=s.replace('return activity_prefs_heart_rate_is_enabled();','return activity_takeover_heart_enabled();');s='#include <stdbool.h>\nbool activity_takeover_heart_enabled(void);\n'+s;f.write_text(s+'\n'+source.read_text())
f=root/'fw/services/activity/activity_metrics.c';s=f.read_text();needle='bool activity_get_metric(ActivityMetric metric, uint32_t history_len, int32_t *history) {'
assert needle in s
s=s.replace(needle,'extern bool activity_takeover_get_metric(ActivityMetric metric, uint32_t count, int32_t *history);\n'+needle+'\n  if (activity_takeover_get_metric(metric, history_len, history)) return true;')
f.write_text(s)
print('Firmware sources patched. No compilation performed. CONFIG_SHELL and activity support are required.')
