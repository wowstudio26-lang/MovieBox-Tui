use std::{
    collections::HashMap,
    io,
    path::{Path, PathBuf},
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, AtomicU64, Ordering},
    },
    time::{SystemTime, UNIX_EPOCH},
};

use reqwest::header::{HeaderMap, HeaderName, HeaderValue};
use serde::{Deserialize, Serialize};
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::{TcpListener, TcpStream},
};

use crate::{
    providers::{ReleaseProvider, models::ProviderKind},
    service::MovieBoxService,
};

const HTML: &str = include_str!("../web/index.html");
const MAX_HEADER_BYTES: usize = 64 * 1024;
const MAX_BODY_BYTES: usize = 1024 * 1024;

struct AppState {
    service: MovieBoxService,
    jobs: Mutex<HashMap<String, DownloadJob>>,
    next_id: AtomicU64,
}

struct DownloadJob {
    id: String,
    title: String,
    url: String,
    path: PathBuf,
    headers: Vec<(String, String)>,
    state: String,
    downloaded: u64,
    total: Option<u64>,
    bytes_per_second: f64,
    cancel: Arc<AtomicBool>,
    remove_when_stopped: bool,
}

#[derive(Serialize)]
struct DownloadSummary {
    id: String,
    title: String,
    state: String,
    downloaded: u64,
    total: Option<u64>,
    bytes_per_second: f64,
    path: String,
}

#[derive(Serialize)]
struct LibraryItem {
    name: String,
    path: String,
    size: u64,
    kind: String,
}

#[derive(Deserialize)]
struct NewDownload {
    url: String,
    title: String,
    #[serde(default)]
    media_type: String,
    #[serde(default)]
    season: usize,
    #[serde(default)]
    episode: usize,
    #[serde(default)]
    headers: Vec<(String, String)>,
    #[serde(default)]
    direct_file: bool,
    #[serde(default)]
    subtitle: bool,
    #[serde(default)]
    subtitle_language: String,
}

struct Request {
    method: String,
    target: String,
    body: Vec<u8>,
}

pub async fn run() -> io::Result<()> {
    let port = std::env::var("MOVIEBOX_WEB_PORT")
        .ok()
        .and_then(|value| value.parse::<u16>().ok())
        .unwrap_or(8787);
    let listener = TcpListener::bind(("127.0.0.1", port)).await?;
    let state = Arc::new(AppState {
        service: MovieBoxService::new(),
        jobs: Mutex::new(HashMap::new()),
        next_id: AtomicU64::new(1),
    });

    println!("MovieBox-TUI Web Downloader");
    println!("Open http://127.0.0.1:{port} in your phone browser.");
    println!("Local-only server; it does not listen on your Wi-Fi network.");
    println!("Downloads default to ~/storage/shared/Movies/MovieBox");
    println!("Stop the service with Ctrl+C in Termux.");

    loop {
        let (stream, _) = listener.accept().await?;
        let state = Arc::clone(&state);
        tokio::spawn(async move {
            if let Err(error) = handle_connection(stream, state).await {
                log::debug!("web request ended: {error}");
            }
        });
    }
}

async fn handle_connection(mut stream: TcpStream, state: Arc<AppState>) -> io::Result<()> {
    let Some(request) = read_request(&mut stream).await? else {
        return Ok(());
    };
    let parsed = url::Url::parse(&format!("http://127.0.0.1{}", request.target));
    let Ok(parsed) = parsed else {
        return respond(&mut stream, 400, "application/json", br#"{"error":"Invalid URL"}"#).await;
    };
    let path = parsed.path().to_string();
    let query: HashMap<String, String> = parsed.query_pairs()
        .map(|(key, value)| (key.into_owned(), value.into_owned()))
        .collect();

    if request.method == "GET" && path == "/" {
        return respond(&mut stream, 200, "text/html; charset=utf-8", HTML.as_bytes()).await;
    }
    if request.method == "GET" && path == "/health" {
        return json_response(&mut stream, 200, &serde_json::json!({"ok": true, "service": "moviebox-tui-web"})).await;
    }
    if request.method == "GET" && path == "/api/suggest" {
        let q = query.get("q").map(String::as_str).unwrap_or("").trim();
        if q.len() < 2 {
            return json_response(&mut stream, 200, &serde_json::json!({"results": []})).await;
        }
        return match state.service.suggest(q).await {
            Ok(results) => json_response(&mut stream, 200, &serde_json::json!({"results": results})).await,
            Err(error) => json_response(&mut stream, 502, &serde_json::json!({"error": error})).await,
        };
    }
    if request.method == "GET" && path == "/api/search" {
        let q = query.get("q").map(String::as_str).unwrap_or("").trim();
        let page = query.get("page").and_then(|v| v.parse::<usize>().ok()).unwrap_or(1).max(1);
        if q.is_empty() {
            return json_response(&mut stream, 200, &serde_json::json!({"results": []})).await;
        }
        return match state.service.search_typed(ProviderKind::MovieBox, q, page).await {
            Ok(results) => json_response(&mut stream, 200, &serde_json::json!({"results": results})).await,
            Err(error) => json_response(&mut stream, 502, &serde_json::json!({"error": error.to_string()})).await,
        };
    }
    if request.method == "GET" && path == "/api/details" {
        let id = query.get("id").map(String::as_str).unwrap_or("").trim();
        if id.is_empty() {
            return json_response(&mut stream, 400, &serde_json::json!({"error": "Missing id"})).await;
        }
        return match state.service.details_typed(ProviderKind::MovieBox, id).await {
            Ok(details) => json_response(&mut stream, 200, &details).await,
            Err(error) => json_response(&mut stream, 502, &serde_json::json!({"error": error.to_string()})).await,
        };
    }
    if request.method == "GET" && path == "/api/releases" {
        let id = query.get("id").map(String::as_str).unwrap_or("").trim();
        let season = query.get("season").and_then(|v| v.parse::<usize>().ok()).unwrap_or(0);
        let episode = query.get("episode").and_then(|v| v.parse::<usize>().ok()).unwrap_or(0);
        if id.is_empty() {
            return json_response(&mut stream, 400, &serde_json::json!({"error": "Missing id"})).await;
        }
        return match state.service.client.episode_streams(id, season, episode).await {
            Ok(releases) => json_response(&mut stream, 200, &serde_json::json!({"releases": releases})).await,
            Err(error) => json_response(&mut stream, 502, &serde_json::json!({"error": error.to_string()})).await,
        };
    }
    if request.method == "GET" && path == "/api/subtitles" {
        let id = query.get("id").map(String::as_str).unwrap_or("").trim();
        let resource_id = query.get("resource_id").map(String::as_str).unwrap_or("").trim();
        let season = query.get("season").and_then(|v| v.parse::<usize>().ok()).unwrap_or(0);
        let episode = query.get("episode").and_then(|v| v.parse::<usize>().ok()).unwrap_or(0);
        let siblings = query.get("siblings").map(|value| value.split(',').map(str::to_string).collect::<Vec<_>>()).unwrap_or_default();
        if id.is_empty() || resource_id.is_empty() {
            return json_response(&mut stream, 400, &serde_json::json!({"error": "Missing id or resource_id"})).await;
        }
        return match state.service.get_ext_captions(id, resource_id, &siblings, season, episode).await {
            Ok(subtitles) => json_response(&mut stream, 200, &serde_json::json!({"subtitles": subtitles})).await,
            Err(error) => json_response(&mut stream, 502, &serde_json::json!({"error": error})).await,
        };
    }
    if request.method == "GET" && path == "/api/downloads" {
        let jobs = state.jobs.lock().unwrap_or_else(|error| error.into_inner());
        let summaries = jobs.values().map(|job| DownloadSummary {
            id: job.id.clone(),
            title: job.title.clone(),
            state: job.state.clone(),
            downloaded: job.downloaded,
            total: job.total,
            bytes_per_second: job.bytes_per_second,
            path: display_relative_path(&job.path),
        }).collect::<Vec<_>>();
        drop(jobs);
        return json_response(&mut stream, 200, &summaries).await;
    }
    if request.method == "GET" && path == "/api/library" {
        let root = match download_root() {
            Ok(root) => root,
            Err(error) => return json_response(&mut stream, 400, &serde_json::json!({"error": error})).await,
        };
        let mut items = Vec::new();
        if let Err(error) = collect_library(&root, &root, 0, &mut items).await {
            return json_response(&mut stream, 500, &serde_json::json!({"error": error.to_string()})).await;
        }
        items.sort_by(|a, b| a.path.to_lowercase().cmp(&b.path.to_lowercase()));
        return json_response(&mut stream, 200, &serde_json::json!({"root": root.display().to_string(), "items": items})).await;
    }
    if request.method == "POST" && path == "/api/downloads" {
        let payload: NewDownload = match serde_json::from_slice(&request.body) {
            Ok(payload) => payload,
            Err(error) => return json_response(&mut stream, 400, &serde_json::json!({"error": format!("Invalid request: {error}")})).await,
        };
        if !payload.direct_file {
            return json_response(&mut stream, 400, &serde_json::json!({"error": "This source is not marked as a direct file. Downloading a playlist manifest as a video would produce an invalid file."})).await;
        }
        let parsed_url = match url::Url::parse(&payload.url) {
            Ok(url) if matches!(url.scheme(), "http" | "https") => url,
            _ => return json_response(&mut stream, 400, &serde_json::json!({"error": "Download URL must be HTTP or HTTPS."})).await,
        };
        let root = match download_root() {
            Ok(root) => root,
            Err(error) => return json_response(&mut stream, 400, &serde_json::json!({"error": error})).await,
        };
        let title = crate::download::safe_file_stem(&payload.title);
        let is_series = payload.media_type.eq_ignore_ascii_case("series");
        let (folder, stem) = crate::download::resolve_media_target(&root, &title, is_series, payload.season, payload.episode);
        let extension = if payload.subtitle {
            subtitle_extension(parsed_url.path())
        } else {
            media_extension(parsed_url.path())
        };
        let file_stem = if payload.subtitle {
            format!("{stem} - {}", crate::download::safe_file_stem(&payload.subtitle_language))
        } else {
            stem
        };
        let destination = folder.join(format!("{file_stem}{extension}"));
        if destination.exists() {
            return json_response(&mut stream, 409, &serde_json::json!({"error": "A file with this name already exists. Remove it or rename the title before downloading again."})).await;
        }
        let counter = state.next_id.fetch_add(1, Ordering::Relaxed);
        let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_millis();
        let id = format!("d{now}-{counter}");
        let job_title = if payload.subtitle {
            format!("{} · {}", payload.title, payload.subtitle_language)
        } else if is_series {
            format!("{} S{:02}E{:02}", payload.title, payload.season, payload.episode)
        } else {
            payload.title.clone()
        };
        let job = DownloadJob {
            id: id.clone(),
            title: job_title,
            url: parsed_url.to_string(),
            path: destination,
            headers: payload.headers,
            state: "queued".to_string(),
            downloaded: 0,
            total: None,
            bytes_per_second: 0.0,
            cancel: Arc::new(AtomicBool::new(false)),
            remove_when_stopped: false,
        };
        state.jobs.lock().unwrap_or_else(|error| error.into_inner()).insert(id.clone(), job);
        if let Err(error) = launch_download(Arc::clone(&state), &id) {
            return json_response(&mut stream, 500, &serde_json::json!({"error": error})).await;
        }
        return json_response(&mut stream, 202, &serde_json::json!({"id": id, "state": "downloading"})).await;
    }
    if request.method == "POST" && path.starts_with("/api/downloads/") {
        let parts = path.trim_start_matches("/api/downloads/").split('/').collect::<Vec<_>>();
        if parts.len() == 2 {
            let id = parts[0];
            let action = parts[1];
            let action_result = match action {
                "pause" => {
                    let mut jobs = state.jobs.lock().unwrap_or_else(|error| error.into_inner());
                    match jobs.get_mut(id) {
                        Some(job) if job.state == "downloading" => {
                            job.cancel.store(true, Ordering::Relaxed);
                            job.state = "pausing".to_string();
                            Ok(())
                        }
                        Some(_) => Err("Only active downloads can be paused.".to_string()),
                        None => Err("Download not found.".to_string()),
                    }
                }
                "cancel" => {
                    let mut jobs = state.jobs.lock().unwrap_or_else(|error| error.into_inner());
                    match jobs.get_mut(id) {
                        Some(job) if job.state == "downloading" || job.state == "pausing" => {
                            job.remove_when_stopped = true;
                            job.cancel.store(true, Ordering::Relaxed);
                            job.state = "cancelling".to_string();
                            Ok(())
                        }
                        Some(_) => Err("Only active downloads can be cancelled.".to_string()),
                        None => Err("Download not found.".to_string()),
                    }
                }
                "resume" | "retry" => {
                    let should_start = {
                        let jobs = state.jobs.lock().unwrap_or_else(|error| error.into_inner());
                        jobs.get(id).is_some_and(|job| matches!(job.state.as_str(), "paused" | "failed"))
                    };
                    if should_start {
                        launch_download(Arc::clone(&state), id)
                    } else {
                        Err("Only paused or failed downloads can be resumed.".to_string())
                    }
                }
                "delete" => {
                    let job = {
                        let mut jobs = state.jobs.lock().unwrap_or_else(|error| error.into_inner());
                        match jobs.get(id) {
                            Some(job) if job.state != "downloading" && job.state != "pausing" && job.state != "cancelling" => jobs.remove(id),
                            Some(_) => None,
                            None => None,
                        }
                    };
                    match job {
                        Some(job) => {
                            if job.state == "completed" {
                                match tokio::fs::remove_file(&job.path).await {
                                    Ok(()) => Ok(()),
                                    Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(()),
                                    Err(error) => Err(format!("Could not delete file: {error}")),
                                }
                            } else {
                                Ok(())
                            }
                        }
                        None => Err("Download not found or still active.".to_string()),
                    }
                }
                _ => Err("Unknown download action.".to_string()),
            };
            return match action_result {
                Ok(()) => json_response(&mut stream, 200, &serde_json::json!({"ok": true})).await,
                Err(error) => json_response(&mut stream, 400, &serde_json::json!({"error": error})).await,
            };
        }
    }

    respond(&mut stream, 404, "application/json", br#"{"error":"Not found"}"#).await
}

fn launch_download(state: Arc<AppState>, id: &str) -> Result<(), String> {
    let (url, path, headers, cancel) = {
        let mut jobs = state.jobs.lock().unwrap_or_else(|error| error.into_inner());
        let job = jobs.get_mut(id).ok_or_else(|| "Download not found.".to_string())?;
        job.cancel.store(false, Ordering::Relaxed);
        job.remove_when_stopped = false;
        job.state = "downloading".to_string();
        (job.url.clone(), job.path.clone(), job.headers.clone(), Arc::clone(&job.cancel))
    };
    let id = id.to_string();
    tokio::spawn(async move {
        if let Some(parent) = path.parent() {
            if let Err(error) = tokio::fs::create_dir_all(parent).await {
                set_job_error(&state, &id, format!("Cannot create destination folder: {error}"));
                return;
            }
        }
        let mut header_map = HeaderMap::new();
        for (name, value) in &headers {
            if let (Ok(name), Ok(value)) = (HeaderName::from_bytes(name.as_bytes()), HeaderValue::from_str(value)) {
                header_map.insert(name, value);
            }
        }
        let client = match reqwest::Client::builder().default_headers(header_map).build() {
            Ok(client) => client,
            Err(error) => {
                set_job_error(&state, &id, format!("Cannot initialize downloader: {error}"));
                return;
            }
        };
        let state_for_progress = Arc::clone(&state);
        let id_for_progress = id.clone();
        let result = crate::download::download(&client, &url, &path, Arc::clone(&cancel), move |progress| {
            let mut jobs = state_for_progress.jobs.lock().unwrap_or_else(|error| error.into_inner());
            if let Some(job) = jobs.get_mut(&id_for_progress) {
                job.downloaded = progress.downloaded;
                job.total = progress.total;
                job.bytes_per_second = progress.bytes_per_second;
            }
        }).await;
        let remove = {
            let jobs = state.jobs.lock().unwrap_or_else(|error| error.into_inner());
            jobs.get(&id).is_some_and(|job| job.remove_when_stopped)
        };
        if remove {
            let _ = tokio::fs::remove_file(&path).await;
            let _ = tokio::fs::remove_file(append_suffix(&path, "part")).await;
            let _ = tokio::fs::remove_file(append_suffix(&path, "part.json")).await;
            if let Some(job) = state.jobs.lock().unwrap_or_else(|error| error.into_inner()).get_mut(&id) {
                job.state = "cancelled".to_string();
                job.downloaded = 0;
                job.total = None;
            }
            return;
        }
        let mut jobs = state.jobs.lock().unwrap_or_else(|error| error.into_inner());
        if let Some(job) = jobs.get_mut(&id) {
            match result {
                Ok(crate::download::DownloadOutcome::Completed { bytes }) => {
                    job.state = "completed".to_string();
                    job.downloaded = bytes;
                    job.total = Some(bytes);
                    job.bytes_per_second = 0.0;
                }
                Ok(crate::download::DownloadOutcome::Paused { bytes }) => {
                    job.state = "paused".to_string();
                    job.downloaded = bytes;
                    job.bytes_per_second = 0.0;
                }
                Err(error) => {
                    job.state = "failed".to_string();
                    job.bytes_per_second = 0.0;
                    log::warn!("download {} failed: {}", job.id, error);
                }
            }
        }
    });
    Ok(())
}

fn set_job_error(state: &AppState, id: &str, message: String) {
    log::warn!("download {id}: {message}");
    if let Some(job) = state.jobs.lock().unwrap_or_else(|error| error.into_inner()).get_mut(id) {
        job.state = "failed".to_string();
    }
}

fn download_root() -> Result<PathBuf, String> {
    if let Ok(custom) = std::env::var("MOVIEBOX_DOWNLOAD_DIR") {
        if !custom.trim().is_empty() {
            return Ok(PathBuf::from(custom));
        }
    }
    let home = dirs::home_dir().ok_or_else(|| "Could not determine Termux home directory.".to_string())?;
    let shared = home.join("storage").join("shared");
    if !shared.is_dir() {
        return Err("Termux shared storage is not enabled. Run termux-setup-storage in Termux, grant the permission, then restart MovieBox-TUI.".to_string());
    }
    Ok(shared.join("Movies").join("MovieBox"))
}

fn media_extension(path: &str) -> &'static str {
    match Path::new(path).extension().and_then(|value| value.to_str()).unwrap_or("").to_ascii_lowercase().as_str() {
        "mp4" => ".mp4",
        "mkv" => ".mkv",
        "webm" => ".webm",
        "mov" => ".mov",
        "m4v" => ".m4v",
        "ts" => ".ts",
        "avi" => ".avi",
        _ => ".media",
    }
}

fn subtitle_extension(path: &str) -> &'static str {
    match Path::new(path).extension().and_then(|value| value.to_str()).unwrap_or("").to_ascii_lowercase().as_str() {
        "vtt" => ".vtt",
        "ass" => ".ass",
        "ssa" => ".ssa",
        "srt" => ".srt",
        _ => ".srt",
    }
}

async fn collect_library(root: &Path, current: &Path, depth: usize, output: &mut Vec<LibraryItem>) -> io::Result<()> {
    if depth > 6 { return Ok(()); }
    let mut entries = match tokio::fs::read_dir(current).await {
        Ok(entries) => entries,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(()),
        Err(error) => return Err(error),
    };
    while let Some(entry) = entries.next_entry().await? {
        let path = entry.path();
        let metadata = match entry.metadata().await { Ok(metadata) => metadata, Err(_) => continue };
        if metadata.is_dir() {
            Box::pin(collect_library(root, &path, depth + 1, output)).await?;
            continue;
        }
        if !metadata.is_file() { continue; }
        let extension = path.extension().and_then(|value| value.to_str()).unwrap_or("").to_ascii_lowercase();
        if !matches!(extension.as_str(), "mp4" | "mkv" | "webm" | "mov" | "m4v" | "ts" | "avi" | "media" | "srt" | "vtt" | "ass" | "ssa") {
            continue;
        }
        let relative = path.strip_prefix(root).unwrap_or(&path).to_string_lossy().replace('\\', "/");
        output.push(LibraryItem {
            name: path.file_name().unwrap_or_default().to_string_lossy().to_string(),
            path: relative,
            size: metadata.len(),
            kind: if matches!(extension.as_str(), "srt" | "vtt" | "ass" | "ssa") { "subtitle".to_string() } else { "video".to_string() },
        });
    }
    Ok(())
}

fn display_relative_path(path: &Path) -> String {
    let root = download_root().ok();
    root.and_then(|root| path.strip_prefix(root).ok().map(|p| p.to_string_lossy().replace('\\', "/")))
        .unwrap_or_else(|| path.display().to_string())
}

async fn read_request(stream: &mut TcpStream) -> io::Result<Option<Request>> {
    let mut raw = Vec::new();
    let mut chunk = [0u8; 8192];
    let mut header_end = None;
    let mut content_length = 0usize;
    loop {
        let read = stream.read(&mut chunk).await?;
        if read == 0 {
            return Ok(None);
        }
        raw.extend_from_slice(&chunk[..read]);
        if header_end.is_none() {
            if let Some(index) = find_bytes(&raw, b"\r\n\r\n") {
                header_end = Some(index);
                if index > MAX_HEADER_BYTES {
                    return Err(io::Error::new(io::ErrorKind::InvalidData, "HTTP headers too large"));
                }
                let headers = String::from_utf8_lossy(&raw[..index]);
                for line in headers.lines().skip(1) {
                    if let Some((name, value)) = line.split_once(':') {
                        if name.trim().eq_ignore_ascii_case("content-length") {
                            content_length = value.trim().parse::<usize>().unwrap_or(0);
                        }
                    }
                }
                if content_length > MAX_BODY_BYTES {
                    return Err(io::Error::new(io::ErrorKind::InvalidData, "HTTP body too large"));
                }
            }
        }
        if let Some(index) = header_end {
            if raw.len() >= index + 4 + content_length { break; }
        }
        if raw.len() > MAX_HEADER_BYTES + MAX_BODY_BYTES + 4 {
            return Err(io::Error::new(io::ErrorKind::InvalidData, "HTTP request too large"));
        }
    }
    let index = header_end.ok_or_else(|| io::Error::new(io::ErrorKind::InvalidData, "missing HTTP headers"))?;
    let header_text = String::from_utf8_lossy(&raw[..index]);
    let mut parts = header_text.lines().next().unwrap_or("").split_whitespace();
    let method = parts.next().unwrap_or("").to_ascii_uppercase();
    let target = parts.next().unwrap_or("/").to_string();
    let body_start = index + 4;
    let body_end = body_start + content_length;
    Ok(Some(Request { method, target, body: raw[body_start..body_end].to_vec() }))
}

fn find_bytes(haystack: &[u8], needle: &[u8]) -> Option<usize> {
    haystack.windows(needle.len()).position(|window| window == needle)
}

async fn json_response<T: Serialize>(stream: &mut TcpStream, status: u16, value: &T) -> io::Result<()> {
    let body = serde_json::to_vec(value).unwrap_or_else(|_| br#"{"error":"JSON serialization failed"}"#.to_vec());
    respond(stream, status, "application/json; charset=utf-8", &body).await
}

async fn respond(stream: &mut TcpStream, status: u16, content_type: &str, body: &[u8]) -> io::Result<()> {
    let reason = match status {
        200 => "OK",
        202 => "Accepted",
        400 => "Bad Request",
        404 => "Not Found",
        409 => "Conflict",
        500 => "Internal Server Error",
        502 => "Bad Gateway",
        _ => "OK",
    };
    let header = format!(
        "HTTP/1.1 {status} {reason}\r\nContent-Type: {content_type}\r\nContent-Length: {}\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n",
        body.len()
    );
    stream.write_all(header.as_bytes()).await?;
    stream.write_all(body).await?;
    stream.shutdown().await
}


fn append_suffix(path: &Path, suffix: &str) -> PathBuf {
    let mut name = path.as_os_str().to_os_string();
    name.push(".");
    name.push(suffix);
    PathBuf::from(name)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sidecar_suffix_keeps_the_original_extension() {
        assert_eq!(
            append_suffix(Path::new("episode.mp4"), "part"),
            PathBuf::from("episode.mp4.part")
        );
        assert_eq!(
            append_suffix(Path::new("episode.mp4"), "part.json"),
            PathBuf::from("episode.mp4.part.json")
        );
    }

    #[test]
    fn only_known_media_extensions_are_preserved() {
        assert_eq!(media_extension("/movie.mp4"), ".mp4");
        assert_eq!(media_extension("/movie.mkv"), ".mkv");
        assert_eq!(media_extension("/movie.m3u8"), ".media");
        assert_eq!(subtitle_extension("/subtitles.vtt"), ".vtt");
    }
}
