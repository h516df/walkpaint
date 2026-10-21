#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [[ -f .cloud/env.sh ]]; then source .cloud/env.sh; fi
python3 -m json.tool .devcontainer/devcontainer.json >/dev/null
git diff --check
bash ./gradlew --no-daemon --max-workers=1 :app:testDebugUnitTest :app:assembleDebug :app:lintDebug -PwalkpaintDeveloperBuild=true
python3 -m unittest discover -s tests -v
python3 -m unittest discover -s scripts -p "test_*.py" -v
