import contextlib
import io
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET


def load(name):
    path = Path(__file__).resolve().parents[1] / (name + ".py")
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


reports = load("reports")
container = load("container_target")


class ReportsTest(unittest.TestCase):
    def setUp(self):
        self.enterContext(contextlib.redirect_stdout(io.StringIO()))
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.key = "com.example.SecurityTest#verifica()"
        self.plan = {"unit": {"minimum_tests": 1, "required_cases": [self.key]}}
        reports.prepare(self.root, "unit")
        self.exit_code(0)

    def exit_code(self, value, suite="unit"):
        path = self.root / "target/ci-private" / (suite + "-exit-code.txt")
        path.write_text(str(value), encoding="utf-8")

    def report(self, keys=None, status=None, secret=None, suite="unit"):
        keys = keys if keys is not None else [self.key]
        root = ET.Element("testsuite", {"name": "com.example.SecurityTest", "tests": str(len(keys)),
                                     "failures": str(len(keys) if status == "failure" else 0),
                                     "errors": "0", "skipped": str(len(keys) if status == "skipped" else 0)})
        for key in keys:
            classname, method = key.split("#", 1)
            child = ET.SubElement(root, "testcase", {"classname": classname, "name": method})
            if status:
                ET.SubElement(child, status, {"message": secret or "raw-message"}).text = secret or "raw-stack"
        if secret:
            ET.SubElement(root, "properties").text = secret
            ET.SubElement(root, "system-out").text = secret
        path = self.root / "target/security-phase129" / suite / "TEST-fixture.xml"
        ET.ElementTree(root).write(path, encoding="utf-8")
        return path

    def test_success_requires_actual_cases(self):
        self.report()
        self.assertTrue(reports.verify(self.root, "unit", self.plan))

    def test_missing_xml_is_failure(self):
        self.assertFalse(reports.verify(self.root, "unit", self.plan))

    def test_maven_error_is_failure_even_with_green_xml(self):
        self.report()
        self.exit_code(1)
        self.assertFalse(reports.verify(self.root, "unit", self.plan))

    def test_skipped_is_failure(self):
        self.report(status="skipped")
        self.assertFalse(reports.verify(self.root, "unit", self.plan))

    def test_test_failure_is_failure_and_secrets_are_removed(self):
        secret = "sensitive-fixture-should-never-be-published"
        self.report(status="failure", secret=secret)
        self.assertFalse(reports.verify(self.root, "unit", self.plan))
        for file in (self.root / "target/ci-reports/unit").iterdir():
            self.assertNotIn(secret, file.read_text(encoding="utf-8"))

    def test_successful_reports_also_strip_logs_and_properties(self):
        secret = "sensitive-fixture-should-never-be-published"
        self.report(secret=secret)
        self.assertTrue(reports.verify(self.root, "unit", self.plan))
        output = (self.root / "target/ci-reports/unit/TEST-000.xml").read_text(encoding="utf-8")
        self.assertNotIn(secret, output)
        self.assertNotIn("properties", output)
        self.assertNotIn("system-out", output)

    def test_untrusted_public_xml_is_removed_before_export(self):
        self.report()
        unexpected = self.root / "target/ci-reports/unit/TEST-unexpected.xml"
        unexpected.write_text("sensitive fixture", encoding="utf-8")
        self.assertTrue(reports.verify(self.root, "unit", self.plan))
        self.assertFalse(unexpected.exists())

    def test_same_count_cannot_replace_a_required_test(self):
        self.report(["com.example.SecurityTest#otro()"])
        self.assertFalse(reports.verify(self.root, "unit", self.plan))

    def test_stale_reports_are_rejected(self):
        self.report()
        state = self.root / "target/ci-private/unit-state.json"
        state.write_text(json.dumps({"started_ns": 2**63-1}), encoding="utf-8")
        self.assertFalse(reports.verify(self.root, "unit", self.plan))

    def test_duplicate_test_cannot_inflate_count(self):
        self.report([self.key, self.key])
        self.assertFalse(reports.verify(self.root, "unit", self.plan))

    def test_three_actual_concurrency_cases_are_mandatory(self):
        reports.prepare(self.root, "sqlserver")
        self.exit_code(0, "sqlserver")
        keys = [reports.RACE + f"[{i}]" for i in (1, 2, 3)]
        plan = {"sqlserver": {"minimum_tests": 3, "required_cases": keys}}
        self.report(keys, suite="sqlserver")
        self.assertTrue(reports.verify(self.root, "sqlserver", plan))
        self.report(keys[:2] + ["com.example.SecurityTest#relleno()"], suite="sqlserver")
        self.assertFalse(reports.verify(self.root, "sqlserver", plan))


class ContainerTargetTest(unittest.TestCase):
    def setUp(self):
        self.name = "lmmedical-ci-100-1"
        self.marker = "a" * 48
        self.data = [{"Name": "/"+self.name, "State": {"Running": True},
                      "Config": {"Hostname": "lmmedical-ci", "Labels": {"com.lmmedical.ci.marker": self.marker}},
                      "HostConfig": {"NetworkMode": "bridge", "Binds": None}, "Mounts": [],
                      "NetworkSettings": {"Ports": {"1433/tcp": [{"HostIp": "127.0.0.1", "HostPort": "32001"}]}}}]

    def test_accepts_exact_isolated_container(self):
        self.assertEqual("32001", container.validate(self.data, self.name, self.marker))

    def test_refuses_developer_port(self):
        self.data[0]["NetworkSettings"]["Ports"]["1433/tcp"][0]["HostPort"] = "1433"
        with self.assertRaises(ValueError):
            container.validate(self.data, self.name, self.marker)

    def test_refuses_non_loopback(self):
        self.data[0]["NetworkSettings"]["Ports"]["1433/tcp"][0]["HostIp"] = "0.0.0.0"
        with self.assertRaises(ValueError):
            container.validate(self.data, self.name, self.marker)

    def test_refuses_host_network_and_persistent_storage(self):
        for change in ("host", "mount"):
            data = copy.deepcopy(self.data)
            if change == "host":
                data[0]["HostConfig"]["NetworkMode"] = "host"
            else:
                data[0]["Mounts"] = [{"Type": "bind"}]
            with self.assertRaises(ValueError):
                container.validate(data, self.name, self.marker)

    def test_refuses_a_different_run(self):
        with self.assertRaises(ValueError):
            container.validate(self.data, self.name, "b" * 48)


if __name__ == "__main__":
    unittest.main()
