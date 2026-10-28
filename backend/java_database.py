from pathlib import Path

import jpype


PROJECT_ROOT = Path(__file__).resolve().parents[1]
JAVA_CLASSES = PROJECT_ROOT / "target" / "classes"
JAVA_DEPENDENCIES = PROJECT_ROOT / "target" / "dependency"
DATABASE_FILE = PROJECT_ROOT / "bank.db"


def initialize_java_database() -> None:
    # Initialize the root-level bank.db using the Java DatabaseManager.
    if not (JAVA_CLASSES / "database" / "DatabaseManager.class").is_file():
        raise RuntimeError(
            "Java classes are missing. From the project root, run "
            "'mvn compile dependency:copy-dependencies "
            "-DoutputDirectory=target/dependency'."
        )

    jars = sorted(JAVA_DEPENDENCIES.glob("*.jar"))
    if not any(jar.name.startswith("sqlite-jdbc-") for jar in jars):
        raise RuntimeError(
            "The SQLite JDBC dependency is missing. From the project root, run "
            "'mvn compile dependency:copy-dependencies "
            "-DoutputDirectory=target/dependency'."
        )

    classpath = [str(JAVA_CLASSES), *(str(jar) for jar in jars)]
    if jpype.isJVMStarted():
        for entry in classpath:
            jpype.addClassPath(entry)
    else:
        jpype.startJVM(classpath=classpath)

    jpype.java.lang.System.setProperty("bank.db.path", str(DATABASE_FILE))
    database_manager = jpype.JClass("database.DatabaseManager")
    database_manager.initializeDatabase()
    database_manager.registerUser("john_doe", "John", "Doe", "john@example.com", "1234567890", "fake_hashed_password_123")
    database_manager.getUserByUsername("john_doe")
