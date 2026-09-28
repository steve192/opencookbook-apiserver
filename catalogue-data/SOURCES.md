# Sources

Downloads go into `raw/` under the name the publisher gives them; they are not in git. The import
(`scripts/import_sources.py`) uses the newest download of each source and records exactly which
files it used, with their sha256, in `sources/provenance.json` and in the shipped
`manifest.json`.

| Source | File name in `raw/` | Where to get it |
|---|---|---|
| Bundeslebensmittelschlüssel, Max Rubner-Institut | `BLS_<major>_<minor>_<year>_DE.zip` | https://blsdb.de, the German edition; the archive contains the data file, a component list and the reference manual |
| USDA FoodData Central, Foundation Foods (JSON) | `FoodData_Central_foundation_food_json_<date>.zip` | `https://fdc.nal.usda.gov/fdc-datasets/<file name>` |
| USDA FoodData Central, SR Legacy (JSON) | `FoodData_Central_sr_legacy_food_json_<date>.zip` | as above; SR Legacy's final release is 2018-04 |

The FDC server rejects requests without a browser user agent, e.g.
`curl -A "Mozilla/5.0" -O https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_foundation_food_json_2026-04-30.zip`.

## What the BLS code means

The reference manual (section 2.4) only defines the leading letter as the main food group. The
structure of the remaining positions is inherited from earlier BLS versions and holds in the 4.0
data for purely numeric codes (`K110132`): positions 1 to 5 identify a food and its processing,
position 6 its preparation (`0` unprepared, `3` boiled, `4` braised/steamed, `5` stewed, `6`
baked, `7` grilled, `8` pan-fried, `9` deep-fried). Codes with letters inside (`D7A7430`) were
added later and do not follow it, so their preparation is read from the names only. Because the
manual guarantees neither, the build reports every coded preparation the name does not mention.
