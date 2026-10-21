#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
command -v java >/dev/null
command -v python3.12 >/dev/null
sdk_root="${ANDROID_HOME:-$HOME/.cache/cloud-android-sdk}"
export ANDROID_HOME="$sdk_root"
export ANDROID_SDK_ROOT="$sdk_root"
sdkmanager_bin="$sdk_root/cmdline-tools/20.0/bin/sdkmanager"
if [[ ! -x "$sdkmanager_bin" ]]; then
  if command -v sdkmanager >/dev/null; then
    sdkmanager_bin="$(command -v sdkmanager)"
  else
    command -v curl >/dev/null
    command -v unzip >/dev/null
    task_sdk_tmp="$(mktemp -d)"
    trap 'rm -rf "$task_sdk_tmp"' EXIT
    curl --fail --location --retry 2 --connect-timeout 30 --max-time 600       https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip       -o "$task_sdk_tmp/tools.zip"
    printf '%s  %s\n' 04453066b540409d975c676d781da1477479dde3761310f1a7eb92a1dfb15af7 "$task_sdk_tmp/tools.zip" | sha256sum --check --status
    unzip -q "$task_sdk_tmp/tools.zip" -d "$task_sdk_tmp/unpacked"
    mkdir -p "$sdk_root/cmdline-tools"
    mv "$task_sdk_tmp/unpacked/cmdline-tools" "$sdk_root/cmdline-tools/20.0"
  fi
fi
# sdkmanager may stop reading early; ignore SIGPIPE from yes, not SDK failures.
set +o pipefail
yes | "$sdkmanager_bin" --sdk_root="$sdk_root" --licenses >/dev/null
sdk_license_status=${PIPESTATUS[1]}
set -o pipefail
test "$sdk_license_status" -eq 0
"$sdkmanager_bin" --sdk_root="$sdk_root" 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'
mkdir -p .cloud
printf 'export ANDROID_HOME=%q\nexport ANDROID_SDK_ROOT=%q\nexport PATH=%q:"$PATH"\n'   "$sdk_root" "$sdk_root" "$sdk_root/platform-tools:$sdk_root/cmdline-tools/20.0/bin" > .cloud/env.sh
printf 'export SHICANG_BUILD_PYTHON=%q\n' "$(command -v python3.12)" >> .cloud/env.sh
printf '%s\n' 'Android cloud toolchain ready. Run: source .cloud/env.sh'
