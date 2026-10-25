//! Win32 side: finds the taskbar, keeps the pill on it, opens the flyout on hover,
//! places the lyrics overlay and gets out of the way of fullscreen apps.

use std::sync::atomic::Ordering;
use std::time::{Duration, Instant};

use tauri::{AppHandle, Emitter, Manager, WebviewWindow};
use windows::core::{w, PCWSTR};
use windows::Win32::Foundation::{HWND, RECT};
use windows::Win32::UI::Input::KeyboardAndMouse::{GetAsyncKeyState, VK_CONTROL};
use windows::Win32::Graphics::Gdi::{GetMonitorInfoW, MonitorFromWindow, MONITORINFO, MONITOR_DEFAULTTONEAREST};
use windows::Win32::UI::Shell::{SHQueryUserNotificationState, QUNS_PRESENTATION_MODE, QUNS_RUNNING_D3D_FULL_SCREEN};
use windows::Win32::UI::WindowsAndMessaging::{
    FindWindowExW, FindWindowW, GetClassNameW, GetForegroundWindow, GetTopWindow, GetWindow, GetWindowLongPtrW,
    GetWindowRect, SetWindowLongPtrW, SetWindowPos, ShowWindow, GWL_EXSTYLE, GW_HWNDNEXT, HWND_TOPMOST,
    SWP_NOACTIVATE, SWP_SHOWWINDOW, SW_HIDE, WS_EX_NOACTIVATE, WS_EX_TOOLWINDOW,
};

use crate::Shared;

const OPEN_DELAY: Duration = Duration::from_millis(120);
const CLOSE_DELAY: Duration = Duration::from_millis(300);
pub const FLYOUT_W: f64 = 440.0;
const OVERLAY_W: f64 = 680.0;

#[derive(Clone, Copy, PartialEq, Default, Debug)]
struct Rect {
    x: i32,
    y: i32,
    w: i32,
    h: i32,
}

impl Rect {
    fn contains(&self, px: f64, py: f64) -> bool {
        px >= self.x as f64 && px < (self.x + self.w) as f64 && py >= self.y as f64 && py < (self.y + self.h) as f64
    }
}

struct Taskbar {
    hwnd: HWND,
    bar: RECT,
    tray_left: i32,
}

fn hwnd_of(win: &WebviewWindow) -> Option<HWND> {
    win.hwnd().ok().map(|h| HWND(h.0 as _))
}

/// Tool window (no Alt-Tab entry) that never takes focus from the app you are using.
fn prepare(hwnd: HWND) {
    unsafe {
        let ex = GetWindowLongPtrW(hwnd, GWL_EXSTYLE);
        SetWindowLongPtrW(hwnd, GWL_EXSTYLE, ex | (WS_EX_TOOLWINDOW.0 | WS_EX_NOACTIVATE.0) as isize);
    }
}

fn place(hwnd: HWND, r: Rect) {
    unsafe {
        let _ = SetWindowPos(hwnd, Some(HWND_TOPMOST), r.x, r.y, r.w, r.h, SWP_NOACTIVATE | SWP_SHOWWINDOW);
    }
}

fn hide(hwnd: HWND) {
    unsafe {
        let _ = ShowWindow(hwnd, SW_HIDE);
    }
}

fn taskbar() -> Option<Taskbar> {
    unsafe {
        let tb = FindWindowW(w!("Shell_TrayWnd"), PCWSTR::null()).ok()?;
        let mut bar = RECT::default();
        GetWindowRect(tb, &mut bar).ok()?;
        let tray_left = FindWindowExW(Some(tb), None, w!("TrayNotifyWnd"), PCWSTR::null())
            .ok()
            .and_then(|t| {
                let mut r = RECT::default();
                GetWindowRect(t, &mut r).ok()?;
                (r.left > bar.left).then_some(r.left)
            })
            .unwrap_or(bar.right - 220);
        Some(Taskbar { hwnd: tb, bar, tray_left })
    }
}

fn ctrl_down() -> bool {
    unsafe { (GetAsyncKeyState(VK_CONTROL.0 as i32) as u16 & 0x8000) != 0 }
}

/// Windows raises the taskbar over other topmost windows whenever you switch or minimize apps.
/// Walks the z-order from the top: whichever of the two comes first is in front.
fn is_above(win: HWND, taskbar: HWND) -> bool {
    unsafe {
        let mut h = match GetTopWindow(None) {
            Ok(h) => h,
            Err(_) => return true,
        };
        for _ in 0..4096 {
            if h == win {
                return true;
            }
            if h == taskbar {
                return false;
            }
            h = match GetWindow(h, GW_HWNDNEXT) {
                Ok(n) => n,
                Err(_) => return true,
            };
        }
        true
    }
}

/// A game, fullscreen video or presentation. (Not QUNS_BUSY: Windows reports that for ordinary apps too.)
fn fullscreen_app_running() -> bool {
    unsafe {
        if matches!(SHQueryUserNotificationState(), Ok(s) if s == QUNS_RUNNING_D3D_FULL_SCREEN || s == QUNS_PRESENTATION_MODE) {
            return true;
        }
        let fg = GetForegroundWindow();
        if fg.is_invalid() {
            return false;
        }
        let mut buf = [0u16; 64];
        let n = GetClassNameW(fg, &mut buf) as usize;
        let class = String::from_utf16_lossy(&buf[..n.min(buf.len())]);
        if matches!(class.as_str(), "Progman" | "WorkerW" | "Shell_TrayWnd" | "Shell_SecondaryTrayWnd") {
            return false;
        }
        let mut r = RECT::default();
        if GetWindowRect(fg, &mut r).is_err() {
            return false;
        }
        let mut mi = MONITORINFO { cbSize: std::mem::size_of::<MONITORINFO>() as u32, ..Default::default() };
        if !GetMonitorInfoW(MonitorFromWindow(fg, MONITOR_DEFAULTTONEAREST), &mut mi).as_bool() {
            return false;
        }
        // Maximized windows stop at the taskbar; only true fullscreen covers the whole monitor.
        let m = mi.rcMonitor;
        r.left <= m.left && r.top <= m.top && r.right >= m.right && r.bottom >= m.bottom
    }
}

pub fn run(app: AppHandle) {
    let shared = app.state::<Shared>().inner().clone();
    let (Some(pill), Some(flyout), Some(overlay)) = (
        app.get_webview_window("pill"),
        app.get_webview_window("flyout"),
        app.get_webview_window("overlay"),
    ) else {
        return;
    };
    let _ = overlay.set_ignore_cursor_events(true);
    let (Some(h_pill), Some(h_fly), Some(h_ov)) = (hwnd_of(&pill), hwnd_of(&flyout), hwnd_of(&overlay)) else {
        return;
    };
    for h in [h_pill, h_fly, h_ov] {
        prepare(h);
    }

    let mut pill_rect = Rect::default();
    let mut fly_rect = Rect::default();
    let mut ov_rect = Rect::default();
    let mut ov_shown = false;
    let mut ov_interactive = false;
    let mut hidden_for_fullscreen = false;
    let mut open = false;
    let mut hover_since: Option<Instant> = None;
    let mut last_inside = Instant::now();
    let mut last_layout = Instant::now() - Duration::from_secs(5);

    loop {
        std::thread::sleep(Duration::from_millis(40));
        let scale = pill.scale_factor().unwrap_or(1.0);
        let px = |v: f64| (v * scale).round() as i32;

        // Layout: cheap to compute, only touch windows when something moved (or once a second for z-order).
        if let Some(tb) = taskbar() {
            let pill_w = *shared.pill_w.lock().unwrap();
            let bar_h = tb.bar.bottom - tb.bar.top;
            let want_pill = Rect {
                x: tb.tray_left - px(pill_w) - px(8.0),
                y: tb.bar.top + (bar_h - px(40.0)) / 2,
                w: px(pill_w),
                h: px(40.0),
            };
            let fly_h = *shared.flyout_h.lock().unwrap();
            let fw = px(FLYOUT_W);
            let want_fly = Rect {
                x: (want_pill.x + want_pill.w - fw).min(tb.bar.right - fw - px(8.0)),
                y: tb.bar.top - px(fly_h) - px(8.0),
                w: fw,
                h: px(fly_h),
            };
            let (dx, dy) = {
                let s = shared.settings.lock().unwrap();
                (s.lyr_dx, s.lyr_dy)
            };
            // Anchored by its bottom-right corner so the text grows up and left.
            let ov_h = px(*shared.overlay_h.lock().unwrap());
            let ov_w = px(OVERLAY_W);
            let want_ov = Rect {
                x: (tb.bar.right - ov_w - px(28.0) + px(dx)).clamp(tb.bar.left, tb.bar.right - ov_w),
                y: (tb.bar.top - ov_h - px(20.0) + px(dy)).max(0),
                w: ov_w,
                h: ov_h,
            };
            let editing = shared.overlay_edit.load(Ordering::Relaxed);

            let periodic = last_layout.elapsed() > Duration::from_secs(1);
            if periodic {
                last_layout = Instant::now();
                hidden_for_fullscreen = fullscreen_app_running();
            }

            if hidden_for_fullscreen {
                if pill_rect != Rect::default() {
                    hide(h_pill);
                    hide(h_ov);
                    pill_rect = Rect::default();
                    ov_shown = false;
                }
            } else {
                if want_pill != pill_rect || periodic || !is_above(h_pill, tb.hwnd) {
                    place(h_pill, want_pill);
                    pill_rect = want_pill;
                }

                let show_ov = {
                    let s = shared.settings.lock().unwrap();
                    let l = shared.lyrics.lock().unwrap();
                    editing || (s.lyrics_on && s.lyr_place == "desktop" && l.status == "synced" && !l.lines.is_empty() && !open)
                };
                if editing != ov_interactive {
                    let _ = overlay.set_ignore_cursor_events(!editing);
                    ov_interactive = editing;
                }
                if show_ov && (!ov_shown || want_ov != ov_rect || periodic) {
                    place(h_ov, want_ov);
                    ov_rect = want_ov;
                    ov_shown = true;
                } else if !show_ov && ov_shown {
                    hide(h_ov);
                    ov_shown = false;
                }
            }

            if open && (want_fly != fly_rect || !is_above(h_fly, tb.hwnd)) {
                place(h_fly, want_fly);
                fly_rect = want_fly;
            }

            // Hover: open after a short dwell on the pill, close shortly after leaving both windows.
            if let Ok(c) = app.cursor_position() {
                // Ctrl + hover over the desktop lyrics unlocks them; "Done" in their toolbar locks them again.
                if !editing && ov_shown && ov_rect.contains(c.x, c.y) && ctrl_down() {
                    crate::set_overlay_edit_inner(&app, true);
                }
                let in_pill = !hidden_for_fullscreen && pill_rect.contains(c.x, c.y);
                let inside = in_pill || (open && fly_rect.contains(c.x, c.y));
                let now = Instant::now();
                if inside {
                    last_inside = now;
                }
                if !open {
                    if in_pill {
                        let since = *hover_since.get_or_insert(now);
                        if now - since >= OPEN_DELAY {
                            open = true;
                            place(h_fly, want_fly);
                            fly_rect = want_fly;
                            shared.flyout_open.store(true, Ordering::Relaxed);
                            let _ = app.emit("flyout", true);
                        }
                    } else {
                        hover_since = None;
                    }
                } else if now - last_inside > CLOSE_DELAY {
                    open = false;
                    hover_since = None;
                    hide(h_fly);
                    shared.flyout_open.store(false, Ordering::Relaxed);
                    let _ = app.emit("flyout", false);
                }
            }
        }
    }
}
