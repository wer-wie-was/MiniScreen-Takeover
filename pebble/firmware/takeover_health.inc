/* MiniScreen Takeover host-data bridge for Core Devices PebbleOS.
 * Included at the END of fw/services/activity/activity.c, kernel side only.
 * Current-day steps and current pulse are real host observations, not sensor simulations.
 * Historical samples are deliberately unavailable until imported independently.
 */
#ifdef CONFIG_SHELL
#include <pbl/shell/shell.h>
#include <stdlib.h>
#include <errno.h>
#include <limits.h>

static struct {
  bool active;
  int32_t steps;
  int32_t bpm;
  time_t pulse_time;
  time_t day_start;
  time_t received;
} s_takeover_health;

bool activity_takeover_heart_enabled(void) { return s_takeover_health.active ? s_takeover_health.bpm > 0 : activity_prefs_heart_rate_is_enabled(); }

bool activity_takeover_get_metric(ActivityMetric metric, uint32_t count, int32_t *history) {
  if (!s_takeover_health.active) return false;
  const bool step_metric = metric == ActivityMetricStepCount;
  const bool heart_metric = metric == ActivityMetricHeartRateRawBPM ||
      metric == ActivityMetricHeartRateFilteredBPM ||
      metric == ActivityMetricHeartRateRawUpdatedTimeUTC ||
      metric == ActivityMetricHeartRateFilteredUpdatedTimeUTC;
  if (!step_metric && !heart_metric) return false;
  for (uint32_t i = 0; i < count; ++i) history[i] = -1;
  if (count == 0) return true;
  pbl_mutex_lock(&s_activity_state.mutex, PBL_FOREVER);
  const time_t now = rtc_get_time();
  if (now >= s_takeover_health.received && now - s_takeover_health.received <= 180) {
    if (step_metric && s_takeover_health.steps >= 0 &&
        now >= s_takeover_health.day_start && now - s_takeover_health.day_start < 24 * 3600) {
      history[0] = s_takeover_health.steps;
    } else if (heart_metric && s_takeover_health.bpm > 0) {
      if (metric == ActivityMetricHeartRateRawUpdatedTimeUTC ||
          metric == ActivityMetricHeartRateFilteredUpdatedTimeUTC) {
        history[0] = s_takeover_health.pulse_time;
      } else {
        history[0] = s_takeover_health.bpm;
      }
    }
  }
  pbl_mutex_unlock(&s_activity_state.mutex);
  return true;
}

static int prv_takeover_health(const struct pbl_shell *sh, size_t argc, char **argv) {
  if (argc == 2 && strcmp(argv[1], "version") == 0) {
    pbl_shell_print(sh, "TAKEOVER_HEALTH_V1");
    return 0;
  }
  if (argc != 5 || !s_activity_initialized) return -EINVAL;
  long values[4];
  for (size_t i = 0; i < 4; ++i) {
    char *end;
    values[i] = strtol(argv[i + 1], &end, 10);
    if (*end != '\0') return -EINVAL;
  }
  if (values[0] < -1 || values[0] > INT_MAX || values[1] < -1 || values[1] > 1000 ||
      values[2] < 0 || values[3] < 0) return -EINVAL;
  pbl_mutex_lock(&s_activity_state.mutex, PBL_FOREVER);
  s_takeover_health.active = true;
  s_takeover_health.steps = values[0];
  s_takeover_health.bpm = values[1];
  s_takeover_health.pulse_time = values[2];
  s_takeover_health.day_start = values[3];
  s_takeover_health.received = rtc_get_time();
  // Keep the activity service's current state consistent for existing clients.
  if (values[0] >= 0) s_activity_state.step_data.steps = values[0];
  s_activity_state.hr.metrics.current_bpm = values[1] > 0 ? values[1] : 0;
  s_activity_state.hr.metrics.last_stable_bpm = values[1] > 0 ? values[1] : 0;
  s_activity_state.hr.metrics.current_update_time_utc = values[2];
  s_activity_state.hr.metrics.last_stable_bpm_update_time_utc = values[2];
  pbl_mutex_unlock(&s_activity_state.mutex);
  PebbleEvent event = {
    .type = PEBBLE_HEALTH_SERVICE_EVENT,
    .health_event = {.type = HealthEventSignificantUpdate,
                     .data.significant_update = {.day_id = 0}},
  };
  event_put(&event);
  pbl_shell_print(sh, "TAKEOVER_HEALTH_OK");
  return 0;
}
PBL_SHELL_CMD_ARG_REGISTER(takeover_health, NULL, "Host steps, pulse, timestamp, day start",
                         prv_takeover_health, 0, 0);
#else
bool activity_takeover_heart_enabled(void) { return activity_prefs_heart_rate_is_enabled(); }
bool activity_takeover_get_metric(ActivityMetric metric, uint32_t count, int32_t *history) {
  return false;
}
#endif
