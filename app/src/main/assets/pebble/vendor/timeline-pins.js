// Timeline pins (and their reminders) for the JS phone.
//
// Converts the public Pebble timeline pin JSON — the same shape CloudPebble's
// libpebble emu_send_pin() produces — into a serialized TimelineItem and
// writes it to the watch's pin database over BlobDB. This is the piece the
// server-side emulator delegated to pypkjs; here the browser is the phone, so
// we build the item and INSERT it directly, the same way phone-extras.js
// inserts a notification.
//
// Wire formats verified against firmware source:
//   TimelineItem header + type/flags/layout  — services/timeline/item.h
//   BlobDBIdPins = 0x01, INSERT/DELETE opcodes — services/blob_db/api.h,
//                                                 blob_db/endpoint_private.h
//   AttributeId values                        — services/timeline/attribute.h
//   Timeline resource ids (system:// icons)   — timeline_resource_ids.auto.h

const EP_BLOB_DB = 0xb1db;
const BLOB_DB_ID_PINS = 0x01;
const BLOB_DB_ID_REMINDERS = 0x03;
const CMD_INSERT = 0x01, CMD_DELETE = 0x04;

// TimelineItemType (item.h)
const ITEM_TYPE_PIN = 2, ITEM_TYPE_REMINDER = 3;
// TimelineItemFlag (item.h): a pin must be visible to show in the timeline.
const FLAG_VISIBLE = 0x01;

// LayoutId (layout_layer.h). Public pin layout.type -> id.
const LAYOUTS = {
  genericPin: 1, calendarPin: 2, reminder: 3, notification: 4,
  commNotification: 5, weatherPin: 6, sportsPin: 7, alarmPin: 8, healthPin: 9,
};
const LAYOUT_GENERIC = 1, LAYOUT_REMINDER = 3;

// AttributeId (attribute.h)
const A = {
  title: 1, subtitle: 2, body: 3, tinyIcon: 4, smallIcon: 5, largeIcon: 6,
  ancsAction: 7, cannedResponses: 8, shortTitle: 9, iconPin: 10,
  locationName: 11, sender: 12, launchCode: 13, lastUpdated: 14,
  headings: 25, paragraphs: 26, primaryColor: 27, backgroundColor: 28,
  secondaryColor: 29, appName: 30, displayRecurring: 31, emojiSupported: 33,
};
// Which attribute keys carry a u32 resource id, a u32 int, or a 1-byte color.
const ICON_ATTRS = new Set(['tinyIcon', 'smallIcon', 'largeIcon', 'iconPin']);
const U32_ATTRS = new Set(['launchCode', 'lastUpdated']);
const COLOR_ATTRS = new Set(['primaryColor', 'backgroundColor', 'secondaryColor']);

// TimelineItemActionType (item.h). Public action.type -> id.
const ACTIONS = {
  ancsNegative: 0x01, generic: 0x02, response: 0x03, dismiss: 0x04, http: 0x05,
  snooze: 0x06, openWatchApp: 0x07, empty: 0x08, remove: 0x09, openPin: 0x0a,
  complete: 0x10, postpone: 0x11, remoteRemove: 0x12,
};

// system://images/<NAME> -> timeline resource id (generated from
// timeline_resource_ids.auto.h). Unknown names fall back to a generic pin.
const ICONS = {NOTIFICATION_GENERIC:2147483649, TIMELINE_MISSED_CALL:2147483650, NOTIFICATION_REMINDER:2147483651, NOTIFICATION_FLAG:2147483652, NOTIFICATION_WHATSAPP:2147483653, NOTIFICATION_TWITTER:2147483654, NOTIFICATION_TELEGRAM:2147483655, NOTIFICATION_GOOGLE_HANGOUTS:2147483656, NOTIFICATION_GMAIL:2147483657, NOTIFICATION_FACEBOOK_MESSENGER:2147483658, NOTIFICATION_FACEBOOK:2147483659, AUDIO_CASSETTE:2147483660, ALARM_CLOCK:2147483661, TIMELINE_WEATHER:2147483662, TIMELINE_SUN:2147483664, TIMELINE_SPORTS:2147483665, GENERIC_EMAIL:2147483667, AMERICAN_FOOTBALL:2147483668, TIMELINE_CALENDAR:2147483669, TIMELINE_BASEBALL:2147483670, BIRTHDAY_EVENT:2147483671, CAR_RENTAL:2147483672, CLOUDY_DAY:2147483673, CRICKET_GAME:2147483674, DINNER_RESERVATION:2147483675, GENERIC_WARNING:2147483676, GLUCOSE_MONITOR:2147483677, HOCKEY_GAME:2147483678, HOTEL_RESERVATION:2147483679, LIGHT_RAIN:2147483680, LIGHT_SNOW:2147483681, MOVIE_EVENT:2147483682, MUSIC_EVENT:2147483683, NEWS_EVENT:2147483684, PARTLY_CLOUDY:2147483685, PAY_BILL:2147483686, RADIO_SHOW:2147483687, SCHEDULED_EVENT:2147483688, SOCCER_GAME:2147483689, STOCKS_EVENT:2147483690, RESULT_DELETED:2147483691, CHECK_INTERNET_CONNECTION:2147483692, GENERIC_SMS:2147483693, RESULT_MUTE:2147483694, RESULT_SENT:2147483695, WATCH_DISCONNECTED:2147483696, DURING_PHONE_CALL:2147483697, TIDE_IS_HIGH:2147483698, RESULT_DISMISSED:2147483699, HEAVY_RAIN:2147483700, HEAVY_SNOW:2147483701, SCHEDULED_FLIGHT:2147483702, GENERIC_CONFIRMATION:2147483703, DAY_SEPARATOR:2147483704, NO_EVENTS:2147483705, NOTIFICATION_BLACKBERRY_MESSENGER:2147483706, NOTIFICATION_INSTAGRAM:2147483707, NOTIFICATION_MAILBOX:2147483708, NOTIFICATION_GOOGLE_INBOX:2147483709, RESULT_FAILED:2147483710, GENERIC_QUESTION:2147483711, NOTIFICATION_OUTLOOK:2147483712, RAINING_AND_SNOWING:2147483713, REACHED_FITNESS_GOAL:2147483714, NOTIFICATION_LINE:2147483715, NOTIFICATION_SKYPE:2147483716, NOTIFICATION_SNAPCHAT:2147483717, NOTIFICATION_VIBER:2147483718, NOTIFICATION_WECHAT:2147483719, NOTIFICATION_YAHOO_MAIL:2147483720, TV_SHOW:2147483721, BASKETBALL:2147483722, DISMISSED_PHONE_CALL:2147483723, NOTIFICATION_GOOGLE_MESSENGER:2147483724, NOTIFICATION_HIPCHAT:2147483725, INCOMING_PHONE_CALL:2147483726, NOTIFICATION_KAKAOTALK:2147483727, NOTIFICATION_KIK:2147483728, NOTIFICATION_LIGHTHOUSE:2147483729, LOCATION:2147483730, SETTINGS:2147483731, SUNRISE:2147483732, SUNSET:2147483733, RESULT_UNMUTE:2147483734, RESULT_UNMUTE_ALT:2147483742, DURING_PHONE_CALL_CENTERED:2147483743, TIMELINE_EMPTY_CALENDAR:2147483744, THUMBS_UP:2147483745, ARROW_UP:2147483746, ARROW_DOWN:2147483747, ACTIVITY:2147483748, SLEEP:2147483749, REWARD_BAD:2147483750, REWARD_GOOD:2147483751, REWARD_AVERAGE:2147483752, CALORIES:2147483753, DISTANCE:2147483754, DURATION:2147483755, PACE:2147483756, RUN:2147483757, NOTIFICATION_FACETIME:2147483758, NOTIFICATION_AMAZON:2147483759, NOTIFICATION_GOOGLE_MAPS:2147483760, NOTIFICATION_GOOGLE_PHOTOS:2147483761, NOTIFICATION_IOS_PHOTOS:2147483762, NOTIFICATION_LINKEDIN:2147483763, NOTIFICATION_SLACK:2147483764, SMART_ALARM:2147483765, HEART:2147483766, BLE_HRM_SHARING:2147483767, NOTIFICATION_BEEPER:2147483768, NOTIFICATION_DISCORD:2147483769, NOTIFICATION_BLUESKY:2147483770, NOTIFICATION_DUOLINGO:2147483771, NOTIFICATION_ELEMENT:2147483772, NOTIFICATION_GOOGLE_CHAT:2147483773, NOTIFICATION_GOOGLE_TASKS:2147483774, NOTIFICATION_HOME_ASSISTANT:2147483775, NOTIFICATION_STEAM:2147483776, NOTIFICATION_TEAMS:2147483777, NOTIFICATION_THREADS:2147483778, NOTIFICATION_UNIFI_PROTECT:2147483779, NOTIFICATION_ZOOM:2147483780, NOTIFICATION_EBAY:2147483781, NOTIFICATION_YOUTUBE:2147483782, NOTIFICATION_SIGNAL:2147483783, NOTIFICATION_TWITCH:2147483784, NOTIFICATION_AIRMAIL:2147483785, NOTIFICATION_REDDIT:2147483786, NOTIFICATION_SWARM:2147483787, NOTIFICATION_TAPO:2147483788};
const DEFAULT_ICON = ICONS.NOTIFICATION_GENERIC;

const enc = new TextEncoder();

function attrBytes(id, data) {
  const d = typeof data === 'string' ? enc.encode(data) : data;
  const out = new Uint8Array(3 + d.length);
  out[0] = id;
  out[1] = d.length & 0xff; out[2] = d.length >> 8;
  out.set(d, 3);
  return out;
}
function u32le(v) {
  const b = new Uint8Array(4);
  new DataView(b.buffer).setUint32(0, v >>> 0, true);
  return b;
}
function cat(parts) {
  const total = parts.reduce((n, p) => n + p.length, 0);
  const out = new Uint8Array(total);
  let off = 0;
  for (const p of parts) { out.set(p, off); off += p.length; }
  return out;
}

// "system://images/NAME" | "NAME" | number -> u32 resource id.
function iconId(v) {
  if (v == null) return null;
  if (typeof v === 'number') return v >>> 0;
  const name = String(v).replace(/^system:\/\/images\//, '').toUpperCase();
  return ICONS[name] != null ? ICONS[name] : DEFAULT_ICON;
}

// GColor8 is 0bAARRGGBB (2 bits/channel). "#rrggbb"/"#rgb"/named -> byte.
const NAMED_COLORS = {
  white: 0xff, black: 0xc0, red: 0xf0, green: 0xcc, blue: 0xc3,
  yellow: 0xfc, cyan: 0xcf, magenta: 0xf3, clear: 0x00,
};
function colorByte(v) {
  if (v == null) return null;
  if (typeof v === 'number') return v & 0xff;
  const s = String(v).trim().toLowerCase();
  if (NAMED_COLORS[s] != null) return NAMED_COLORS[s];
  let m = /^#?([0-9a-f]{6})$/.exec(s);
  if (m) {
    const n = parseInt(m[1], 16);
    const r = (n >> 16) & 0xff, g = (n >> 8) & 0xff, b = n & 0xff;
    return 0xc0 | ((r >> 6) << 4) | ((g >> 6) << 2) | (b >> 6);
  }
  m = /^#?([0-9a-f]{3})$/.exec(s);
  if (m) {
    const r = parseInt(m[1][0], 16) * 17, g = parseInt(m[1][1], 16) * 17, b = parseInt(m[1][2], 16) * 17;
    return 0xc0 | ((r >> 6) << 4) | ((g >> 6) << 2) | (b >> 6);
  }
  return null;
}

function toSeconds(t) {
  if (t == null) return Math.floor(Date.now() / 1000);
  if (typeof t === 'number') return t > 1e11 ? Math.floor(t / 1000) : t; // ms vs s
  const ms = Date.parse(t);
  return Number.isNaN(ms) ? Math.floor(Date.now() / 1000) : Math.floor(ms / 1000);
}

// Parse a UUID string "xxxxxxxx-xxxx-..." -> 16 bytes.
function parseUuid(s) {
  const hex = String(s).replace(/[^0-9a-fA-F]/g, '');
  if (hex.length !== 32) return null;
  const out = new Uint8Array(16);
  for (let i = 0; i < 16; i++) out[i] = parseInt(hex.substr(i * 2, 2), 16);
  return out;
}
function formatUuid(b) {
  const h = [...b].map((x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

// RFC 4122 v5 (SHA-1) UUID, matching CloudPebble's id_to_uuid():
//   UUID.v5(id + ".pins.developer.getpebble.com", DNS namespace).
const DNS_NS = parseUuid('6ba7b810-9dad-11d1-80b4-00c04fd430c8');
export async function pinUuidFromId(id) {
  const name = enc.encode(String(id) + '.pins.developer.getpebble.com');
  const buf = new Uint8Array(DNS_NS.length + name.length);
  buf.set(DNS_NS, 0); buf.set(name, DNS_NS.length);
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-1', buf));
  const u = digest.slice(0, 16);
  u[6] = (u[6] & 0x0f) | 0x50; // version 5
  u[8] = (u[8] & 0x3f) | 0x80; // variant
  return u;
}

// Turn one attribute list (from a pin/reminder layout, minus the reserved
// keys) into serialized attribute bytes + count.
function buildAttrs(layout) {
  const attrs = [];
  for (const [key, val] of Object.entries(layout || {})) {
    if (key === 'type' || val == null) continue;
    const id = A[key];
    if (id == null) continue; // unknown key -> skip (e.g. layout-only hints)
    if (ICON_ATTRS.has(key)) attrs.push(attrBytes(id, u32le(iconId(val))));
    else if (U32_ATTRS.has(key)) attrs.push(attrBytes(id, u32le(toSeconds(val))));
    else if (COLOR_ATTRS.has(key)) {
      const c = colorByte(val); if (c != null) attrs.push(attrBytes(id, new Uint8Array([c])));
    } else if (key === 'headings' || key === 'paragraphs') {
      // string arrays are joined with NUL like canned responses
      const s = Array.isArray(val) ? val.join('\0') : String(val);
      attrs.push(attrBytes(id, s));
    } else {
      attrs.push(attrBytes(id, String(val)));
    }
  }
  return attrs;
}

// One action -> [action_id][type][num_attrs] + attrs.
function buildAction(action, index) {
  const type = ACTIONS[action.type] != null ? ACTIONS[action.type] : ACTIONS.openWatchApp;
  const attrs = [];
  if (action.title != null) attrs.push(attrBytes(A.title, String(action.title)));
  if (action.launchCode != null) attrs.push(attrBytes(A.launchCode, u32le(action.launchCode >>> 0)));
  if (Array.isArray(action.cannedResponses) && action.cannedResponses.length) {
    attrs.push(attrBytes(A.cannedResponses, action.cannedResponses.join('\0')));
  }
  return cat([new Uint8Array([action.id != null ? action.id : index + 1, type, attrs.length]), ...attrs]);
}

// Build a serialized TimelineItem (pin or reminder).
function serializeItem({ uuid, parentUuid, timestamp, duration, type, layoutId, layout, actions }) {
  const attrs = buildAttrs(layout);
  const actionList = (actions || []).map(buildAction);
  const payload = cat([...attrs, ...actionList]);
  const header = new Uint8Array(46);
  const dv = new DataView(header.buffer);
  header.set(uuid, 0);
  if (parentUuid) header.set(parentUuid, 16); // else zeros
  dv.setUint32(32, timestamp >>> 0, true);
  dv.setUint16(36, Math.min(duration >>> 0, 0xffff), true);
  header[38] = type;
  header[39] = FLAG_VISIBLE;
  header[40] = 0; // status: must be 0
  header[41] = layoutId;
  dv.setUint16(42, payload.length, true);
  header[44] = attrs.length;
  header[45] = actionList.length;
  return cat([header, payload]);
}

// Sends BlobDB INSERT/DELETE and tracks the response by token (shares the
// endpoint with AppInstaller/NotificationSender — tokens disambiguate).
export class PinSender {
  constructor(phone, log = () => {}) {
    this.phone = phone;
    this.log = log;
    this.token = (Math.random() * 0xfffe + 1) & 0xffff;
    this.waiter = null;
    this.lastToken = 0;
    const prev = phone.ppHandlers.get(EP_BLOB_DB);
    phone.onPP(EP_BLOB_DB, (p) => {
      if (p.length >= 3 && this.waiter) {
        const token = new DataView(p.buffer, p.byteOffset).getUint16(0, true);
        if (token === this.lastToken) {
          const w = this.waiter; this.waiter = null;
          w({ status: p[2] });
          return;
        }
      }
      if (prev) prev(p);
    });
  }

  _blobdb(cmd, dbId, key, value) {
    const token = this.lastToken = (this.token = (this.token % 0xfffe) + 1);
    const valLen = value ? value.length : 0;
    const msg = new Uint8Array(1 + 2 + 1 + 1 + key.length + (value ? 2 + valLen : 0));
    const dv = new DataView(msg.buffer);
    msg[0] = cmd;
    dv.setUint16(1, token, true);
    msg[3] = dbId;
    msg[4] = key.length;
    msg.set(key, 5);
    if (value) { dv.setUint16(5 + key.length, valLen, true); msg.set(value, 7 + key.length); }
    return new Promise((resolve, reject) => {
      const t = setTimeout(() => { this.waiter = null; reject(new Error('blobdb timeout')); }, 5000);
      this.waiter = (r) => {
        clearTimeout(t);
        if (r.status === 0x01) resolve(r);
        else reject(new Error(`blobdb rejected, status 0x${r.status.toString(16)}`));
      };
      this.phone.sendPP(EP_BLOB_DB, msg);
    });
  }

  // pin: the public timeline pin object (parsed). Returns the pin's uuid.
  async sendPin(pin) {
    const uuid = pin.guid ? parseUuid(pin.guid) : await pinUuidFromId(pin.id);
    if (!uuid) throw new Error('pin needs a valid guid or id');
    const layout = pin.layout || {};
    const timestamp = toSeconds(pin.time);
    const value = serializeItem({
      uuid,
      timestamp,
      duration: pin.duration || 0,
      type: ITEM_TYPE_PIN,
      layoutId: LAYOUTS[layout.type] || LAYOUT_GENERIC,
      layout,
      actions: pin.actions,
    });
    await this._blobdb(CMD_INSERT, BLOB_DB_ID_PINS, uuid, value);
    this.log(`pin inserted (${formatUuid(uuid)})`);

    // Optional reminders: each is its own reminder item that points at the pin.
    if (Array.isArray(pin.reminders)) {
      for (let i = 0; i < pin.reminders.length; i++) {
        const r = pin.reminders[i];
        const rUuid = await pinUuidFromId((pin.id || formatUuid(uuid)) + '-reminder-' + i);
        const rValue = serializeItem({
          uuid: rUuid,
          parentUuid: uuid,
          timestamp: toSeconds(r.time),
          duration: 0,
          type: ITEM_TYPE_REMINDER,
          layoutId: LAYOUT_REMINDER,
          layout: r.layout || {},
        });
        try { await this._blobdb(CMD_INSERT, BLOB_DB_ID_REMINDERS, rUuid, rValue); }
        catch (e) { this.log('reminder insert failed: ' + e.message); }
      }
    }
    return formatUuid(uuid);
  }

  // idOrUuid: the pin id string, or the already-derived uuid string.
  async deletePin(idOrUuid) {
    const uuid = parseUuid(idOrUuid) || await pinUuidFromId(idOrUuid);
    await this._blobdb(CMD_DELETE, BLOB_DB_ID_PINS, uuid, null);
    this.log(`pin deleted (${formatUuid(uuid)})`);
    return formatUuid(uuid);
  }
}

export { serializeItem, iconId, colorByte, buildAttrs };
