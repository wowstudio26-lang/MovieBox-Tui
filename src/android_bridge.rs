use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jint, jstring};

use crate::providers::models::ProviderKind;
use crate::service::MovieBoxService;

fn json_response<T: serde::Serialize>(value: T) -> String {
    serde_json::to_string(&value)
        .unwrap_or_else(|_| r#"{"ok":false,"error":"serialization failed"}"#.to_string())
}

#[derive(serde::Serialize)]
struct AndroidSearchResult {
    id: String,
    title: String,
    year: String,
    media_type: String,
    poster_url: Option<String>,
    provider: String,
}

#[derive(serde::Serialize)]
struct AndroidSearchResponse {
    ok: bool,
    results: Vec<AndroidSearchResult>,
    error: Option<String>,
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wowstudio26_movieboxleo_RustBridge_nativeSearch(
    mut env: JNIEnv,
    _class: JClass,
    query: JString,
) -> jstring {
    let query: String = match env.get_string(&query) {
        Ok(value) => value.into(),
        Err(error) => {
            return env
                .new_string(json_response(AndroidSearchResponse {
                    ok: false,
                    results: Vec::new(),
                    error: Some(format!("invalid query: {error}")),
                }))
                .map(|value| value.into_raw())
                .unwrap_or(std::ptr::null_mut());
        }
    };

    let query = query.trim().to_string();
    if query.is_empty() {
        return env
            .new_string(json_response(AndroidSearchResponse {
                ok: true,
                results: Vec::new(),
                error: None,
            }))
            .map(|value| value.into_raw())
            .unwrap_or(std::ptr::null_mut());
    }

    let response = match tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()
    {
        Ok(runtime) => runtime.block_on(async move {
            let service = MovieBoxService::new();
            match service
                .search_typed(ProviderKind::MovieBox, &query, 1)
                .await
            {
                Ok(items) => AndroidSearchResponse {
                    ok: true,
                    results: items
                        .into_iter()
                        .map(|item| AndroidSearchResult {
                            id: item.id.value,
                            title: item.title,
                            year: item.year.unwrap_or_default(),
                            media_type: format!("{:?}", item.media_type),
                            poster_url: item.poster_url,
                            provider: format!("{:?}", item.id.provider),
                        })
                        .collect(),
                    error: None,
                },
                Err(error) => AndroidSearchResponse {
                    ok: false,
                    results: Vec::new(),
                    error: Some(error.to_string()),
                },
            }
        }),
        Err(error) => AndroidSearchResponse {
            ok: false,
            results: Vec::new(),
            error: Some(format!("Rust runtime error: {error}")),
        },
    };

    env.new_string(json_response(response))
        .map(|value| value.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[derive(serde::Serialize)]
struct AndroidDetailsResponse {
    ok: bool,
    details: Option<crate::providers::models::MediaDetails>,
    error: Option<String>,
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wowstudio26_movieboxleo_RustBridge_nativeDetails(
    mut env: JNIEnv,
    _class: JClass,
    subject_id: JString,
) -> jstring {
    let subject_id: String = match env.get_string(&subject_id) {
        Ok(value) => value.into(),
        Err(error) => {
            return env
                .new_string(json_response(AndroidDetailsResponse {
                    ok: false,
                    details: None,
                    error: Some(format!("invalid subject id: {error}")),
                }))
                .map(|value| value.into_raw())
                .unwrap_or(std::ptr::null_mut());
        }
    };

    let response = match tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()
    {
        Ok(runtime) => runtime.block_on(async move {
            let service = MovieBoxService::new();
            match service
                .details_typed(ProviderKind::MovieBox, subject_id.trim())
                .await
            {
                Ok(details) => AndroidDetailsResponse {
                    ok: true,
                    details: Some(details),
                    error: None,
                },
                Err(error) => AndroidDetailsResponse {
                    ok: false,
                    details: None,
                    error: Some(error.to_string()),
                },
            }
        }),
        Err(error) => AndroidDetailsResponse {
            ok: false,
            details: None,
            error: Some(format!("Rust runtime error: {error}")),
        },
    };

    env.new_string(json_response(response))
        .map(|value| value.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[derive(serde::Serialize)]
struct AndroidPlaybackOption {
    quality: String,
    resolution: u64,
    url: String,
    headers: Vec<(String, String)>,
}

#[derive(serde::Serialize)]
struct AndroidPlaybackResponse {
    ok: bool,
    options: Vec<AndroidPlaybackOption>,
    error: Option<String>,
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wowstudio26_movieboxleo_RustBridge_nativePlayback(
    mut env: JNIEnv,
    _class: JClass,
    subject_id: JString,
    season: jint,
    episode: jint,
) -> jstring {
    let subject_id: String = match env.get_string(&subject_id) {
        Ok(value) => value.into(),
        Err(error) => {
            return env
                .new_string(json_response(AndroidPlaybackResponse {
                    ok: false,
                    options: Vec::new(),
                    error: Some(format!("invalid subject id: {error}")),
                }))
                .map(|value| value.into_raw())
                .unwrap_or(std::ptr::null_mut());
        }
    };

    let response = match tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()
    {
        Ok(runtime) => runtime.block_on(async move {
            use crate::providers::ReleaseProvider;

            let service = MovieBoxService::new();
            match service
                .client
                .episode_streams(
                    subject_id.trim(),
                    season.max(0) as usize,
                    episode.max(0) as usize,
                )
                .await
            {
                Ok(releases) => {
                    let options = releases
                        .into_iter()
                        .filter_map(|release| {
                            let resolution = release.resolution_u64();
                            let mirror = release.mirrors.into_iter().next()?;
                            Some(AndroidPlaybackOption {
                                quality: release
                                    .quality
                                    .unwrap_or_else(|| format!("{resolution}p")),
                                resolution,
                                url: mirror.resolver_url,
                                headers: mirror.headers,
                            })
                        })
                        .collect::<Vec<_>>();

                    if options.is_empty() {
                        AndroidPlaybackResponse {
                            ok: false,
                            options: Vec::new(),
                            error: Some("No playable stream URL found".to_string()),
                        }
                    } else {
                        AndroidPlaybackResponse {
                            ok: true,
                            options,
                            error: None,
                        }
                    }
                }
                Err(error) => AndroidPlaybackResponse {
                    ok: false,
                    options: Vec::new(),
                    error: Some(error.to_string()),
                },
            }
        }),
        Err(error) => AndroidPlaybackResponse {
            ok: false,
            options: Vec::new(),
            error: Some(format!("Rust runtime error: {error}")),
        },
    };

    env.new_string(json_response(response))
        .map(|value| value.into_raw())
        .unwrap_or(std::ptr::null_mut())
}
