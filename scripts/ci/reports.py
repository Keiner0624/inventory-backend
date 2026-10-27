"""Contrato de ejecucion y exportacion JUnit sin propiedades, logs, mensajes ni stacks."""
import argparse
import json
from pathlib import Path
import re
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SUITES = {"unit", "sqlserver"}
RACE = "com.lmmedical.inventory.integration.SecuritySqlServerTest#dosRenovacionesConcurrentesTienenUnSoloGanadorYRevocanLaFamilia()"


def locations(root, suite):
    if suite not in SUITES:
        raise ValueError("Suite incorrecta")
    return (root / "target/security-phase129" / suite,
            root / "target/ci-reports" / suite,
            root / "target/ci-private")


def prepare(root, suite):
    raw, public, private = locations(root, suite)
    for directory in (raw, public, private):
        if directory.is_symlink():
            raise ValueError("Carpeta de informes enlazada no permitida")
        directory.mkdir(parents=True, exist_ok=True)
    # Solo resultados generados de esta suite. No se borra target ni evidencias anteriores.
    for directory in (raw, public):
        for file in directory.glob("TEST-*.xml"):
            if file.is_symlink():
                raise ValueError("Informe enlazado no permitido")
            file.unlink()
    (public / "summary.json").unlink(missing_ok=True)
    (private / f"{suite}-exit-code.txt").unlink(missing_ok=True)
    (private / f"{suite}-state.json").write_text(json.dumps({"started_ns": time.time_ns()}), encoding="utf-8")


def identifier(value):
    if not value or not re.fullmatch(r"[\w.$(), \[\]-]{1,240}", value):
        raise ValueError("Identificador JUnit no valido")
    return value


def failure_metadata(element, classname):
    raw = (element.get("type", "") + "\n" + (element.text or ""))
    allowed = {"IllegalTransactionStateException", "AssertionFailedError", "DataIntegrityViolationException",
               "InvalidDataAccessApiUsageException", "SQLServerException", "ServletException",
               "UnexpectedRollbackException", "LazyInitializationException", "NullPointerException"}
    types = sorted(name for name in allowed if re.search(r"\b" + name + r"\b", raw))
    # Solo líneas de nuestras clases de prueba públicas; no nombres de datos ni argumentos.
    filename = classname.rsplit(".", 1)[-1].split("$", 1)[0] + ".java"
    locations = sorted(set(re.findall(r"\b(" + re.escape(filename) + r":[0-9]+)\)", raw)))
    return {"exception_types": types, "test_locations": locations}


def verify(root, suite, plan=None):
    raw, public, private = locations(root, suite)
    public.mkdir(parents=True, exist_ok=True)
    for old in public.glob("TEST-*.xml"):
        old.unlink()
    problems, cases, failed = [], set(), []
    failure_details = []
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    started = 0
    try:
        started = int(json.loads((private / f"{suite}-state.json").read_text(encoding="utf-8"))["started_ns"])
    except (OSError, ValueError, KeyError):
        problems.append("missing_run_state")
    try:
        maven_exit = int((private / f"{suite}-exit-code.txt").read_text(encoding="utf-8").strip())
    except (OSError, ValueError):
        maven_exit = None
    if maven_exit != 0:
        problems.append("maven_failed_or_not_executed")
    files = sorted(raw.glob("TEST-*.xml"))
    if not files:
        problems.append("no_junit_reports")
    for index, file in enumerate(files):
        try:
            if file.is_symlink() or file.stat().st_mtime_ns < started:
                raise ValueError("Informe no pertenece a esta ejecucion")
            document = ET.parse(file).getroot()
            if document.tag != "testsuite":
                raise ValueError("Formato JUnit inesperado")
            name = identifier(document.get("name"))
            counts = {field: int(document.get(field, "-1")) for field in totals}
            tests = document.findall("testcase")
            if any(n < 0 for n in counts.values()) or counts["tests"] != len(tests):
                raise ValueError("Conteos JUnit inconsistentes")
            measured = {"failures": 0, "errors": 0, "skipped": 0}
            safe = ET.Element("testsuite", {"name": name, **{key: str(value) for key, value in counts.items()}})
            for test in tests:
                classname, method = identifier(test.get("classname")), identifier(test.get("name"))
                key = classname + "#" + method
                if key in cases:
                    raise ValueError("Caso duplicado")
                cases.add(key)
                clean = ET.SubElement(safe, "testcase", {"classname": classname, "name": method})
                duration = test.get("time", "0")
                if re.fullmatch(r"[0-9]+(?:\.[0-9]+)?", duration):
                    clean.set("time", duration)
                for tag, count in (("failure", "failures"), ("error", "errors"), ("skipped", "skipped")):
                    for detail in test.findall(tag):
                        measured[count] += 1
                        ET.SubElement(clean, tag, {"message": "Consultar el caso localmente; detalles crudos no publicados"})
                        failed.append(key)
                        failure_details.append({"case": key, **failure_metadata(detail, classname)})
            if any(counts[key] != value for key, value in measured.items()):
                raise ValueError("Estados JUnit inconsistentes")
            for key, value in counts.items():
                totals[key] += value
            ET.ElementTree(safe).write(public / f"TEST-{index:03d}.xml", encoding="utf-8", xml_declaration=True)
        except (OSError, ValueError, ET.ParseError, TypeError):
            problems.append("invalid_or_stale_junit_report")
    try:
        if plan is None:
            plan = json.loads((root / "scripts/ci/test-plan.json").read_text(encoding="utf-8"))
        contract = plan[suite]
        required = set(contract["required_cases"])
        missing = required - cases
        if missing:
            problems.append("required_cases_missing")
        if totals["tests"] < int(contract["minimum_tests"]):
            problems.append("test_count_below_contract")
    except (OSError, ValueError, KeyError, TypeError):
        missing = set()
        problems.append("invalid_test_contract")
    if any(totals[key] for key in ("failures", "errors", "skipped")):
        problems.append("tests_failed_or_skipped")
    races = sum(RACE + f"[{i}]" in cases for i in (1, 2, 3))
    if suite == "sqlserver" and races != 3:
        problems.append("concurrency_cases_missing")
    summary = {"suite": suite, "passed": not problems, "maven_exit": maven_exit,
               **totals, "verified_concurrency_cases": races,
               "problems": sorted(set(problems)), "failed_cases": sorted(set(failed)),
               "missing_required_cases": sorted(missing), "failure_details": failure_details}
    (public / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    # La consola tampoco contiene mensajes de excepcion ni propiedades del proceso.
    print(json.dumps({key: value for key, value in summary.items() if key not in ("failed_cases", "missing_required_cases")}))
    return not problems


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("prepare", "verify"))
    parser.add_argument("suite", choices=sorted(SUITES))
    args = parser.parse_args()
    if args.command == "prepare":
        prepare(ROOT, args.suite)
    elif not verify(ROOT, args.suite):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
