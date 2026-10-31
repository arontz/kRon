//! Finds lyrics for the current track: the user's lyrics folder first, then the
//! disk cache, then QQ Music, then LRCLIB. Also the Search / Add / Folder / Reload actions.

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
    /// "local" | "qq" | "lrclib" | ""
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

// ---------- QQ Music, LRCLIB + cache ----------

fn cache_path(app: &AppHandle, key: &str) -> Option<PathBuf> {
    // "lyrics-v2": the first cache only ever asked LRCLIB, so its "no lyrics" entries must not hide what QQ Music has.
    let dir = app.path().app_cache_dir().ok()?.join("lyrics-v2");
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

/// Lyrics one online source came back with: timestamped text, plain text, or both.
#[derive(Debug)]
struct Found {
    source: &'static str,
    synced: Option<String>,
    plain: Option<String>,
}

#[derive(Debug)]
struct Lookup {
    found: Option<Found>,
    /// True when every source that was asked answered, so "nothing found" can be remembered. A source that was down
    /// might have had them, so that case is not cached.
    complete: bool,
}

/// QQ Music's text when it has timestamps. Then LRCLIB does not need to be asked at all.
fn qq_synced(qq: &Result<Option<String>, String>) -> Option<Found> {
    let text = qq.as_ref().ok()?.as_ref()?;
    (!parse_lrc(text).is_empty()).then(|| Found { source: "qq", synced: Some(text.clone()), plain: None })
}

/// Combines the answers of the two sources: timestamped lyrics from either, then QQ Music's plain text, then
/// LRCLIB's. An error only when neither source could be reached.
fn merge(qq: Result<Option<String>, String>, lr: Result<Option<LrclibRecord>, String>) -> Result<Lookup, ()> {
    if qq.is_err() && lr.is_err() {
        return Err(());
    }
    let complete = qq.is_ok() && lr.is_ok();
    let non_empty = |s: Option<String>| s.filter(|t| !t.trim().is_empty());

    let lr_rec = lr.ok().flatten();
    let lr_synced = lr_rec.as_ref().and_then(|r| non_empty(r.synced.clone()));
    let lr_plain = lr_rec.as_ref().and_then(|r| non_empty(r.plain.clone()));
    let qq_text = qq.ok().flatten().and_then(|t| non_empty(Some(t)));

    let found = if let Some(s) = lr_synced {
        Some(Found { source: "lrclib", synced: Some(s), plain: None })
    } else if let Some(t) = qq_text {
        Some(Found { source: "qq", synced: None, plain: Some(t) })
    } else {
        lr_plain.map(|p| Found { source: "lrclib", synced: None, plain: Some(p) })
    };
    Ok(Lookup { found, complete })
}

/// QQ Music first; LRCLIB only when QQ Music has no timestamped lyrics.
async fn find(snap: &MediaSnapshot) -> Result<Lookup, ()> {
    let qq = crate::qq::find(&snap.title, &snap.artist, snap.duration_ms).await;
    if let Some(found) = qq_synced(&qq) {
        return Ok(Lookup { found: Some(found), complete: true });
    }
    let lr = fetch(snap).await.map_err(|e| e.to_string());
    merge(qq, lr)
}

fn lyrics_from(key: &str, found: Option<Found>) -> Lyrics {
    let Some(f) = found else { return status_only(key, "none") };
    if let Some(s) = f.synced.as_deref() {
        let lines = parse_lrc(s);
        if !lines.is_empty() {
            return Lyrics { track_key: key.into(), status: "synced".into(), source: f.source.into(), lines, local_mtime: None };
        }
    }
    let lines = plain_lines(f.plain.as_deref().unwrap_or_default());
    if lines.is_empty() {
        return status_only(key, "none");
    }
    Lyrics { track_key: key.into(), status: "plain".into(), source: f.source.into(), lines, local_mtime: None }
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
            publish(&app, cached, false);
            return;
        }

        let Ok(lookup) = find(&snap).await else {
            publish(&app, status_only(&key, "error"), false);
            return;
        };
        let lyrics = lyrics_from(&key, lookup.found);
        // Lyrics are always worth remembering; "none" only when no source was down.
        if lyrics.status != "none" || lookup.complete {
            if let (Some(p), Ok(b)) = (path, serde_json::to_vec(&lyrics)) {
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

/// Opens a web address in the user's default browser. (`explorer.exe <url>` can open a File Explorer window instead.)
pub fn open_url(url: &str) {
    use windows::core::{w, HSTRING};
    use windows::Win32::UI::Shell::ShellExecuteW;
    use windows::Win32::UI::WindowsAndMessaging::SW_SHOWNORMAL;
    unsafe {
        ShellExecuteW(None, w!("open"), &HSTRING::from(url), None, None, SW_SHOWNORMAL);
    }
}

/// Google the current track's lyrics in the default browser.
pub fn search_web(app: &AppHandle) {
    let Some(snap) = current_track(app) else { return };
    let q = format!("{} {} lrc lyrics", snap.artist, snap.title);
    if let Ok(url) = reqwest::Url::parse_with_params("https://www.google.com/search", &[("q", q.trim())]) {
        open_url(url.as_str());
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

/// Writes `content` as the lyrics file for `snap` ("Artist - Title.lrc" if it has timestamps, else ".txt") and returns
/// the file name. The other extension is left alone, but a synced .lrc always wins over a .txt when both exist.
pub fn save_local(app: &AppHandle, snap: &MediaSnapshot, content: &str, synced: bool) -> Result<String, String> {
    let dir = lyrics_dir(app).ok_or("Couldn't open the lyrics folder")?;
    let name = format!("{}.{}", base_name(snap), if synced { "lrc" } else { "txt" });
    std::fs::write(dir.join(&name), content.replace("\r\n", "\n")).map_err(|e| e.to_string())?;
    Ok(name)
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

    fn rec(synced: Option<&str>, plain: Option<&str>) -> LrclibRecord {
        LrclibRecord { synced: synced.map(String::from), plain: plain.map(String::from) }
    }

    #[test]
    fn qq_text_with_timestamps_is_used_straight_away() {
        let f = qq_synced(&Ok(Some("[00:01.00]hi".into()))).unwrap();
        assert_eq!((f.source, f.synced.as_deref()), ("qq", Some("[00:01.00]hi")));
    }

    #[test]
    fn qq_text_without_timestamps_is_not_enough() {
        assert!(qq_synced(&Ok(Some("just words".into()))).is_none());
        assert!(qq_synced(&Ok(None)).is_none());
        assert!(qq_synced(&Err("down".into())).is_none());
    }

    #[test]
    fn lrclib_synced_beats_qq_plain_text() {
        let l = merge(Ok(Some("words".into())), Ok(Some(rec(Some("[00:01.00]hi"), None)))).unwrap();
        let f = l.found.unwrap();
        assert_eq!((f.source, f.synced.as_deref()), ("lrclib", Some("[00:01.00]hi")));
        assert!(l.complete);
    }

    #[test]
    fn qq_plain_text_beats_lrclib_plain_text() {
        let l = merge(Ok(Some("qq words".into())), Ok(Some(rec(None, Some("lrclib words"))))).unwrap();
        let f = l.found.unwrap();
        assert_eq!((f.source, f.plain.as_deref(), f.synced), ("qq", Some("qq words"), None));
    }

    #[test]
    fn lrclib_plain_text_is_used_when_qq_has_nothing() {
        let f = merge(Ok(None), Ok(Some(rec(None, Some("lrclib words"))))).unwrap().found.unwrap();
        assert_eq!((f.source, f.plain.as_deref()), ("lrclib", Some("lrclib words")));
    }

    #[test]
    fn no_lyrics_is_only_final_when_both_sources_answered() {
        let both = merge(Ok(None), Ok(None)).unwrap();
        assert!(both.found.is_none() && both.complete);
        let one_down = merge(Err("down".into()), Ok(None)).unwrap();
        assert!(one_down.found.is_none() && !one_down.complete);
    }

    #[test]
    fn one_source_down_still_uses_the_other() {
        let l = merge(Err("down".into()), Ok(Some(rec(Some("[00:01.00]hi"), None)))).unwrap();
        assert_eq!(l.found.unwrap().source, "lrclib");
        assert!(!l.complete);
        let q = merge(Ok(Some("words".into())), Err("down".into())).unwrap();
        assert_eq!(q.found.unwrap().source, "qq");
    }

    #[test]
    fn both_sources_failing_is_an_error() {
        assert!(merge(Err("a".into()), Err("b".into())).is_err());
    }
}
