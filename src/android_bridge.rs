use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jstring;

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

#[no_mangle]
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
