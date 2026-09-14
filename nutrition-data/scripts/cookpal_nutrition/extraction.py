"""Raw downloads -> source extracts, plus a record of which downloads they were made from."""

import hashlib
import json
from dataclasses import asdict, dataclass
from pathlib import Path

from cookpal_nutrition import paths
from cookpal_nutrition.sources import bls, fdc
from cookpal_nutrition.tables import write_table


@dataclass(frozen=True)
class Provenance:
    source: str
    release: str
    file: str
    sha256: str

    @staticmethod
    def of(source: str, release: str, archive: Path) -> "Provenance":
        return Provenance(source=source, release=release, file=archive.name, sha256=_sha256(archive))


def extract_all() -> list[str]:
    """Extracts every source from the newest downloads in raw/ and records them. Returns what was done."""
    bls_archive = paths.newest_raw(paths.BLS_ARCHIVE_PATTERN)
    fdc_archives = [paths.newest_raw(paths.FDC_FOUNDATION_PATTERN), paths.newest_raw(paths.FDC_SR_LEGACY_PATTERN)]

    bls_count = write_table(paths.BLS_FOODS, bls.COLUMNS, sorted(bls.read_foods(bls_archive), key=lambda food: food["code"]))
    fdc_foods = sorted(fdc.read_foods(fdc_archives), key=lambda food: food["fdcId"])
    fdc_count = write_table(paths.FDC_FOODS, fdc.FOOD_COLUMNS, (fdc.food_row(food) for food in fdc_foods))
    portion_count = write_table(paths.FDC_PORTIONS, fdc.PORTION_COLUMNS,
                                (portion for food in fdc_foods for portion in fdc.portion_rows(food)))

    provenance = [Provenance.of("BLS", bls.release(bls_archive), bls_archive)]
    provenance += [Provenance.of("FDC", fdc.release(archive), archive) for archive in fdc_archives]
    paths.PROVENANCE.write_text(json.dumps([asdict(entry) for entry in provenance], indent=1) + "\n", encoding="utf-8")

    return [f"{bls_count} BLS foods from {bls_archive.name}",
            f"{fdc_count} FDC foods and {portion_count} portions from {', '.join(archive.name for archive in fdc_archives)}"]


def read_provenance() -> list[Provenance]:
    return [Provenance(**entry) for entry in json.loads(paths.PROVENANCE.read_text(encoding="utf-8"))]


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()
