//! Reads "now playing" from Windows media controls (SMTC) and sends commands to the player.

use std::sync::mpsc::{Receiver, RecvTimeoutError};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use base64::Engine;
use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager};
use windows::Media::Control::{
    GlobalSystemMediaTransportControlsSession as Session,
    GlobalSystemMediaTransportControlsSessionManager as SessionManager,
    GlobalSystemMediaTransportControlsSessionPlaybackStatus as PlaybackStatus,
};
use windows::Media::MediaPlaybackAutoRepeatMode;
use windows::Storage::Streams::DataReader;
use windows::Win32::System::WinRT::{RoInitialize, RO_INIT_MULTITHREADED};

use crate::Shared;

/// Seconds between 1601-01-01 (Windows epoch) and 1970-01-01, in 100ns ticks.
const EPOCH_DIFF_TICKS: i64 = 116_444_736_000_000_000;

#[derive(Clone, Debug, Default, Serialize, PartialEq)]
pub struct MediaSnapshot {
    pub active: bool,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub source: String,
    pub playing: bool,
    pub position_ms: i64,
    pub duration_ms: i64,
    /// Unix ms at which `position_ms` was true; the UI extrapolates from here while playing.
    pub updated_at_ms: i64,
    pub shuffle: bool,
    /// "none" | "track" | "list"
    pub repeat: String,
    /// Changes whenever the track changes.
    pub track_key: String,
}

pub enum MediaCmd {
    TogglePlay,
    Next,
    Prev,
    Seek(i64),
    ToggleShuffle,
    CycleRepeat,
}

fn now_ms() -> i64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as i64).unwrap_or(0)
}

fn pretty_source(aumid: &str) -> String {
    let base = aumid.rsplit(['\\', '!']).next().unwrap_or(aumid);
    let base = base.trim_end_matches(".exe").trim_end_matches(".EXE");
    let lower = base.to_lowercase();
    if lower.contains("spotify") {
        "Spotify".into()
    } else if lower.contains("zunemusic") || lower.contains("media player") {
        "Media Player".into()
    } else if lower.contains("applemusic") || lower.contains("apple") {
        "Apple Music".into()
    } else if lower.contains("chrome") {
        "Chrome".into()
    } else if lower.contains("msedge") {
        "Edge".into()
    } else if lower.contains("firefox") {
        "Firefox".into()
    } else if base.is_empty() {
        "Media".into()
    } else {
        let mut c = base.chars();
        c.next().map(|f| f.to_uppercase().collect::<String>() + c.as_str()).unwrap_or_default()
    }
}

fn read_snapshot(session: &Session) -> windows::core::Result<MediaSnapshot> {
    let props = session.TryGetMediaPropertiesAsync()?.get()?;
    let info = session.GetPlaybackInfo()?;
    let tl = session.GetTimelineProperties()?;

    let title = props.Title()?.to_string();
    let artist = props.Artist()?.to_string();
    let album = props.AlbumTitle()?.to_string();
    let playing = info.PlaybackStatus()? == PlaybackStatus::Playing;
    let shuffle = info.IsShuffleActive().and_then(|r| r.Value()).unwrap_or(false);
    let repeat = match info.AutoRepeatMode().and_then(|r| r.Value()) {
        Ok(MediaPlaybackAutoRepeatMode::Track) => "track",
        Ok(MediaPlaybackAutoRepeatMode::List) => "list",
        _ => "none",
    }
    .to_string();

    let position_ms = tl.Position()?.Duration / 10_000;
    let duration_ms = (tl.EndTime()?.Duration - tl.StartTime()?.Duration) / 10_000;
    let updated = tl.LastUpdatedTime()?.UniversalTime;
    let updated_at_ms = if updated > EPOCH_DIFF_TICKS { (updated - EPOCH_DIFF_TICKS) / 10_000 } else { now_ms() };

    let source = pretty_source(&session.SourceAppUserModelId()?.to_string());
    Ok(MediaSnapshot {
        active: !title.is_empty(),
        track_key: format!("{artist}\u{1f}{title}"),
        title,
        artist,
        album,
        source,
        playing,
        position_ms,
        duration_ms,
        updated_at_ms,
        shuffle,
        repeat,
    })
}

fn read_artwork(session: &Session) -> windows::core::Result<String> {
    let props = session.TryGetMediaPropertiesAsync()?.get()?;
    let thumb = props.Thumbnail()?;
    let stream = thumb.OpenReadAsync()?.get()?;
    let size = stream.Size()? as u32;
    if size == 0 || size > 8 * 1024 * 1024 {
        return Ok(String::new());
    }
    let mime = stream.ContentType().map(|s| s.to_string()).unwrap_or_default();
    let reader = DataReader::CreateDataReader(&stream.GetInputStreamAt(0)?)?;
    reader.LoadAsync(size)?.get()?;
    let mut buf = vec![0u8; size as usize];
    reader.ReadBytes(&mut buf)?;
    let mime = if mime.is_empty() { "image/png".to_string() } else { mime };
    Ok(format!("data:{mime};base64,{}", base64::engine::general_purpose::STANDARD.encode(buf)))
}

fn run_cmd(session: &Session, cmd: MediaCmd, snap: &MediaSnapshot) -> windows::core::Result<()> {
    match cmd {
        MediaCmd::TogglePlay => {
            session.TryTogglePlayPauseAsync()?.get()?;
        }
        MediaCmd::Next => {
            session.TrySkipNextAsync()?.get()?;
        }
        MediaCmd::Prev => {
            session.TrySkipPreviousAsync()?.get()?;
        }
        MediaCmd::Seek(ms) => {
            session.TryChangePlaybackPositionAsync(ms.max(0) * 10_000)?.get()?;
        }
        MediaCmd::ToggleShuffle => {
            session.TryChangeShuffleActiveAsync(!snap.shuffle)?.get()?;
        }
        MediaCmd::CycleRepeat => {
            let next = match snap.repeat.as_str() {
                "none" => MediaPlaybackAutoRepeatMode::List,
                "list" => MediaPlaybackAutoRepeatMode::Track,
                _ => MediaPlaybackAutoRepeatMode::None,
            };
            session.TryChangeAutoRepeatModeAsync(next)?.get()?;
        }
    }
    Ok(())
}

/// Media thread: polls the current session twice a second and runs queued commands.
pub fn run(app: AppHandle, rx: Receiver<MediaCmd>) {
    unsafe {
        let _ = RoInitialize(RO_INIT_MULTITHREADED);
    }
    let shared = app.state::<Shared>().inner().clone();
    let mut manager: Option<SessionManager> = None;
    let mut last_key = String::new();
    let mut polls: u32 = 0;

    loop {
        polls = polls.wrapping_add(1);
        if polls % 6 == 0 {
            crate::lyrics::check_local(&app);
        }
        let cmd = match rx.recv_timeout(Duration::from_millis(500)) {
            Ok(c) => Some(c),
            Err(RecvTimeoutError::Timeout) => None,
            Err(RecvTimeoutError::Disconnected) => return,
        };

        if manager.is_none() {
            manager = SessionManager::RequestAsync().and_then(|op| op.get()).ok();
        }
        let session = manager.as_ref().and_then(|m| m.GetCurrentSession().ok());

        if let (Some(s), Some(c)) = (&session, cmd) {
            let snap = shared.media.lock().unwrap().clone();
            let _ = run_cmd(s, c, &snap);
            std::thread::sleep(Duration::from_millis(120));
        }

        let snap = session.as_ref().and_then(|s| read_snapshot(s).ok()).unwrap_or_default();

        if snap.track_key != last_key {
            last_key = snap.track_key.clone();
            let art = if snap.active {
                session.as_ref().and_then(|s| read_artwork(s).ok()).unwrap_or_default()
            } else {
                String::new()
            };
            *shared.artwork.lock().unwrap() = art.clone();
            let _ = app.emit("artwork", art);
            crate::lyrics::on_track_change(&app, &snap);
        }

        let changed = {
            let mut cur = shared.media.lock().unwrap();
            // Ignore tiny timeline jitter so we don't spam the UI.
            let same = cur.track_key == snap.track_key
                && cur.playing == snap.playing
                && cur.shuffle == snap.shuffle
                && cur.repeat == snap.repeat
                && cur.duration_ms == snap.duration_ms
                && ((cur.position_ms + (snap.updated_at_ms - cur.updated_at_ms) * cur.playing as i64)
                    - snap.position_ms)
                    .abs()
                    < 400;
            if !same {
                *cur = snap.clone();
            }
            !same
        };
        if changed {
            let _ = app.emit("media", snap);
        }
    }
}
