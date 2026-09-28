"""The Bundeslebensmittelschlüssel (BLS) of the Max Rubner-Institut.

Columns: code, German name, English name, then value, origin and reference per component. Values are extracted
as published, including no-number markers, which `value` interprets.
"""

import io
import re
import zipfile
from pathlib import Path
from typing import Iterator

import openpyxl

# The data file inside the archive, e.g. BLS_4_0_Daten_2025_DE.xlsx next to documentation and a component list.
DATA_FILE = re.compile(r"(^|/)BLS_[^/]*_Daten_[^/]*\.xlsx$")
# The release in the archive name, e.g. BLS_4_0_2025_DE.zip.
ARCHIVE_RELEASE = re.compile(r"BLS_(\d+)_(\d+)_(\d{4})_")

# Components cookpal needs: the nine it shows, plus what EU energy recomputation needs.
COMPONENTS = ("ENERCC", "ENERCJ", "PROT625", "FAT", "FASAT", "CHO", "SUGAR", "FIBT", "NACL", "ALC",
              "POLYL", "OA", "OLSAC")

IDENTIFYING_COLUMNS = ("code", "name_de", "name_en")
COLUMNS = IDENTIFYING_COLUMNS + COMPONENTS

NOT_DETERMINED = "-"
# Present, but too little to measure: below the limit of detection or quantification, or a trace.
NEGLIGIBLE = frozenset({"<LOD", "<LOQ", "<LOD or <LOQ", "TR"})


def release(archive: Path) -> str:
    """The release an archive holds, as the BLS names it: "4.0 (2025)"."""
    match = ARCHIVE_RELEASE.search(archive.name)
    return f"{match.group(1)}.{match.group(2)} ({match.group(3)})" if match else archive.stem


def value(cell: str) -> float | None:
    """A component value per 100 g as extracted: a number, 0 for a negligible amount, None if unknown."""
    text = cell.strip()
    if text in ("", NOT_DETERMINED):
        return None
    if text in NEGLIGIBLE:
        return 0.0
    return float(text)


def read_foods(archive: Path) -> Iterator[dict[str, object]]:
    """Every food of the archive's data file, in file order."""
    rows = _data_sheet_rows(archive)
    header = next(rows)
    value_columns = _value_columns(header)
    for row in rows:
        code = row[0]
        if not code:
            continue
        food: dict[str, object] = {"code": code, "name_de": row[1], "name_en": row[2]}
        for component, column in value_columns.items():
            food[component] = _published(row[column])
        yield food


def _published(cell: object) -> object:
    """Numbers as floats, markers as their text."""
    if isinstance(cell, (int, float)):
        return float(cell)
    return cell


def _data_sheet_rows(archive: Path) -> Iterator[tuple]:
    with zipfile.ZipFile(archive) as zipped:
        names = [name for name in zipped.namelist() if DATA_FILE.search(name)]
        if len(names) != 1:
            raise ValueError(f"expected one BLS data file in {archive}, found {names}")
        workbook = openpyxl.load_workbook(io.BytesIO(zipped.read(names[0])), read_only=True)
    return workbook.active.iter_rows(values_only=True)


def _value_columns(header: tuple) -> dict[str, int]:
    """Where each component's value column is. A value column's title is "CODE Name [unit/100g]"."""
    columns = {}
    for index, title in enumerate(header):
        if not title or "[" not in title:
            continue
        code = title.split(" ", 1)[0]
        if code in COMPONENTS:
            columns[code] = index
    missing = set(COMPONENTS) - columns.keys()
    if missing:
        raise ValueError(f"BLS data file lacks value columns for {sorted(missing)}")
    return columns
