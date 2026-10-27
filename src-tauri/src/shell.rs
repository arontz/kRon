//! Win32 side: finds the taskbar, keeps the pill on it, opens the flyout on hover,
//! places the lyrics overlay and gets out of the way of fullscreen apps.

use std::sync::atomic::Ordering;
use std::time::{Duration, Instant};

use tauri::{AppHandle, Emitter, Manager, WebviewWindow};
use windows::core::{w, PCWSTR};
use windows::Win32::Foundation::{HWND, RECT};
use windows::Win32::UI::Input::KeyboardAndMouse::{GetAsyncKeyState, VK_CONTROL};
use windows::Win32::Foundation::ERROR_SUCCESS;
use windows::Win32::System::Registry::{RegGetValueW, HKEY_CURRENT_USER, RRF_RT_REG_DWORD};
use windows::Win32::System::Com::{CoCreateInstance, CoInitializeEx, CLSCTX_INPROC_SERVER, COINIT_MULTITHREADED};
use windows::Win32::UI::Accessibility::{CUIAutomation, IUIAutomation, TreeScope_Descendants};
use windows::Win32::Graphics::Gdi::{GetMonitorInfoW, MonitorFromWindow, MONITORINFO, MONITOR_DEFAULTTONEAREST};
use windows::Win32::UI::Shell::{SHQueryUserNotificationState, QUNS_PRESENTATION_MODE, QUNS_RUNNING_D3D_FULL_SCREEN};
use windows::Win32::UI::WindowsAndMessaging::{
    FindWindowExW, FindWindowW, GetClassNameW, GetForegroundWindow, GetTopWindow, GetWindow, GetWindowLongPtrW,
    GetWindowRect, SetWindowLongPtrW, SetWindowPos, ShowWindow, GWLP_HWNDPARENT, GWL_EXSTYLE, GW_HWNDNEXT,
    HWND_TOPMOST,
    SWP_NOACTIVATE, SWP_SHOWWINDOW, SW_HIDE, WS_EX_NOACTIVATE, WS_EX_TOOLWINDOW,
};

use crate::Shared;

const OPEN_DELAY: Duration = Duration::from_millis(120);
const CLOSE_DELAY: Duration = Duration::from_millis(300);
/// How long the card's fly-out animation runs before its window is hidden.
const DOCK_OUT: Duration = Duration::from_millis(240);
pub const FLYOUT_W: f64 = 440.0;
/// Dragging the pill this close to a side edge docks it there as a vertical column…
const DOCK_SNAP: f64 = 24.0;
/// …and it takes this much further drag away from the edge to undock it again.
const DOCK_RELEASE: f64 = 140.0;

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

/// Make the taskbar the window's owner. While Start (and similar flyouts) is open, Windows 11 moves the taskbar into a
/// higher layer that paints over ordinary topmost windows, hiding the pill. A window owned by the taskbar stays above
/// it in that layer.
fn set_owner(hwnd: HWND, owner: HWND) {
    unsafe {
        SetWindowLongPtrW(hwnd, GWLP_HWNDPARENT, owner.0 as isize);
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

/// Whether the Windows taskbar uses the light theme ("Choose your default Windows mode" in Settings > Colors).
pub fn taskbar_is_light() -> bool {
    unsafe {
        let mut data: u32 = 0;
        let mut size = std::mem::size_of::<u32>() as u32;
        let r = RegGetValueW(
            HKEY_CURRENT_USER,
            w!("Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"),
            w!("SystemUsesLightTheme"),
            RRF_RT_REG_DWORD,
            None,
            Some(&mut data as *mut u32 as *mut core::ffi::c_void),
            Some(&mut size),
        );
        r == ERROR_SUCCESS && data == 1
    }
}

/// The Windows notification centre / calendar flyout: a CoreWindow docked to the right screen edge,
/// nearly full height. It closes the moment it loses focus, so it only ever shows up as the foreground window.
fn notification_center(fg: HWND, mon: RECT) -> Option<RECT> {
    unsafe {
        if fg.is_invalid() {
            return None;
        }
        let mut buf = [0u16; 64];
        let n = GetClassNameW(fg, &mut buf) as usize;
        if String::from_utf16_lossy(&buf[..n.min(buf.len())]) != "Windows.UI.Core.CoreWindow" {
            return None;
        }
        let mut r = RECT::default();
        GetWindowRect(fg, &mut r).ok()?;
        let (w, h) = (r.right - r.left, r.bottom - r.top);
        let mon_h = mon.bottom - mon.top;
        (r.right >= mon.right - 2 && (250..=700).contains(&w) && h * 10 >= mon_h * 6).then_some(r)
    }
}

/// Where the flyout's first card starts. The flyout window itself is a transparent full-height pane, and its
/// notification card grows with the number of notifications, so ask UI Automation for the highest card-sized element.
fn flyout_content_top(uia: &IUIAutomation, hwnd: HWND) -> Option<i32> {
    unsafe {
        let el = uia.ElementFromHandle(hwnd).ok()?;
        let root = el.CurrentBoundingRectangle().ok()?;
        let all = el.FindAll(TreeScope_Descendants, &uia.CreateTrueCondition().ok()?).ok()?;
        let root_h = root.bottom - root.top;
        let mut top = i32::MAX;
        for i in 0..all.Length().ok()? {
            let Ok(e) = all.GetElement(i) else { continue };
            let Ok(r) = e.CurrentBoundingRectangle() else { continue };
            if r.right - r.left >= 250 && r.bottom - r.top >= 40 && r.bottom - r.top < root_h - 8 && r.top > root.top {
                top = top.min(r.top);
            }
        }
        (top != i32::MAX).then_some(top)
    }
}

/// UI Automation calls cross into another process and can be slow, so they run off the shell thread.
fn uia_thread(shared: Shared) {
    unsafe {
        let _ = CoInitializeEx(None, COINIT_MULTITHREADED);
    }
    let uia: Option<IUIAutomation> = unsafe { CoCreateInstance(&CUIAutomation, None, CLSCTX_INPROC_SERVER).ok() };
    let Some(uia) = uia else { return };
    loop {
        std::thread::sleep(Duration::from_millis(500));
        if !shared.dock_open.load(Ordering::Relaxed) {
            continue;
        }
        let h = shared.dock_hwnd.load(Ordering::Relaxed);
        if h == 0 {
            continue;
        }
        if let Some(top) = flyout_content_top(&uia, HWND(h as _)) {
            shared.dock_top.store(top, Ordering::Relaxed);
        }
    }
}

/// Full area of the monitor the taskbar is on.
fn monitor_rect(taskbar: HWND, fallback: RECT) -> RECT {
    unsafe {
        let mut mi = MONITORINFO { cbSize: std::mem::size_of::<MONITORINFO>() as u32, ..Default::default() };
        if GetMonitorInfoW(MonitorFromWindow(taskbar, MONITOR_DEFAULTTONEAREST), &mut mi).as_bool() {
            mi.rcMonitor
        } else {
            fallback
        }
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
    let (Some(pill), Some(flyout), Some(overlay), Some(dock)) = (
        app.get_webview_window("pill"),
        app.get_webview_window("flyout"),
        app.get_webview_window("overlay"),
        app.get_webview_window("dock"),
    ) else {
        return;
    };
    let _ = overlay.set_ignore_cursor_events(true);
    let (Some(h_pill), Some(h_fly), Some(h_ov), Some(h_dock)) =
        (hwnd_of(&pill), hwnd_of(&flyout), hwnd_of(&overlay), hwnd_of(&dock))
    else {
        return;
    };
    for h in [h_pill, h_fly, h_ov, h_dock] {
        prepare(h);
    }
    {
        let shared = shared.clone();
        std::thread::spawn(move || uia_thread(shared));
    }
    let mut dock_open = false;
    let mut dock_rect = Rect::default();
    let mut dock_hide_at: Option<Instant> = None;

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
    let mut owned_by = HWND(std::ptr::null_mut());
    let mut free: Option<(f64, f64)> = None;
    let mut horiz_w = 340.0;

    loop {
        std::thread::sleep(Duration::from_millis(40));
        let scale = pill.scale_factor().unwrap_or(1.0);
        let px = |v: f64| (v * scale).round() as i32;

        // Layout: cheap to compute, only touch windows when something moved (or once a second for z-order).
        if let Some(tb) = taskbar() {
            // Also redone when Explorer restarts and the taskbar gets a new window handle.
            if tb.hwnd != owned_by {
                set_owner(h_pill, tb.hwnd);
                set_owner(h_fly, tb.hwnd);
                owned_by = tb.hwnd;
            }
            let pill_w = *shared.pill_w.lock().unwrap();
            let pill_h = *shared.pill_h.lock().unwrap();
            let bar_h = tb.bar.bottom - tb.bar.top;
            let mon = monitor_rect(tb.hwnd, tb.bar);
            let (mon_w, mon_h) = (((mon.right - mon.left) as f64 / scale), ((mon.bottom - mon.top) as f64 / scale));
            let (pw, ph) = (px(pill_w), px(pill_h));

            let mut st = shared.settings.lock().unwrap().clone();
            // Remember how wide the horizontal pill is, so we know when it touches a side edge.
            if st.pill_side.is_empty() {
                horiz_w = pill_w;
            }

            // Apply drag distance from the pill's handle. `free` follows the pointer without snapping,
            // so the pill can dock to a side edge and still be dragged away from it.
            let mv = {
                let mut m = shared.pill_move.lock().unwrap();
                std::mem::take(&mut *m)
            };
            if mv.dx != 0.0 || mv.dy != 0.0 || mv.commit {
                let base = if st.pill_placed {
                    (st.pill_x, st.pill_y)
                } else {
                    (
                        (tb.tray_left - mon.left - pw - px(8.0)) as f64 / scale,
                        (tb.bar.top - mon.top + (bar_h - ph) / 2) as f64 / scale,
                    )
                };
                let f = free.get_or_insert(base);
                f.0 = (f.0 + mv.dx).clamp(-300.0, mon_w + 300.0);
                f.1 = (f.1 + mv.dy).clamp(-300.0, mon_h + 300.0);
                let dock = DOCK_SNAP;
                // Lyrics on the taskbar need the wide pill, so it only docks to a side when they aren't.
                let can_dock = !(st.lyrics_on && st.lyr_place == "taskbar");
                let side = match st.pill_side.as_str() {
                    "" if can_dock && f.0 <= dock => "left",
                    "" if can_dock && f.0 + horiz_w >= mon_w - dock => "right",
                    "left" if f.0 <= DOCK_RELEASE => "left",
                    "right" if f.0 + pill_w >= mon_w - DOCK_RELEASE => "right",
                    _ => "",
                };
                let new_x = match side {
                    "left" => 0.0,
                    "right" => (mon_w - pill_w).max(0.0),
                    _ => f.0.clamp(0.0, (mon_w - horiz_w).max(0.0)),
                };
                let new_y = f.1.clamp(0.0, (mon_h - pill_h).max(0.0));
                let side = side.to_string();
                if mv.commit {
                    free = None;
                }
                crate::update_settings_opt(&app, mv.commit, |s| {
                    s.pill_placed = true;
                    s.pill_x = new_x;
                    s.pill_y = new_y;
                    s.pill_side = side;
                });
                st = shared.settings.lock().unwrap().clone();
            }

            let (lx, ly) = if st.pill_placed {
                (st.pill_x, st.pill_y)
            } else {
                (
                    (tb.tray_left - mon.left - pw - px(8.0)) as f64 / scale,
                    (tb.bar.top - mon.top + (bar_h - ph) / 2) as f64 / scale,
                )
            };
            let want_pill = Rect {
                x: match st.pill_side.as_str() {
                    "left" => mon.left,
                    "right" => mon.right - pw,
                    _ => (mon.left + px(lx)).clamp(mon.left, (mon.right - pw).max(mon.left)),
                },
                y: (mon.top + px(ly)).clamp(mon.top, (mon.bottom - ph).max(mon.top)),
                w: pw,
                h: ph,
            };

            // The panel opens above the pill (below if there's no room), or beside it when the pill is docked to a side.
            let fly_h = px(*shared.flyout_h.lock().unwrap());
            let fw = px(FLYOUT_W);
            let gap = px(8.0);
            let want_fly = match st.pill_side.as_str() {
                "left" => Rect {
                    x: want_pill.x + want_pill.w + gap,
                    y: want_pill.y.clamp(mon.top, (mon.bottom - fly_h).max(mon.top)),
                    w: fw,
                    h: fly_h,
                },
                "right" => Rect {
                    x: want_pill.x - fw - gap,
                    y: want_pill.y.clamp(mon.top, (mon.bottom - fly_h).max(mon.top)),
                    w: fw,
                    h: fly_h,
                },
                _ => {
                    let above = want_pill.y - fly_h - gap >= mon.top;
                    Rect {
                        x: (want_pill.x + want_pill.w - fw).clamp(mon.left, (mon.right - fw).max(mon.left)),
                        y: if above { want_pill.y - fly_h - gap } else { want_pill.y + want_pill.h + gap },
                        w: fw,
                        h: fly_h,
                    }
                }
            };
            let moving_pill = shared.pill_edit.load(Ordering::Relaxed);
            let (dx, dy) = {
                let s = shared.settings.lock().unwrap();
                (s.lyr_dx, s.lyr_dy)
            };
            // Anchored by its bottom-right corner so the text grows up and left.
            let ov_h = px(*shared.overlay_h.lock().unwrap());
            let ov_w = px(*shared.overlay_w.lock().unwrap());
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
                // The user can switch Windows between light and dark at any time.
                let light = taskbar_is_light();
                if shared.taskbar_light.swap(light, Ordering::Relaxed) != light {
                    let _ = app.emit("sys_theme", light);
                }
            }

            if hidden_for_fullscreen {
                if pill_rect != Rect::default() {
                    hide(h_pill);
                    hide(h_ov);
                    pill_rect = Rect::default();
                    ov_shown = false;
                }
            } else {
                if st.dock_on {
                    // Attached to the calendar flyout: the pill leaves the taskbar and only the card shows.
                    if pill_rect != Rect::default() {
                        hide(h_pill);
                        pill_rect = Rect::default();
                    }
                } else if want_pill != pill_rect || periodic || !is_above(h_pill, tb.hwnd) {
                    place(h_pill, want_pill);
                    pill_rect = want_pill;
                }

                // "No lyrics found - Search" hint: shown instead of lyrics, and clickable, so the window takes the mouse.
                let (lyrics_ready, hinting) = {
                    let s = shared.settings.lock().unwrap();
                    let l = shared.lyrics.lock().unwrap();
                    let active = shared.media.lock().unwrap().active;
                    let on = s.lyrics_on && s.lyr_place == "desktop";
                    (
                        on && l.status == "synced" && !l.lines.is_empty(),
                        on && active && matches!(l.status.as_str(), "none" | "plain" | "error"),
                    )
                };
                let show_ov = editing || lyrics_ready || (hinting && !editing);
                let interactive = editing || hinting;
                if interactive != ov_interactive {
                    let _ = overlay.set_ignore_cursor_events(!interactive);
                    ov_interactive = interactive;
                }
                if show_ov && (!ov_shown || want_ov != ov_rect || (periodic && !open)) {
                    place(h_ov, want_ov);
                    ov_rect = want_ov;
                    ov_shown = true;
                } else if !show_ov && ov_shown {
                    hide(h_ov);
                    ov_shown = false;
                }
            }

            // Notification centre open: show the now-playing card above its first card.
            let fg = unsafe { GetForegroundWindow() };
            match notification_center(fg, mon).filter(|_| st.dock_on && !hidden_for_fullscreen) {
                Some(fr) => {
                    shared.dock_hwnd.store(fg.0 as isize, Ordering::Relaxed);
                    shared.dock_open.store(true, Ordering::Relaxed);
                    let dw = (fr.right - fr.left) - px(24.0);
                    let dh = px(*shared.dock_h.lock().unwrap());
                    let top = match shared.dock_top.load(Ordering::Relaxed) {
                        t if t > 0 => t,
                        _ => fr.bottom - px(711.0), // until UI Automation answers: the usual spot with no notifications
                    };
                    let gap = px(8.0);
                    let mut r = Rect { x: fr.left + px(12.0), y: top - dh - gap, w: dw, h: dh };
                    if r.y < mon.top + gap {
                        // No room above the cards (lots of notifications): sit beside the flyout instead.
                        r = Rect { x: fr.left - dw - gap, y: fr.bottom - dh - px(12.0), w: dw, h: dh };
                    }
                    let opening = !dock_open;
                    if opening || r != dock_rect || periodic || !is_above(h_dock, fg) {
                        place(h_dock, r);
                        dock_rect = r;
                        dock_open = true;
                    }
                    if opening {
                        dock_hide_at = None;
                        let _ = app.emit("dock_vis", true); // the card flies in from the right
                    }
                }
                None => {
                    if dock_open {
                        dock_open = false;
                        shared.dock_open.store(false, Ordering::Relaxed);
                        shared.dock_top.store(0, Ordering::Relaxed);
                        let _ = app.emit("dock_vis", false); // fly out first, hide the window once it's gone
                        dock_hide_at = Some(Instant::now() + DOCK_OUT);
                    }
                }
            }
            if let Some(t) = dock_hide_at {
                if !dock_open && Instant::now() >= t {
                    hide(h_dock);
                    dock_hide_at = None;
                }
            }

            if open && (want_fly != fly_rect || !is_above(h_fly, tb.hwnd) || (ov_shown && !is_above(h_fly, h_ov))) {
                place(h_fly, want_fly);
                fly_rect = want_fly;
            }

            // Hover: open after a short dwell on the pill, close shortly after leaving both windows.
            if let Ok(c) = app.cursor_position() {
                // Ctrl + hover over the desktop lyrics unlocks them; "Done" in their toolbar locks them again.
                if !editing && ov_shown && ov_rect.contains(c.x, c.y) && ctrl_down() {
                    crate::set_overlay_edit_inner(&app, true);
                }
                // While the pill is being moved, the panel stays out of the way.
                let in_pill = !hidden_for_fullscreen && !moving_pill && pill_rect.contains(c.x, c.y);
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
                } else if now - last_inside > CLOSE_DELAY || moving_pill {
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
