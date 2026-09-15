"""Reading and writing the CSV tables that sit between the raw downloads and the dataset."""

import csv
from pathlib import Path
from typing import Iterable, Mapping, Sequence


def write_table(path: Path, columns: Sequence[str], rows: Iterable[Mapping[str, object]]) -> int:
    """Writes rows sorted as given, one column per key in `columns`. Returns the row count."""
    path.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    with path.open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=columns, lineterminator="\n")
        writer.writeheader()
        for row in rows:
            writer.writerow({column: _cell(row.get(column)) for column in columns})
            count += 1
    return count


def read_table(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as file:
        return list(csv.DictReader(file))


def number(text: object) -> float | None:
    """A numeric cell, or None where the source has no value."""
    if text is None:
        return None
    stripped = str(text).strip()
    if stripped in ("", "-"):
        return None
    return float(stripped)


def _cell(value: object) -> str:
    if value is None:
        return ""
    if isinstance(value, float):
        # Shortest text that reads back as the same float, so a value is never rounded and the
        # CSV diff of a source update shows only values that actually changed.
        return repr(value)
    return str(value)
