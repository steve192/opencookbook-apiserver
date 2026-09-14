"""Imports new BLS and FDC releases: raw/ -> sources/ -> src/main/resources/nutrition/

Put the downloads into raw/ (see SOURCES.md); the report goes to local/build-report.txt.
"""

import sys

import build_dataset
from cookpal_nutrition import extraction
from cookpal_nutrition.paths import MissingDownload


def main() -> int:
    try:
        for line in extraction.extract_all():
            print(line)
    except MissingDownload as missing:
        print(f"Sources not imported: {missing}", file=sys.stderr)
        return 1
    return build_dataset.main()


if __name__ == "__main__":
    sys.exit(main())
