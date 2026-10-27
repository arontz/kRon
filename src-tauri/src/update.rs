//! "Check for updates…": asks GitHub for the latest release and, if it's newer, offers to open its download page.


use windows::core::HSTRING;
use windows::Win32::UI::WindowsAndMessaging::{
    MessageBoxW, IDYES, MB_ICONERROR, MB_ICONINFORMATION, MB_OK, MB_SETFOREGROUND, MB_TOPMOST, MB_YESNO,
};

const LATEST_API: &str = "https://api.github.com/repos/arontz/kRon/releases/latest";
const RELEASES_PAGE: &str = "https://github.com/arontz/kRon/releases/latest";
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

enum Outcome {
    UpToDate,
    Newer { version: String, url: String },
    Failed,
}

/// "v0.10.2" -> [0, 10, 2]; anything after a '-' (pre-release) is ignored.
fn parse_version(s: &str) -> Vec<u64> {
    s.trim()
        .trim_start_matches(['v', 'V'])
        .split('-')
        .next()
        .unwrap_or("")
        .split('.')
        .map(|p| p.parse().unwrap_or(0))
        .collect()
}

fn is_newer(latest: &str, current: &str) -> bool {
    let (mut a, mut b) = (parse_version(latest), parse_version(current));
    let n = a.len().max(b.len());
    a.resize(n, 0);
    b.resize(n, 0);
    a > b
}

async fn check() -> Outcome {
    let client = match reqwest::Client::builder().user_agent(format!("kRon/{VERSION}")).build() {
        Ok(c) => c,
        Err(_) => return Outcome::Failed,
    };
    let release: serde_json::Value = match client.get(LATEST_API).send().await {
        Ok(r) if r.status().is_success() => match r.json().await {
            Ok(v) => v,
            Err(_) => return Outcome::Failed,
        },
        _ => return Outcome::Failed,
    };
    let Some(tag) = release["tag_name"].as_str() else { return Outcome::Failed };
    if is_newer(tag, VERSION) {
        Outcome::Newer {
            version: tag.trim_start_matches(['v', 'V']).to_string(),
            url: release["html_url"].as_str().unwrap_or(RELEASES_PAGE).to_string(),
        }
    } else {
        Outcome::UpToDate
    }
}

/// Native message box; blocks until dismissed, so call it off the UI threads. Returns true for "Yes".
fn message(text: &str, buttons_yes_no: bool, error: bool) -> bool {
    let flags = if buttons_yes_no { MB_YESNO } else { MB_OK }
        | if error { MB_ICONERROR } else { MB_ICONINFORMATION }
        | MB_TOPMOST
        | MB_SETFOREGROUND;
    unsafe { MessageBoxW(None, &HSTRING::from(text), &HSTRING::from("kRon"), flags) == IDYES }
}

pub fn check_for_updates() {
    tauri::async_runtime::spawn(async {
        let outcome = check().await;
        let _ = tauri::async_runtime::spawn_blocking(move || match outcome {
            Outcome::UpToDate => {
                message(&format!("You're up to date. kRon {VERSION} is the latest version."), false, false);
            }
            Outcome::Newer { version, url } => {
                let ask = format!("kRon {version} is available (you have {VERSION}).\n\nOpen the download page?");
                if message(&ask, true, false) {
                    crate::lyrics::open_url(&url);
                }
            }
            Outcome::Failed => {
                message("Couldn't check for updates. Check your internet connection and try again.", false, true);
            }
        })
        .await;
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn compares_versions_numerically() {
        assert!(is_newer("v0.10.0", "0.9.9"));
        assert!(is_newer("1.0.0", "0.99.99"));
        assert!(is_newer("v0.2.1", "0.2.0"));
        assert!(!is_newer("v0.2.0", "0.2.0"));
        assert!(!is_newer("v0.1.9", "0.2.0"));
        assert!(!is_newer("v0.2", "0.2.0"));
    }

    #[test]
    fn ignores_prerelease_suffix() {
        assert_eq!(parse_version("v1.2.3-beta.1"), vec![1, 2, 3]);
    }
}
