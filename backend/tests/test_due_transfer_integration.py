import os
from pathlib import Path
import shutil
import subprocess

import pytest


def test_due_transfer_settlement_in_sqlite(tmp_path):
    root = Path(__file__).resolve().parents[2]
    classes = root / "target" / "classes"
    dependencies = root / "target" / "dependency"
    java = shutil.which("java")
    if not java or not (classes / "database" / "TransactionDAO.class").exists() or not list(dependencies.glob("sqlite-jdbc-*.jar")):
        pytest.skip("Run the Maven compile and dependency copy before the Java integration test.")
    result = subprocess.run(
        [
            java, "-cp", os.pathsep.join((str(classes), str(dependencies / "*"))),
            str(Path(__file__).with_name("DueTransferIntegration.java")),
            str(tmp_path / "bank.db"),
        ],
        cwd=root,
        capture_output=True,
        text=True,
        timeout=30,
    )
    assert result.returncode == 0, result.stdout + result.stderr
