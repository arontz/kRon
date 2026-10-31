//! QQ Music's unofficial lyrics endpoints, used both by the automatic lookup (main source) and the search window.
//! It is not an official public service, so every caller must cope with it failing or changing.

use serde_json::Value;

const SEARCH: &str = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
const LYRIC: &str = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg";
const REFERER: &str = "https://y.qq.com/";

/// A lyric whose length differs from the playing track by more than this is another version (live, extended).
const MAX_DURATION_GAP_SECS: i64 = 15;

#[derive(Clone, Debug, PartialEq)]
pub struct Song {
    pub mid: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub seconds: i64,
}

fn client() -> Result<reqwest::Client, String> {
    reqwest::Client::builder()
        .user_agent("kRon/0.3 (Windows taskbar lyrics)")
        .timeout(std::time::Duration::from_secs(10))
        .build()
        .map_err(|e| e.to_string())
}

pub fn parse_search(v: &Value) -> Vec<Song> {
    let Some(list) = v["data"]["song"]["list"].as_array() else { return vec![] };
    list.iter()
        .filter_map(|s| {
            let mid = s["songmid"].as_str().filter(|m| !m.is_empty())?.to_string();
            let artist = s["singer"]
                .as_array()
                .map(|a| a.iter().filter_map(|x| x["name"].as_str()).collect::<Vec<_>>().join(", "))
                .unwrap_or_default();
            Some(Song {
                mid,
                title: s["songname"].as_str().unwrap_or_default().to_string(),
                artist,
                album: s["albumname"].as_str().unwrap_or_default().to_string(),
                seconds: s["interval"].as_i64().unwrap_or(0),
            })
        })
        .collect()
}

pub async fn search(query: &str) -> Result<Vec<Song>, String> {
    let v: Value = client()?
        .get(SEARCH)
        .header("Referer", REFERER)
        .query(&[("w", query), ("format", "json"), ("n", "15"), ("p", "1")])
        .send()
        .await
        .map_err(|e| e.to_string())?
        .error_for_status()
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|e| e.to_string())?;
    Ok(parse_search(&v))
}

/// The LRC text for a song, or `None` when QQ Music has no lyrics for it.
pub async fn lyric(mid: &str) -> Result<Option<String>, String> {
    let v: Value = client()?
        .get(LYRIC)
        .header("Referer", REFERER)
        .query(&[("songmid", mid), ("format", "json"), ("nobase64", "1")])
        .send()
        .await
        .map_err(|e| e.to_string())?
        .error_for_status()
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|e| e.to_string())?;
    let text = v["lyric"].as_str().unwrap_or_default();
    Ok(if text.trim().is_empty() { None } else { Some(decode_entities(text)) })
}

/// Looks the playing song up: search, pick the matching result, fetch its lyrics.
/// `Ok(None)` means QQ Music answered but has no matching song or no lyrics; `Err` means it could not be reached.
pub async fn find(title: &str, artist: &str, duration_ms: i64) -> Result<Option<String>, String> {
    let query = [artist, title].iter().map(|s| s.trim()).filter(|s| !s.is_empty()).collect::<Vec<_>>().join(" ");
    if query.is_empty() {
        return Ok(None);
    }
    let songs = search(&query).await?;
    match pick(&songs, title, artist, duration_ms) {
        Some(song) => lyric(&song.mid).await,
        None => Ok(None),
    }
}

fn norm(s: &str) -> String {
    s.chars().filter(|c| c.is_alphanumeric()).flat_map(char::to_lowercase).collect()
}

/// The text outside any (...), [...], （...） or 【...】, so "Song (Live)" and "Song [Remastered]" compare as "Song".
fn without_brackets(s: &str) -> String {
    let mut depth = 0;
    let mut out = String::new();
    for c in s.chars() {
        match c {
            '(' | '[' | '（' | '【' => depth += 1,
            ')' | ']' | '）' | '】' => depth = (depth - 1).max(0),
            _ if depth == 0 => out.push(c),
            _ => {}
        }
    }
    out
}

/// The result that is this song, or `None`. The title must match (ignoring bracketed tags), a known artist must
/// overlap, and when both lengths are known they must be within 15 s. Among the rest, the closest length wins.
pub fn pick<'a>(songs: &'a [Song], title: &str, artist: &str, duration_ms: i64) -> Option<&'a Song> {
    let want_title = norm(&without_brackets(title));
    let want_artist = norm(artist);
    if want_title.is_empty() {
        return None;
    }
    songs
        .iter()
        .filter_map(|s| {
            if norm(&without_brackets(&s.title)) != want_title {
                return None;
            }
            let mut score: i64 = if norm(&s.title) == norm(title) { 100 } else { 90 };
            if !want_artist.is_empty() {
                let a = norm(&s.artist);
                if a.is_empty() || !(a.contains(&want_artist) || want_artist.contains(&a)) {
                    return None;
                }
            }
            if duration_ms > 0 && s.seconds > 0 {
                let gap = (duration_ms / 1000 - s.seconds).abs();
                if gap > MAX_DURATION_GAP_SECS {
                    return None;
                }
                score -= gap;
            }
            Some((s, score))
        })
        .max_by_key(|(_, score)| *score)
        .map(|(s, _)| s)
}

/// QQ sends lyrics with HTML entities, sometimes even in the timestamps (`&#58;`, `&#46;`).
pub fn decode_entities(s: &str) -> String {
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

#[cfg(test)]
mod tests {
    use super::*;

    fn song(mid: &str, title: &str, artist: &str, seconds: i64) -> Song {
        Song { mid: mid.into(), title: title.into(), artist: artist.into(), album: String::new(), seconds }
    }

    /// Talks to the real service, so it only runs on request: `cargo test -- --ignored live_lookup`.
    #[test]
    #[ignore]
    fn live_lookup() {
        let text = tauri::async_runtime::block_on(find("Never Gonna Give You Up", "Rick Astley", 213_000))
            .expect("QQ Music reachable")
            .expect("QQ Music has lyrics for this song");
        assert!(text.contains("[00:"), "expected timestamped lyrics, got: {}", &text[..text.len().min(120)]);
    }

    #[test]
    fn decodes_entities() {
        assert_eq!(decode_entities("Don&apos;t &amp; won&#39;t&#10;stop"), "Don't & won't\nstop");
        assert_eq!(decode_entities("a &#x41; b"), "a A b");
        assert_eq!(decode_entities("[00&#58;01&#46;00]hi"), "[00:01.00]hi");
    }

    #[test]
    fn parses_search_results() {
        let v: Value = serde_json::from_str(
            r#"{"data":{"song":{"list":[
                {"songmid":"001abc","songname":"Never Gonna Give You Up","singer":[{"name":"Rick Astley"}],"albumname":"W","interval":213},
                {"songmid":"002def","songname":"Duet","singer":[{"name":"A"},{"name":"B"}],"interval":180},
                {"songname":"no mid"}
            ]}}}"#,
        )
        .unwrap();
        let songs = parse_search(&v);
        assert_eq!(songs.len(), 2);
        assert_eq!(songs[0], Song { mid: "001abc".into(), title: "Never Gonna Give You Up".into(), artist: "Rick Astley".into(), album: "W".into(), seconds: 213 });
        assert_eq!(songs[1].artist, "A, B");
        assert!(parse_search(&serde_json::json!({"data": {}})).is_empty());
    }

    #[test]
    fn the_studio_version_beats_a_live_take() {
        let songs = [song("live", "Song", "Artist", 260), song("studio", "Song", "Artist", 201)];
        assert_eq!(pick(&songs, "Song", "Artist", 200_000).map(|s| s.mid.as_str()), Some("studio"));
    }

    #[test]
    fn matching_ignores_case_punctuation_brackets_and_extra_artists() {
        let songs = [song("a", "Don't Stop! (Explicit)", "Artist, Guest", 0)];
        assert_eq!(pick(&songs, "don't stop", "ARTIST", 0).map(|s| s.mid.as_str()), Some("a"));
    }

    #[test]
    fn a_different_song_is_never_picked() {
        let songs = [song("x", "Other Song", "Artist", 200), song("y", "Song", "Someone Else", 200)];
        assert!(pick(&songs, "Song", "Artist", 200_000).is_none());
        assert!(pick(&[], "Song", "Artist", 200_000).is_none());
        assert!(pick(&songs, "", "Artist", 0).is_none());
    }

    #[test]
    fn without_an_artist_the_title_alone_decides_and_unknown_length_rules_nobody_out() {
        let songs = [song("a", "Song", "Whoever", 999)];
        assert_eq!(pick(&songs, "Song", "", -1).map(|s| s.mid.as_str()), Some("a"));
    }
}
