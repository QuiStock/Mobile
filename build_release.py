"""Build the QuiStock Android release APK."""

import os
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent
APK = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"


def main() -> int:
    wrapper = "gradlew.bat" if os.name == "nt" else "gradlew"
    wrapper_path = ROOT / wrapper
    if not wrapper_path.is_file():
        print(f"Gradle Wrapper not found: {wrapper_path}", file=sys.stderr)
        return 2

    command = [str(wrapper_path), ":app:assembleRelease", "--console=verbose"]
    if os.name != "nt":
        command[0] = f"./{wrapper}"

    try:
        result = subprocess.run(command, cwd=ROOT, check=False)
    except OSError as error:
        print(f"Could not start Gradle Wrapper: {error}", file=sys.stderr)
        return 2

    if result.returncode:
        print(f"Release build failed with exit code {result.returncode}.", file=sys.stderr)
        return result.returncode
    if not APK.is_file():
        print(f"Release APK was not found at {APK}.", file=sys.stderr)
        return 2

    print(f"Release APK built: {APK}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
