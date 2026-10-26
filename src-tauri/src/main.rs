// No console window in release builds.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod lyrics;
mod media;
mod shell;
mod stats;
mod update;

use std::sync::atomic::{AtomicBool, AtomicI32, AtomicIsize};
use std::sync::mpsc::{channel, Sender};
use std::sync::{Arc, Mutex};

use serde::{Deserialize, Serialize};
use tauri::menu::{CheckMenuItem, Menu, MenuItem, PredefinedMenuItem};
use tauri::tray::TrayIconBuilder;
use tauri::{AppHandle, Emitter, Manager, Wry};
use tauri_plugin_autostart::{MacosLauncher, ManagerExt};

use media::{MediaCmd, MediaSnapshot};

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    pub lyrics_on: bool,
    /// Last panel picked in the flyout; the pill mirrors it. "music" | "system"
    pub page: String,
    /// Once dragged: the pill's top-left in logical px from the monitor's top-left. Before that it sits beside the tray.
    pub pill_placed: bool,
    pub pill_x: f64,
    pub pill_y: f64,
    /// "left" or "right" when the pill is docked to that screen edge as a vertical column, else "".
    pub pill_side: String,
    /// Show a now-playing card above the Windows notification centre while it's open.
    pub dock_on: bool,
    /// Where lyrics show when on: "desktop" (overlay) | "taskbar" (one line in the pill).
    pub lyr_place: String,
    /// Desktop lyrics text size multiplier.
    pub lyr_scale: f64,
    /// 3 or 5 lines on the desktop.
    pub lyr_lines: u8,
    /// Frosted backdrop behind the desktop lyrics.
    pub lyr_backdrop: bool,
    /// Shifts lyric timing; positive shows lines earlier.
    pub lyr_offset_ms: i64,
    /// Where the user dragged the lyrics, in logical px from the default bottom-right spot.
    pub lyr_dx: f64,
    pub lyr_dy: f64,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            lyrics_on: true,
            page: "music".into(),
            pill_placed: false,
            pill_x: 0.0,
            pill_y: 0.0,
            pill_side: String::new(),
            dock_on: false,
            lyr_place: "desktop".into(),
            lyr_scale: 1.0,
            lyr_lines: 3,
            lyr_backdrop: false,
            lyr_offset_ms: 0,
            lyr_dx: 0.0,
            lyr_dy: 0.0,
        }
    }
}

pub struct SharedInner {
    pub media: Mutex<MediaSnapshot>,
    pub artwork: Mutex<String>,
    pub lyrics: Mutex<lyrics::Lyrics>,
    pub stats: Mutex<stats::Stats>,
    pub settings: Mutex<Settings>,
    pub flyout_open: AtomicBool,
    /// Desktop lyrics accept the mouse so they can be moved and restyled.
    pub overlay_edit: AtomicBool,
    /// The pill shows a drag handle and can be moved.
    pub pill_edit: AtomicBool,
    pub pill_w: Mutex<f64>,
    pub pill_h: Mutex<f64>,
    /// Drag distance (logical px) from the pill's handle that the shell thread hasn't applied yet.
    pub pill_move: Mutex<PillMove>,
    pub flyout_h: Mutex<f64>,
    pub overlay_h: Mutex<f64>,
    pub dock_h: Mutex<f64>,
    /// The Windows notification centre is open: its window handle and where its first card starts (px, 0 = unknown).
    pub dock_open: AtomicBool,
    pub dock_hwnd: AtomicIsize,
    pub dock_top: AtomicI32,
    cmd_tx: Mutex<Sender<MediaCmd>>,
}

#[derive(Default)]
pub struct PillMove {
    pub dx: f64,
    pub dy: f64,
    /// The drag ended: save the position.
    pub commit: bool,
}

pub type Shared = Arc<SharedInner>;

struct TrayItems {
    off: CheckMenuItem<Wry>,
    desktop: CheckMenuItem<Wry>,
    taskbar: CheckMenuItem<Wry>,
    dock: CheckMenuItem<Wry>,
}

impl TrayItems {
    fn sync(&self, s: &Settings) {
        let _ = self.dock.set_checked(s.dock_on);
        // Lyrics in the pill don't fit the narrow column the pill becomes at a side edge.
        let _ = self.taskbar.set_enabled(s.pill_side.is_empty());
        let _ = self.off.set_checked(!s.lyrics_on);
        let _ = self.desktop.set_checked(s.lyrics_on && s.lyr_place == "desktop");
        let _ = self.taskbar.set_checked(s.lyrics_on && s.lyr_place == "taskbar");
    }
}

fn settings_path(app: &AppHandle) -> Option<std::path::PathBuf> {
    let dir = app.path().app_config_dir().ok()?;
    std::fs::create_dir_all(&dir).ok()?;
    Some(dir.join("settings.json"))
}

fn save_settings(app: &AppHandle, s: &Settings) {
    if let (Some(p), Ok(json)) = (settings_path(app), serde_json::to_vec_pretty(s)) {
        let _ = std::fs::write(p, json);
    }
}

fn update_settings(app: &AppHandle, f: impl FnOnce(&mut Settings)) {
    update_settings_opt(app, true, f);
}

/// `save: false` while dragging, so we don't write the file on every mouse move.
fn update_settings_opt(app: &AppHandle, save: bool, f: impl FnOnce(&mut Settings)) {
    let shared = app.state::<Shared>();
    let s = {
        let mut s = shared.settings.lock().unwrap();
        f(&mut s);
        s.clone()
    };
    if save {
        save_settings(app, &s);
    }
    if let Some(items) = app.try_state::<TrayItems>() {
        items.sync(&s);
    }
    let _ = app.emit("settings", s);
}

#[tauri::command]
fn get_state(shared: tauri::State<Shared>) -> serde_json::Value {
    serde_json::json!({
        "media": *shared.media.lock().unwrap(),
        "artwork": *shared.artwork.lock().unwrap(),
        "lyrics": *shared.lyrics.lock().unwrap(),
        "stats": *shared.stats.lock().unwrap(),
        "settings": *shared.settings.lock().unwrap(),
        "overlayEdit": shared.overlay_edit.load(std::sync::atomic::Ordering::Relaxed),
        "pillEdit": shared.pill_edit.load(std::sync::atomic::Ordering::Relaxed),
    })
}

#[tauri::command]
fn media_cmd(shared: tauri::State<Shared>, action: String, ms: Option<i64>) {
    let cmd = match action.as_str() {
        "toggle" => MediaCmd::TogglePlay,
        "next" => MediaCmd::Next,
        "prev" => MediaCmd::Prev,
        "seek" => MediaCmd::Seek(ms.unwrap_or(0)),
        "shuffle" => MediaCmd::ToggleShuffle,
        "repeat" => MediaCmd::CycleRepeat,
        _ => return,
    };
    let _ = shared.cmd_tx.lock().unwrap().send(cmd);
}

#[tauri::command]
fn set_lyrics_on(app: AppHandle, on: bool) {
    update_settings(&app, |s| s.lyrics_on = on);
}

#[tauri::command]
fn set_page(app: AppHandle, page: String) {
    if page == "music" || page == "system" {
        update_settings(&app, |s| s.page = page);
    }
}

#[tauri::command]
#[allow(clippy::too_many_arguments)]
fn set_lyr(
    app: AppHandle,
    place: Option<String>,
    scale: Option<f64>,
    lines: Option<u8>,
    backdrop: Option<bool>,
    offset_ms: Option<i64>,
    dx: Option<f64>,
    dy: Option<f64>,
    save: Option<bool>,
) {
    update_settings_opt(&app, save.unwrap_or(true), |s| {
        if let Some(v) = place.filter(|p| p == "desktop" || p == "taskbar") {
            s.lyr_place = v;
        }
        if let Some(v) = scale {
            s.lyr_scale = v.clamp(0.6, 2.0);
        }
        if let Some(v) = lines {
            s.lyr_lines = if v >= 5 { 5 } else { 3 };
        }
        if let Some(v) = backdrop {
            s.lyr_backdrop = v;
        }
        if let Some(v) = offset_ms {
            s.lyr_offset_ms = v.clamp(-10_000, 10_000);
        }
        if let Some(v) = dx {
            s.lyr_dx = v;
        }
        if let Some(v) = dy {
            s.lyr_dy = v;
        }
    });
}

pub fn set_overlay_edit_inner(app: &AppHandle, on: bool) {
    let shared = app.state::<Shared>();
    shared.overlay_edit.store(on, std::sync::atomic::Ordering::Relaxed);
    let _ = app.emit("overlay_edit", on);
}

#[tauri::command]
fn set_overlay_edit(app: AppHandle, on: bool) {
    if on {
        update_settings(&app, |s| {
            s.lyrics_on = true;
            s.lyr_place = "desktop".into();
        });
    }
    set_overlay_edit_inner(&app, on);
}

#[tauri::command]
fn lyrics_action(app: AppHandle, action: String) {
    match action.as_str() {
        "search" => lyrics::search_web(&app),
        "add" => lyrics::add_or_edit(&app),
        "folder" => lyrics::open_folder(&app),
        "reload" => lyrics::reload(&app),
        _ => {}
    }
}

pub fn set_pill_edit_inner(app: &AppHandle, on: bool) {
    app.state::<Shared>().pill_edit.store(on, std::sync::atomic::Ordering::Relaxed);
    let _ = app.emit("pill_edit", on);
}

#[tauri::command]
fn set_pill_edit(app: AppHandle, on: bool) {
    if on {
        // The pill has to be on the taskbar to be moved.
        update_settings(&app, |s| s.dock_on = false);
    }
    set_pill_edit_inner(&app, on);
}

/// Drag the pill by this many logical px; the shell thread applies it and decides about edge docking.
#[tauri::command]
fn move_pill_by(shared: tauri::State<Shared>, dx: f64, dy: f64, commit: bool) {
    let mut m = shared.pill_move.lock().unwrap();
    m.dx += dx;
    m.dy += dy;
    m.commit |= commit;
}

/// Back to the default spot beside the tray, horizontal.
#[tauri::command]
fn reset_pill(app: AppHandle) {
    *app.state::<Shared>().pill_move.lock().unwrap() = PillMove::default();
    update_settings(&app, |s| {
        s.pill_placed = false;
        s.pill_x = 0.0;
        s.pill_y = 0.0;
        s.pill_side = String::new();
    });
}

#[tauri::command]
fn overlay_size(shared: tauri::State<Shared>, h: f64) {
    *shared.overlay_h.lock().unwrap() = h.clamp(80.0, 900.0);
}

#[tauri::command]
fn set_dock_on(app: AppHandle, on: bool) {
    update_settings(&app, |s| s.dock_on = on);
}

#[tauri::command]
fn dock_size(shared: tauri::State<Shared>, h: f64) {
    *shared.dock_h.lock().unwrap() = h.clamp(80.0, 400.0);
}

#[tauri::command]
fn pill_size(shared: tauri::State<Shared>, w: f64, h: f64) {
    *shared.pill_w.lock().unwrap() = w.clamp(30.0, 600.0);
    *shared.pill_h.lock().unwrap() = h.clamp(30.0, 900.0);
}

#[tauri::command]
fn flyout_size(shared: tauri::State<Shared>, h: f64) {
    *shared.flyout_h.lock().unwrap() = h.clamp(200.0, 900.0);
}

fn build_tray(app: &AppHandle) -> tauri::Result<()> {
    let settings = app.state::<Shared>().settings.lock().unwrap().clone();
    let autostart_on = app.autolaunch().is_enabled().unwrap_or(false);

    let off = CheckMenuItem::with_id(app, "lyr_off", "Lyrics off", true, false, None::<&str>)?;
    let desktop = CheckMenuItem::with_id(app, "lyr_desktop", "Lyrics on desktop", true, false, None::<&str>)?;
    let taskbar = CheckMenuItem::with_id(app, "lyr_taskbar", "Lyrics on taskbar", true, false, None::<&str>)?;
    let customize = MenuItem::with_id(app, "customize", "Customize lyrics…", true, None::<&str>)?;
    let move_pill = MenuItem::with_id(app, "move_pill", "Move pill…", true, None::<&str>)?;
    let dock = CheckMenuItem::with_id(app, "dock", "Show in calendar flyout (hide from taskbar)", true, false, None::<&str>)?;
    let autostart = CheckMenuItem::with_id(app, "autostart", "Start with Windows", true, autostart_on, None::<&str>)?;
    let check_update = MenuItem::with_id(app, "check_update", "Check for updates…", true, None::<&str>)?;
    let version = MenuItem::with_id(app, "version", format!("kRon v{}", update::VERSION), false, None::<&str>)?;
    let quit = MenuItem::with_id(app, "quit", "Quit kRon", true, None::<&str>)?;
    let sep = PredefinedMenuItem::separator(app)?;
    let sep2 = PredefinedMenuItem::separator(app)?;
    let menu = Menu::with_items(app, &[&off, &desktop, &taskbar, &customize, &move_pill, &dock, &sep2, &autostart, &check_update, &version, &sep, &quit])?;

    let autostart_item = autostart.clone();
    TrayIconBuilder::with_id("kron")
        .icon(app.default_window_icon().cloned().expect("app icon"))
        .tooltip("kRon")
        .menu(&menu)
        .on_menu_event(move |app, event| match event.id.as_ref() {
            "lyr_off" => update_settings(app, |s| s.lyrics_on = false),
            "move_pill" => {
                update_settings(app, |s| s.dock_on = false);
                set_pill_edit_inner(app, true);
            }
            "dock" => {
                let on = app.state::<Shared>().settings.lock().unwrap().dock_on;
                update_settings(app, |s| s.dock_on = !on);
            }
            "lyr_desktop" | "lyr_taskbar" | "customize" => {
                let place = if event.id.as_ref() == "lyr_taskbar" { "taskbar" } else { "desktop" };
                update_settings(app, |s| {
                    s.lyrics_on = true;
                    s.lyr_place = place.into();
                });
                if event.id.as_ref() == "customize" {
                    set_overlay_edit_inner(app, true);
                }
            }
            "autostart" => {
                let al = app.autolaunch();
                let on = al.is_enabled().unwrap_or(false);
                let _ = if on { al.disable() } else { al.enable() };
                let _ = autostart_item.set_checked(al.is_enabled().unwrap_or(false));
            }
            "check_update" => update::check_for_updates(),
            "quit" => app.exit(0),
            _ => {}
        })
        .build(app)?;

    let items = TrayItems { off, desktop, taskbar, dock };
    items.sync(&settings);
    app.manage(items);
    Ok(())
}

fn main() {
    let (tx, rx) = channel();
    tauri::Builder::default()
        .plugin(tauri_plugin_autostart::init(MacosLauncher::LaunchAgent, None))
        .invoke_handler(tauri::generate_handler![
            get_state,
            media_cmd,
            set_lyrics_on,
            set_page,
            set_lyr,
            set_overlay_edit,
            set_pill_edit,
            move_pill_by,
            reset_pill,
            overlay_size,
            dock_size,
            set_dock_on,
            lyrics_action,
            pill_size,
            flyout_size
        ])
        .setup(move |app| {
            let handle = app.handle().clone();
            let settings = settings_path(&handle)
                .and_then(|p| std::fs::read(p).ok())
                .and_then(|b| serde_json::from_slice::<Settings>(&b).ok())
                .unwrap_or_default();

            app.manage::<Shared>(Arc::new(SharedInner {
                media: Mutex::new(MediaSnapshot::default()),
                artwork: Mutex::new(String::new()),
                lyrics: Mutex::new(lyrics::Lyrics::default()),
                stats: Mutex::new(stats::Stats::default()),
                settings: Mutex::new(settings),
                flyout_open: AtomicBool::new(false),
                overlay_edit: AtomicBool::new(false),
                pill_edit: AtomicBool::new(false),
                pill_w: Mutex::new(340.0),
                pill_h: Mutex::new(40.0),
                pill_move: Mutex::new(PillMove::default()),
                flyout_h: Mutex::new(520.0),
                overlay_h: Mutex::new(200.0),
                dock_h: Mutex::new(150.0),
                dock_open: AtomicBool::new(false),
                dock_hwnd: AtomicIsize::new(0),
                dock_top: AtomicI32::new(0),
                cmd_tx: Mutex::new(tx.clone()),
            }));

            build_tray(&handle)?;

            let h = handle.clone();
            std::thread::spawn(move || media::run(h, rx));
            let h = handle.clone();
            std::thread::spawn(move || stats::run(h));
            let h = handle.clone();
            std::thread::spawn(move || shell::run(h));
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running kRon");
}
