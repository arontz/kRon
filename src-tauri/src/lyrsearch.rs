//! The "Search for lyrics" window: searches several sources, shows a preview, and saves the pick to the lyrics folder.

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Manager, WebviewUrl, WebviewWindowBuilder};

use crate::lyrics;
use crate::Shared;

const LRCLIB_SEARCH: &str = "https://lrclib.net/api/search";
const QQ_SEARCH: &str = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
const QQ_LYRIC: &str = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg";

#[derive(Clone, Debug, Serialize)]
pub struct Hit {
    pub source: String,
    pub id: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    /// Known right away for sources that return the lyrics in the search; `None` until fetched otherwise.
    pub synced: Option<bool>,
    /// The lyrics themselves when the search already returned them.
    pub content: Option<String>,
}

fn client() -> Result<reqwest::Client, String> {
    reqwest::Client::builder()
        .user_agent("kRon/0.3 (Windows taskbar lyrics)")
        .timeout(std::time::Duration::from_secs(10))
        .build()
        .map_err(|e| e.to_string())
}

// ---------- LRCLIB (open, official API) ----------

#[derive(Deserialize)]
struct LrclibItem {
    id: i64,
    #[serde(rename = "trackName", default)]
    track_name: String,
    #[serde(rename = "artistName", default)]
    artist_name: String,
    #[serde(rename = "albumName", default)]
    album_name: String,
    #[serde(default)]
    instrumental: bool,
    #[serde(rename = "plainLyrics")]
    plain: Option<String>,
    #[serde(rename = "syncedLyrics")]
    synced: Option<String>,
}

async fn search_lrclib(title: &str, album: &str, artist: &str) -> Result<Vec<Hit>, String> {
    let mut params: Vec<(&str, &str)> = Vec::new();
    if artist.is_empty() && album.is_empty() {
        // One free-text box: let LRCLIB match the words against track, artist and album.
        params.push(("q", title));
    } else {
        if !title.is_empty() {
            params.push(("track_name", title));
        }
        if !artist.is_empty() {
            params.push(("artist_name", artist));
        }
        if !album.is_empty() {
            params.push(("album_name", album));
        }
    }
    let items: Vec<LrclibItem> = client()?
        .get(LRCLIB_SEARCH)
        .query(&params)
        .send()
        .await
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|e| e.to_string())?;
    Ok(items
        .into_iter()
        .filter(|i| !i.instrumental)
        .filter_map(|i| {
            let synced = i.synced.filter(|s| !s.trim().is_empty());
            let is_synced = synced.is_some();
            let content = synced.or(i.plain.filter(|s| !s.trim().is_empty()))?;
            Some(Hit {
                source: "LRCLIB".into(),
                id: i.id.to_string(),
                title: i.track_name,
                artist: i.artist_name,
                album: i.album_name,
                synced: Some(is_synced),
                content: Some(content),
            })
        })
        .collect())
}

// ---------- QQ Music (unofficial; opt-in) ----------

async fn search_qq(title: &str, album: &str, artist: &str) -> Result<Vec<Hit>, String> {
    let w = [artist, title, album].iter().filter(|s| !s.is_empty()).cloned().collect::<Vec<_>>().join(" ");
    let v: serde_json::Value = client()?
        .get(QQ_SEARCH)
        .header("Referer", "https://y.qq.com/")
        .query(&[("w", w.as_str()), ("format", "json"), ("n", "15"), ("p", "1")])
        .send()
        .await
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|e| e.to_string())?;
    let list = v["data"]["song"]["list"].as_array().cloned().unwrap_or_default();
    Ok(list
        .into_iter()
        .filter_map(|s| {
            let id = s["songmid"].as_str()?.to_string();
            let artist = s["singer"]
                .as_array()
                .map(|a| a.iter().filter_map(|x| x["name"].as_str()).collect::<Vec<_>>().join(", "))
                .unwrap_or_default();
            Some(Hit {
                source: "QQ Music".into(),
                id,
                title: s["songname"].as_str().unwrap_or_default().to_string(),
                artist,
                album: s["albumname"].as_str().unwrap_or_default().to_string(),
                synced: None,
                content: None,
            })
        })
        .collect())
}

fn decode_entities(s: &str) -> String {
    let mut out = s
        .replace("&apos;", "'")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&");
    // Numeric entities like &#10; (newline) or &#39;
    while let Some(start) = out.find("&#") {
        let Some(end) = out[start..].find(';') else { break };
        let num = &out[start + 2..start + end];
        let ch = if let Some(hex) = num.strip_prefix(['x', 'X']) {
            u32::from_str_radix(hex, 16).ok()
        } else {
            num.parse::<u32>().ok()
        }
        .and_then(char::from_u32);
        match ch {
            Some(c) => out.replace_range(start..start + end + 1, &c.to_string()),
            None => break,
        }
    }
    out
}

async fn fetch_qq(id: &str) -> Result<String, String> {
    let v: serde_json::Value = client()?
        .get(QQ_LYRIC)
        .header("Referer", "https://y.qq.com/")
        .query(&[("songmid", id), ("format", "json"), ("nobase64", "1")])
        .send()
        .await
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|e| e.to_string())?;
    let text = v["lyric"].as_str().unwrap_or_default();
    if text.trim().is_empty() {
        return Err("This source has no lyrics for that song".into());
    }
    Ok(decode_entities(text))
}

// ---------- commands ----------

/// Searches both sources at once; a source that fails or times out doesn't stop the other.
/// Timestamped results come first, and QQ Music's before LRCLIB's: it tends to have the right studio version,
/// while LRCLIB also returns many live and alternate takes.
#[tauri::command]
pub async fn lyrics_search(title: String, album: String, artist: String) -> Result<Vec<Hit>, String> {
    let (title, album, artist) = (title.trim().to_string(), album.trim().to_string(), artist.trim().to_string());
    if title.is_empty() && artist.is_empty() && album.is_empty() {
        return Ok(vec![]);
    }
    let q1 = (title.clone(), album.clone(), artist.clone());
    let lrclib = tauri::async_runtime::spawn(async move { search_lrclib(&q1.0, &q1.1, &q1.2).await });
    let qq = tauri::async_runtime::spawn(async move { search_qq(&title, &album, &artist).await });
    let a = lrclib.await.unwrap_or_else(|e| Err(e.to_string()));
    let b = qq.await.unwrap_or_else(|e| Err(e.to_string()));
    let mut hits = Vec::new();
    let mut errors = Vec::new();
    for (name, r) in [("QQ Music", b), ("LRCLIB", a)] {
        match r {
            Ok(h) => hits.extend(h),
            Err(e) => errors.push(format!("{name}: {e}")),
        }
    }
    if hits.is_empty() && !errors.is_empty() {
        return Err(errors.join("; "));
    }
    // Stable sort: known-synced first, then not-yet-known (QQ, which is mostly synced), then unsynced.
    hits.sort_by_key(|h| match h.synced {
        Some(true) => 0,
        None => 1,
        Some(false) => 2,
    });
    Ok(hits)
}

/// The lyrics text for a result whose search didn't include it.
#[tauri::command]
pub async fn lyrics_fetch(source: String, id: String) -> Result<String, String> {
    match source.as_str() {
        "QQ Music" => fetch_qq(&id).await,
        _ => Err("Unknown source".into()),
    }
}

/// Saves the chosen lyrics for the track that's playing and shows them straight away.
#[tauri::command]
pub fn lyrics_apply(app: AppHandle, content: String) -> Result<String, String> {
    let snap = app.state::<Shared>().media.lock().unwrap().clone();
    if !snap.active {
        return Err("Play the song these lyrics are for, then apply them.".into());
    }
    let synced = !lyrics::parse_lrc(&content).is_empty();
    let name = lyrics::save_local(&app, &snap, &content, synced)?;
    lyrics::reload(&app);
    Ok(name)
}

#[tauri::command]
pub fn close_lyrics_search(app: AppHandle) {
    if let Some(w) = app.get_webview_window("lyrsearch") {
        let _ = w.close();
    }
}

/// Opens the search window, or brings it to the front if it's already open.
///
/// Built on a worker thread: on Windows, creating a webview window straight from a command or tray-menu handler (which
/// run on the thread that owns the event loop) can deadlock, leaving a blank white window that can't be closed.
pub fn open_window(app: &AppHandle) {
    let app = app.clone();
    std::thread::spawn(move || {
        if let Some(w) = app.get_webview_window("lyrsearch") {
            let _ = w.unminimize();
            let _ = w.set_focus();
            return;
        }
        let built = WebviewWindowBuilder::new(&app, "lyrsearch", WebviewUrl::App("lyrsearch.html".into()))
            .title("Search for lyrics")
            .inner_size(1000.0, 600.0)
            .min_inner_size(780.0, 480.0)
            .center()
            .focused(true)
            .build();
        if let Ok(w) = built {
            let _ = w.set_focus();
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn decodes_entities() {
        assert_eq!(decode_entities("Don&apos;t &amp; won&#39;t&#10;stop"), "Don't & won't\nstop");
        assert_eq!(decode_entities("a &#x41; b"), "a A b");
    }
}
