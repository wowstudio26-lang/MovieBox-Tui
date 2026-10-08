pub mod cache;
pub mod config;
pub mod download;
pub mod favorites;
pub mod history;
pub mod logging;
pub mod models;
pub mod net;
pub mod player;
pub mod providers;
pub mod proxy;
pub mod service;
pub mod tui;
pub mod updater;

#[cfg(target_os = "android")]
pub mod android_bridge;
