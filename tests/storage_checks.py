"""Host SQLite schema/transaction checks. Does not simulate Android lifecycle or UI."""
from pathlib import Path
import re
import sqlite3

source = (Path(__file__).resolve().parents[1] / "app/src/main/java/id/local/mufchecker/QueueStore.java").read_text(encoding="utf-8-sig")
statements = re.findall(r'db\.execSQL\("([^"]+)"\)', source)
ddl = {re.search(r"CREATE TABLE (\w+)", sql).group(1): sql for sql in statements if sql.startswith("CREATE TABLE")}
checks = 0


def check(condition, message):
    global checks
    checks += 1
    assert condition, message


db = sqlite3.connect(":memory:", isolation_level=None)
db.execute(ddl["meta"])
db.execute("CREATE TABLE diagnostics (contract TEXT PRIMARY KEY, report TEXT NOT NULL)")
db.execute("INSERT INTO meta VALUES ('batch','legacy-snapshot')")
db.execute("INSERT INTO diagnostics VALUES ('090000000001','legacy-report')")
db.execute("BEGIN")
try:
    db.execute(ddl["sessions"])
    db.execute(ddl["reports"])
    db.execute("INSERT INTO sessions VALUES ('legacy-v040',0,'legacy-snapshot')")
    db.execute("INSERT INTO reports VALUES ('legacy-v040','090000000001','legacy-report')")
    db.execute("INSERT INTO reports VALUES ('bad','090000000002',NULL)")
except sqlite3.IntegrityError:
    db.execute("ROLLBACK")
check(db.execute("SELECT report FROM diagnostics").fetchone()[0] == "legacy-report", "Failed migration preserves old report")
check(db.execute("SELECT value FROM meta WHERE key='batch'").fetchone()[0] == "legacy-snapshot", "Failed migration preserves current snapshot")
check(not db.execute("SELECT name FROM sqlite_master WHERE name='sessions'").fetchall(), "Failed migration rolls back schema as well as rows")

db.execute("BEGIN")
db.execute(ddl["sessions"])
db.execute(ddl["reports"])
db.execute("INSERT INTO sessions VALUES ('legacy-v040',0,'legacy-snapshot')")
db.execute("INSERT INTO reports SELECT 'legacy-v040',contract,report FROM diagnostics")
db.execute("DROP TABLE diagnostics")
db.execute("COMMIT")
check(db.execute("SELECT report FROM reports WHERE session_id='legacy-v040'").fetchone()[0] == "legacy-report", "Successful migration retains legacy diagnostic")
check(not db.execute("SELECT name FROM sqlite_master WHERE name='diagnostics'").fetchall(), "Old table is removed only after transfer")

db.execute("INSERT INTO sessions VALUES ('session-new',1000,'new-snapshot')")
db.execute("INSERT INTO reports VALUES ('session-new','090000000001','new-report')")
check(db.execute("SELECT COUNT(*) FROM reports WHERE contract='090000000001'").fetchone()[0] == 2, "Same contract can have different reports in separate sessions")
check(db.execute("SELECT report FROM reports WHERE session_id=? AND contract=?", ("legacy-v040", "090000000001")).fetchone()[0] == "legacy-report", "Reading old session cannot return new session report")
check(db.execute("SELECT id FROM sessions ORDER BY created DESC,rowid DESC").fetchone()[0] == "session-new", "Newest session is selected first")

db.execute("BEGIN")
try:
    db.execute("INSERT OR REPLACE INTO meta VALUES ('batch','uncommitted')")
    db.execute("INSERT OR REPLACE INTO sessions VALUES ('session-new',1000,'uncommitted')")
    db.execute("INSERT OR REPLACE INTO reports VALUES ('session-new','090000000002',NULL)")
except sqlite3.IntegrityError:
    db.execute("ROLLBACK")
check(db.execute("SELECT value FROM meta WHERE key='batch'").fetchone()[0] == "legacy-snapshot", "Failed report write rolls back queue checkpoint")
check(db.execute("SELECT snapshot FROM sessions WHERE id='session-new'").fetchone()[0] == "new-snapshot", "Failed checkpoint does not partially overwrite session")

db.execute("BEGIN")
db.execute("DELETE FROM reports WHERE session_id=?", ("legacy-v040",))
db.execute("DELETE FROM sessions WHERE id=?", ("legacy-v040",))
db.execute("COMMIT")
check(db.execute("SELECT report FROM reports WHERE session_id='session-new'").fetchone()[0] == "new-report", "Deleting one session preserves another session with the same contract")
check(db.execute("SELECT COUNT(*) FROM reports WHERE session_id='legacy-v040'").fetchone()[0] == 0, "Selected session's report is removed")
check(db.execute("SELECT COUNT(*) FROM sessions").fetchone()[0] == 1, "Only the selected session is removed")
legacy = (Path(__file__).parent / "fixtures/v070-ready-50.properties").read_text(encoding="utf-8")
legacy100 = (Path(__file__).parent / "fixtures/v071-ready-100.properties").read_text(encoding="utf-8")
db.execute("INSERT INTO sessions VALUES ('synthetic-v070-ready-50',2000,?)", (legacy,))
db.execute("INSERT INTO reports VALUES ('synthetic-v070-ready-50','090000000001','old-masked-report')")
db.execute("INSERT INTO sessions VALUES ('synthetic-v071-ready-100',2500,?)", (legacy100,))
db.execute("INSERT INTO reports VALUES ('synthetic-v071-ready-100','090000000001','old-v071-report')")
for index in range(1, 101):
    number = f"09000000{index:04d}"
    db.execute("BEGIN")
    db.execute("INSERT OR REPLACE INTO sessions VALUES ('new-100',3000,?)", (f"checkpoint-{index}",))
    db.execute("INSERT OR REPLACE INTO meta VALUES ('batch',?)", (f"checkpoint-{index}",))
    db.execute("INSERT INTO reports VALUES ('new-100',?,?)", (number, f"verified-{index}"))
    db.execute("COMMIT")
check(db.execute("SELECT COUNT(*) FROM reports WHERE session_id='new-100'").fetchone()[0] == 100, "100 sequential result transactions retain each report")
check(db.execute("SELECT snapshot FROM sessions WHERE id='synthetic-v070-ready-50'").fetchone()[0] == legacy, "A new 100-contract session leaves the old 0.7.0 snapshot intact")
check(db.execute("SELECT report FROM reports WHERE session_id='synthetic-v070-ready-50'").fetchone()[0] == "old-masked-report", "Repeated contracts in a new 100-row session do not overwrite old reports")
check(db.execute("SELECT value FROM meta WHERE key='batch'").fetchone()[0] == "checkpoint-100", "Latest checkpoint tracks the final committed result")
check(db.execute("SELECT snapshot FROM sessions WHERE id='synthetic-v071-ready-100'").fetchone()[0] == legacy100, "New transactions leave the original 0.7.1 100-row session intact")
check(db.execute("SELECT report FROM reports WHERE session_id='synthetic-v071-ready-100'").fetchone()[0] == "old-v071-report", "Old 0.7.1 report is isolated from another session with the same contract")
print(f"PASS: {checks} host SQLite schema/transaction checks")
