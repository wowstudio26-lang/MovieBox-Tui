# Local Web Downloader (Android / Termux)

This mode serves a mobile-first web interface from MovieBox-TUI on the same Android phone. The browser is the UI; the Rust process on Termux performs source discovery and direct-file downloads. The service binds to 127.0.0.1 only and is not exposed to your Wi-Fi network.

## Requirements

- Android with Termux installed from a trusted, current source.
- Rust toolchain compatible with the project's rust-version in Cargo.toml.
- Shared-storage access granted to Termux.
- A network connection for searching and downloading.

## First-time setup

1. In Termux, enable Android shared-storage access:

       termux-setup-storage

   Accept the Android permission prompt.

2. Install the build tools if they are not already installed:

       pkg update
       pkg install rust git clang make

3. Clone the repository and check out the web-downloader branch:

       git clone https://github.com/wowstudio26-lang/MovieBox-Tui.git
       cd MovieBox-Tui
       git switch web-downloader-mvp

4. Build the binary:

       cargo build --release --locked

5. Start the local web service:

       ./target/release/moviebox-tui --web

6. Open this address in the browser on the same phone:

   http://127.0.0.1:8787

Keep the Termux process running while downloading. Android battery management can stop background processes; exempt Termux from battery optimization for long-running downloads if needed.

## Storage location

The default destination is:

    ~/storage/shared/Movies/MovieBox/

Movie files are organized under Movies/; series files are organized by title and season under Series/. Subtitle downloads are saved as separate files next to the matching video. If shared storage is not available, run termux-setup-storage and grant permission.

A custom destination can be set with MOVIEBOX_DOWNLOAD_DIR before starting the service. The directory must be writable by Termux.

## Current milestone limitations

- This is a download-only interface; it does not include a video player.
- The initial web service supports direct-file sources only. It intentionally rejects sources marked as segmented/non-direct instead of saving an HLS/DASH manifest as if it were a complete video.
- Available resolution, audio variants, episode lists, and subtitle choices depend on the source.
- External subtitles are downloaded as separate files. This milestone does not remux audio/subtitle tracks into a new container.
- Up to two downloads run concurrently in this milestone. The download queue is currently held in service memory. Partial download data is retained by the existing downloader, but queue entries are not yet restored automatically after the service itself exits.
- This milestone should be tested on a real Android device before relying on it for large downloads.
