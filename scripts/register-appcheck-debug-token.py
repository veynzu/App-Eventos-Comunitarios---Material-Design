"""Registra el dispositivo debug abierto sin imprimir ni guardar su secreto de App Check."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import urllib.error
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Identificador que muestra adb devices")
    parser.add_argument("--name", default="Share You - desarrollo")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") or r"C:\Android\sdk"
    adb = shutil.which("adb") or str(Path(sdk) / "platform-tools" / "adb.exe")
    gcloud = shutil.which("gcloud.cmd") or shutil.which("gcloud")
    if not gcloud:
        raise RuntimeError("Instala Google Cloud CLI e inicia sesion antes de registrar el dispositivo.")
    pid = subprocess.run([adb, "-s", args.serial, "shell", "pidof", "com.eventoscomunitarios.app"], capture_output=True, text=True, check=True).stdout.strip().split()[0]
    logs = subprocess.run([adb, "-s", args.serial, "logcat", "-d", "--pid=" + pid], capture_output=True, text=True, check=True).stdout
    found = re.findall(r"Enter this debug secret[^\n]*?:\s*([a-fA-F0-9-]{36})", logs)
    if not found:
        raise RuntimeError("No se encontro el secreto de App Check. Abre la nueva APK debug y vuelve a ejecutar este script.")
    debug_secret = found[-1]
    config = json.loads((root / "app/google-services.json").read_text(encoding="utf-8"))
    app_id = next(client for client in config["client"] if client["client_info"]["android_client_info"]["package_name"] == "com.eventoscomunitarios.app")["client_info"]["mobilesdk_app_id"]
    access_token = subprocess.run([gcloud, "auth", "print-access-token"], capture_output=True, text=True, check=True).stdout.strip()
    url = "https://firebaseappcheck.googleapis.com/v1/projects/381076241260/apps/" + app_id + "/debugTokens"
    body = json.dumps({"displayName": args.name[:100], "token": debug_secret}).encode()
    headers = {"Authorization": "Bearer " + access_token, "Content-Type": "application/json", "x-goog-user-project": "share-you-6a706"}
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=body, headers=headers), timeout=40) as response:
            result = json.load(response)
        print(json.dumps({"registered": True, "name": result.get("displayName"), "secretDisplayed": False}))
    except urllib.error.HTTPError as error:
        if error.code == 409:
            print(json.dumps({"registered": True, "alreadyRegistered": True, "secretDisplayed": False}))
        else:
            raise RuntimeError(f"No se pudo registrar el dispositivo en App Check (HTTP {error.code}).") from None


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # No incluir argumentos de comandos, credenciales ni el contenido de logcat.
        print(f"Registro de App Check: {error}")
        raise SystemExit(1)
