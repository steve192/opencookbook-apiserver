"""sources/ + curation/ -> src/main/resources/nutrition/ (after a curation change; new releases: import_sources.py).

The report goes to local/build-report.txt.
"""

import sys

from cookpal_nutrition import catalogue, curation, dataset, extraction, paths, reference
from cookpal_nutrition.curation import InvalidCuration
from cookpal_nutrition.tables import read_table


def main() -> int:
    try:
        references = reference.load(paths.CURATION)
        curated = curation.load(paths.CURATION)
        sources = catalogue.Sources(
            bls_foods=read_table(paths.BLS_FOODS),
            fdc_foods=read_table(paths.FDC_FOODS),
            fdc_portions=read_table(paths.FDC_PORTIONS),
        )
        foods, report = catalogue.build(references, curated, sources)
    except (InvalidCuration, reference.InvalidReference) as invalid:
        print(f"Dataset not built: {invalid}", file=sys.stderr)
        return 1

    checksum = dataset.write(paths.DATASET, curated.label, foods, references, extraction.read_provenance())
    _write_report(report)
    bases = sum(1 for food in foods if food.variant_of is None)
    print(f"{len(foods)} foods ({bases} bases) -> {paths.DATASET.relative_to(paths.REPOSITORY)}, checksum {checksum[:12]}")
    for topic, lines in sorted(report.lines.items()):
        print(f"  {topic}: {len(lines)}")
    print(f"Report: {paths.BUILD_REPORT.relative_to(paths.NUTRITION_DATA)}")
    return 0


def _write_report(report: catalogue.Report) -> None:
    paths.BUILD_REPORT.parent.mkdir(parents=True, exist_ok=True)
    with paths.BUILD_REPORT.open("w", encoding="utf-8") as file:
        for topic, lines in sorted(report.lines.items()):
            file.write(f"## {topic} ({len(lines)})\n")
            file.writelines(f"{line}\n" for line in lines)
            file.write("\n")


if __name__ == "__main__":
    sys.exit(main())
