//! Finds lyrics for the current track: the user's lyrics folder first, then the
//! disk cache, then LRCLIB. Also the Search / Add / Folder / Reload actions.

use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::SystemTime;

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, Manager};

use crate::media::MediaSnapshot;
use crate::Shared;

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Line {
    pub t: i64,
    pub text: String,
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct Lyrics {
    pub track_key: String,
    /// "idle" | "loading" | "synced" | "plain" | "none" | "error"
    pub status: String,
    /// "local" | "lrclib" | ""
    pub source: String,
    pub lines: Vec<Line>,
    /// Modification time of the local file these came from, to notice edits.
    #[serde(skip)]
    pub local_mtime: Option<SystemTime>,
}

#[derive(Deserialize)]
struct LrclibRecord {
    #[serde(rename = "syncedLyrics")]
    synced: Option<String>,
    #[serde(rename = "plainLyrics")]
    plain: Option<String>,
}

pub fn parse_lrc(src: &str) -> Vec<Line> {
    let mut out = Vec::new();
    for raw in src.lines() {
        let mut rest = raw.trim();
        let mut stamps = Vec::new();
        while let Some(stripped) = rest.strip_prefix('[') {
            let Some(end) = stripped.find(']') else { break };
            let tag = &stripped[..end];
            rest = &stripped[end + 1..];
            let Some((m, s)) = tag.split_once(':') else { continue };
            let (Ok(m), Ok(s)) = (m.trim().parse::<i64>(), s.trim().parse::<f64>()) else { continue };
            stamps.push(m * 60_000 + (s * 1000.0).round() as i64);
        }
        let text = rest.trim().to_string();
        for t in stamps {
            out.push(Line { t, text: text.clone() });
        }
    }
    out.sort_by_key(|l| l.t);
    out
}

/// Lines for unsynced text: drops LRC tags like [ar:...] and # comments.
fn plain_lines(src: &str) -> Vec<Line> {
    src.lines()
        .map(str::trim)
        .filter(|l| !l.is_empty() && !l.starts_with('[') && !l.starts_with('#'))
        .map(|l| Line { t: -1, text: l.to_string() })
        .collect()
}

// ---------- the user's lyrics folder ----------

pub fn lyrics_dir(app: &AppHandle) -> Option<PathBuf> {
    let dir = app.path().audio_dir().ok()?.join("kRon Lyrics");
    std::fs::create_dir_all(&dir).ok()?;
    Some(dir)
}

fn sanitize(s: &str) -> String {
    s.chars()
        .map(|c| if "<>:\"/\\|?*".contains(c) || c.is_control() { '_' } else { c })
        .collect::<String>()
        .trim()
        .trim_end_matches('.')
        .to_string()
}

/// "Artist - Title", the file name kRon creates and looks for.
fn base_name(snap: &MediaSnapshot) -> String {
    if snap.artist.trim().is_empty() {
        sanitize(&snap.title)
    } else {
        format!("{} - {}", sanitize(&snap.artist), sanitize(&snap.title))
    }
}

/// Matches "Artist - Title" or just "Title", .lrc preferred over .txt, ignoring case.
pub fn find_local(app: &AppHandle, snap: &MediaSnapshot) -> Option<PathBuf> {
    let dir = lyrics_dir(app)?;
    let wanted = [base_name(snap).to_lowercase(), sanitize(&snap.title).to_lowercase()];
    let mut best: Option<(usize, PathBuf)> = None;
    for entry in std::fs::read_dir(dir).ok()?.flatten() {
        let path = entry.path();
        let ext = path.extension().and_then(|e| e.to_str()).unwrap_or("").to_lowercase();
        let stem = path.file_stem().and_then(|s| s.to_str()).unwrap_or("").to_lowercase();
        let Some(name_rank) = wanted.iter().position(|w| *w == stem) else { continue };
        let ext_rank = match ext.as_str() {
            "lrc" => 0,
            "txt" => 1,
            _ => continue,
        };
        let rank = name_rank * 2 + ext_rank;
        if best.as_ref().is_none_or(|(r, _)| rank < *r) {
            best = Some((rank, path));
        }
    }
    best.map(|(_, p)| p)
}

fn load_local(path: &Path, key: &str) -> Option<Lyrics> {
    let bytes = std::fs::read(path).ok()?;
    let text = String::from_utf8_lossy(&bytes);
    let text = text.trim_start_matches('\u{feff}');
    let synced = parse_lrc(text);
    let (status, lines) = if !synced.is_empty() {
        ("synced", synced)
    } else {
        let plain = plain_lines(text);
        (if plain.is_empty() { "none" } else { "plain" }, plain)
    };
    Some(Lyrics {
        track_key: key.to_string(),
        status: status.into(),
        source: "local".into(),
        lines,
        local_mtime: std::fs::metadata(path).and_then(|m| m.modified()).ok(),
    })
}

// ---------- LRCLIB + cache ----------

fn cache_path(app: &AppHandle, key: &str) -> Option<PathBuf> {
    let dir = app.path().app_cache_dir().ok()?.join("lyrics");
    std::fs::create_dir_all(&dir).ok()?;
    let safe: String = key
        .chars()
        .map(|c| if c.is_alphanumeric() { c.to_ascii_lowercase() } else { '_' })
        .take(120)
        .collect();
    Some(dir.join(format!("{safe}.json")))
}

async fn fetch(snap: &MediaSnapshot) -> Result<Option<LrclibRecord>, reqwest::Error> {
    let client = reqwest::Client::builder().user_agent("kRon/0.1 (Windows taskbar lyrics)").build()?;

    if snap.duration_ms > 0 {
        let dur = (snap.duration_ms / 1000).to_string();
        let resp = client
            .get("https://lrclib.net/api/get")
            .query(&[
                ("track_name", snap.title.as_str()),
                ("artist_name", snap.artist.as_str()),
                ("album_name", snap.album.as_str()),
                ("duration", dur.as_str()),
            ])
            .send()
            .await?;
        if resp.status().is_success() {
            let rec: LrclibRecord = resp.json().await?;
            if rec.synced.is_some() {
                return Ok(Some(rec));
            }
        }
    }

    let results: Vec<LrclibRecord> = client
        .get("https://lrclib.net/api/search")
        .query(&[("track_name", snap.title.as_str()), ("artist_name", snap.artist.as_str())])
        .send()
        .await?
        .json()
        .await?;
    let mut plain = None;
    for r in results {
        if r.synced.as_deref().is_some_and(|s| !s.is_empty()) {
            return Ok(Some(r));
        }
        if plain.is_none() && r.plain.is_some() {
            plain = Some(r);
        }
    }
    Ok(plain)
}

/// `force` starts a new track; otherwise results for a track that is no longer current are dropped.
fn publish(app: &AppHandle, lyrics: Lyrics, force: bool) {
    let shared = app.state::<Shared>();
    let mut cur = shared.lyrics.lock().unwrap();
    if !force && cur.track_key != lyrics.track_key {
        return;
    }
    *cur = lyrics.clone();
    drop(cur);
    let _ = app.emit("lyrics", lyrics);
}

fn status_only(key: &str, status: &str) -> Lyrics {
    Lyrics { track_key: key.to_string(), status: status.into(), ..Default::default() }
}

pub fn on_track_change(app: &AppHandle, snap: &MediaSnapshot) {
    let key = snap.track_key.clone();
    if !snap.active {
        publish(app, status_only(&key, "idle"), true);
        return;
    }
    if let Some(local) = find_local(app, snap).and_then(|p| load_local(&p, &key)) {
        publish(app, local, true);
        return;
    }
    publish(app, status_only(&key, "loading"), true);

    let app = app.clone();
    let snap = snap.clone();
    tauri::async_runtime::spawn(async move {
        let path = cache_path(&app, &key);
        if let Some(cached) = path.as_ref().and_then(|p| std::fs::read(p).ok()).and_then(|b| serde_json::from_slice::<Lyrics>(&b).ok()) {
            publish(&app, Lyrics { source: "lrclib".into(), ..cached }, false);
            return;
        }

        let lyrics = match fetch(&snap).await {
            Ok(Some(rec)) => match rec.synced.filter(|s| !s.is_empty()) {
                Some(s) => Lyrics { track_key: key.clone(), status: "synced".into(), source: "lrclib".into(), lines: parse_lrc(&s), local_mtime: None },
                None => Lyrics {
                    track_key: key.clone(),
                    status: "plain".into(),
                    source: "lrclib".into(),
                    lines: plain_lines(&rec.plain.unwrap_or_default()),
                    local_mtime: None,
                },
            },
            Ok(None) => status_only(&key, "none"),
            Err(_) => {
                publish(&app, status_only(&key, "error"), false);
                return;
            }
        };
        if let Some(p) = path {
            if let Ok(b) = serde_json::to_vec(&lyrics) {
                let _ = std::fs::write(p, b);
            }
        }
        publish(&app, lyrics, false);
    });
}

/// Called every few seconds: picks up a lyrics file the user just added, edited or deleted.
pub fn check_local(app: &AppHandle) {
    let shared = app.state::<Shared>();
    let snap = shared.media.lock().unwrap().clone();
    if !snap.active {
        return;
    }
    let (key, source, mtime, status) = {
        let l = shared.lyrics.lock().unwrap();
        (l.track_key.clone(), l.source.clone(), l.local_mtime, l.status.clone())
    };
    if key != snap.track_key || status == "loading" {
        return;
    }
    match find_local(app, &snap) {
        Some(p) => {
            let now = std::fs::metadata(&p).and_then(|m| m.modified()).ok();
            if source != "local" || now != mtime {
                if let Some(l) = load_local(&p, &key) {
                    publish(app, l, false);
                }
            }
        }
        None if source == "local" => on_track_change(app, &snap),
        None => {}
    }
}

// ---------- panel buttons ----------

fn current_track(app: &AppHandle) -> Option<MediaSnapshot> {
    let snap = app.state::<Shared>().media.lock().unwrap().clone();
    snap.active.then_some(snap)
}

/// Google the current track's lyrics in the default browser.
pub fn search_web(app: &AppHandle) {
    let Some(snap) = current_track(app) else { return };
    let q = format!("{} {} lrc lyrics", snap.artist, snap.title);
    if let Ok(url) = reqwest::Url::parse_with_params("https://www.google.com/search", &[("q", q.trim())]) {
        let _ = Command::new("explorer.exe").arg(url.as_str()).spawn();
    }
}

/// Opens the current track's lyrics file in Notepad, creating it first if needed.
pub fn add_or_edit(app: &AppHandle) {
    let Some(snap) = current_track(app) else { return };
    let path = match find_local(app, &snap) {
        Some(p) => p,
        None => {
            let Some(dir) = lyrics_dir(app) else { return };
            let p = dir.join(format!("{}.lrc", base_name(&snap)));
            let template = format!(
                "[ar:{}]\n[ti:{}]\n[al:{}]\n# Paste lyrics below and save. Synced lines look like: [01:23.45] words\n\n",
                snap.artist, snap.title, snap.album
            );
            if std::fs::write(&p, template).is_err() {
                return;
            }
            p
        }
    };
    let _ = Command::new("notepad.exe").arg(path).spawn();
}

pub fn open_folder(app: &AppHandle) {
    if let Some(dir) = lyrics_dir(app) {
        let _ = Command::new("explorer.exe").arg(dir).spawn();
    }
}

/// Forgets the cached copy and looks the current track up again.
pub fn reload(app: &AppHandle) {
    let Some(snap) = current_track(app) else { return };
    if let Some(p) = cache_path(app, &snap.track_key) {
        let _ = std::fs::remove_file(p);
    }
    on_track_change(app, &snap);
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_lrc_tags() {
        let lines = parse_lrc("[ar:Someone]\n[00:12.50]Hello there\n[01:02.00][01:30.25]Twice\n[00:00.00]");
        let times: Vec<i64> = lines.iter().map(|l| l.t).collect();
        assert_eq!(times, vec![0, 12_500, 62_000, 90_250]);
        assert_eq!(lines[1].text, "Hello there");
        assert_eq!(lines[3].text, "Twice");
    }

    #[test]
    fn plain_text_skips_tags_and_comments() {
        let lines = plain_lines("[ar:X]\n# note\n\nFirst line\nSecond line\n");
        let text: Vec<&str> = lines.iter().map(|l| l.text.as_str()).collect();
        assert_eq!(text, vec!["First line", "Second line"]);
    }

    #[test]
    fn sanitizes_file_names() {
        assert_eq!(sanitize("AC/DC: Live?"), "AC_DC_ Live_");
    }
}
