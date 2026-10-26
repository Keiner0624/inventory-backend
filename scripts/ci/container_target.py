"""Valida identidad y puerto del unico contenedor que puede usar CI. No imprime inspect."""
import json
import os
import re
import subprocess


def validate(data, name, marker):
    if not re.fullmatch(r"lmmedical-ci-[0-9]+-[0-9]+", name or ""):
        raise ValueError("Nombre de contenedor CI invalido")
    if not re.fullmatch(r"[0-9a-f]{48}", marker or ""):
        raise ValueError("Marca CI invalida")
    if len(data) != 1:
        raise ValueError("Contenedor ambiguo")
    item = data[0]
    if item.get("Name") != "/" + name or not item.get("State", {}).get("Running"):
        raise ValueError("Contenedor incorrecto o detenido")
    config = item.get("Config", {})
    if config.get("Hostname") != "lmmedical-ci" or config.get("Labels", {}).get("com.lmmedical.ci.marker") != marker:
        raise ValueError("Identidad del contenedor no verificada")
    host = item.get("HostConfig", {})
    if host.get("NetworkMode") == "host" or host.get("Binds") or item.get("Mounts"):
        raise ValueError("CI no admite red host ni almacenamiento persistente")
    ports = item.get("NetworkSettings", {}).get("Ports", {}).get("1433/tcp", [])
    if len(ports) != 1 or ports[0].get("HostIp") != "127.0.0.1":
        raise ValueError("SQL Server debe estar publicado solo en loopback")
    port = ports[0].get("HostPort", "")
    if not port.isdecimal() or not 1024 <= int(port) <= 65535 or int(port) == 1433:
        raise ValueError("Puerto CI incorrecto; 1433 local esta prohibido")
    return port


def main():
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted":
        raise ValueError("Este destino requiere un runner hospedado por GitHub")
    name = os.environ.get("CI_SQLSERVER_CONTAINER", "")
    # Validar el nombre antes de invocar Docker.
    if not re.fullmatch(r"lmmedical-ci-[0-9]+-[0-9]+", name):
        raise ValueError("Falta el contenedor CI")
    result = subprocess.run(["docker", "inspect", name], capture_output=True, check=True)
    port = validate(json.loads(result.stdout), name, os.environ.get("CI_SQLSERVER_MARKER", ""))
    configured = os.environ.get("CI_SQLSERVER_PORT")
    if configured is not None and configured != port:
        raise ValueError("El puerto configurado no pertenece al contenedor")
    print(port)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # inspect puede contener claves: nunca imprimir stdout/stderr ni la excepcion original.
        raise SystemExit("No se pudo verificar el contenedor aislado de CI")
