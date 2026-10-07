package com.miniscreen.takeover;

final class PebbleText {
    static String status(String state){int id=state.equals("webview_update_required")?R.string.pebble_webview_update:state.equals("webview_isolation_failed")||state.equals("webview_secure_context_missing")?R.string.pebble_webview_isolation:state.equals("webview_shared_memory_missing")||state.equals("webview_wasm_missing")?R.string.pebble_webview_memory:state.equals("runtime_missing")?R.string.pebble_runtime_missing:state.equals("watchface_missing")?R.string.pebble_no_face:state.equals("booting")||state.isEmpty()?R.string.pebble_starting:state.equals("installing")?R.string.pebble_installing:state.equals("running")?R.string.pebble_running:state.equals("health_bridge_missing")?R.string.pebble_bridge_missing:state.equals("webview_incompatible")?R.string.pebble_webview:state.equals("no_configuration")?R.string.pebble_no_config:R.string.pebble_failed;return I18n.get(id);}
}
