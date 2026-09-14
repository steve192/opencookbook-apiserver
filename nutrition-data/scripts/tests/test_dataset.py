import pytest

from cookpal_nutrition import dataset
from cookpal_nutrition.extraction import Provenance


def _provenance(source: str, release: str) -> Provenance:
    return Provenance(source=source, release=release, file="download.zip", sha256="0")


def test_the_bls_is_cited_as_its_publisher_asks():
    bls = next(entry for entry in dataset.attributions([_provenance("BLS", "4.0 (2025)")]) if entry["source"] == "BLS")
    assert "Max Rubner-Institut (2025)" in bls["text"]
    assert "DOI: 10.25826/Data20251217-134202-0" in bls["text"]
    assert bls["license"] == "CC BY 4.0"


def test_a_release_without_citation_fails_the_build():
    with pytest.raises(ValueError, match="4.1"):
        dataset.attributions([_provenance("BLS", "4.1 (2027)")])
