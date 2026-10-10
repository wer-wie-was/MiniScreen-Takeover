# MiniScreen Takeover 0.6.5

Unsigned release APK built successfully; signing is performed by the user.

Legacy WFZ datawidgets now render native icons, values, units and progress rings rather than text alone. configList choices are saved per widget position and profile; masks and date variants are supported. Phone battery frame mapping and statusbar layering are corrected. Optional Health Connect daily distance and active calories require separate permissions. Missing or unavailable metrics display --. Weather and workout distance remain placeholders. Manufacturer firmware artwork is approximated.

## Validation
Java syntax and resource XML checked statically. Android release compilation and release lint completed successfully. No emulator run or device visual test was performed. The supplied Toro Rosso and WfDharma XML definitions informed the implementation, including WfDharma duplicate widget IDs and Toro Rosso battery frames.

## Device verification after an explicitly requested build
Import both supplied faces again to refresh compatibility reports. Check that widget outlines/icons and -- remain visible with health disabled. Grant steps and pulse and check fresh data, then revoke permission. Select steps/pulse/battery alternatives, switch profile and restart to verify persistence. Check date variants, masks, circular clipping and scaling in preview and rear display. Verify phone battery frame boundaries (0/5/6/95/96/100 percent) and foreground statusbar layering. Enable daily distance/active calories independently and test missing data and local midnight. Weather and workout distance must remain --.
