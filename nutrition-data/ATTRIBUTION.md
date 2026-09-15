# Attribution

cookpal's nutrition dataset (`src/main/resources/nutrition/`) is derived from the sources below.
The same attribution is shipped inside the dataset (`manifest.json`) and shown in the app's nutrition
sheet and the admin catalogue screen.

## Bundeslebensmittelschlüssel (BLS) 4.0

> Nährwertdaten: Max Rubner-Institut (2025): Bundeslebensmittelschlüssel (BLS), Version 4.0 - Deutsche
> Nährstoffdatenbank. Karlsruhe. DOI: 10.25826/Data20251217-134202-0. Auszug; Werte vereinheitlicht,
> Bezeichnungen, Portionsgewichte und Dichten ergänzt. Lizenz: CC BY 4.0
> (https://creativecommons.org/licenses/by/4.0/).

Publisher: Max Rubner-Institut, Bundesforschungsinstitut für Ernährung und Lebensmittel.
License: Creative Commons Attribution 4.0 International (CC BY 4.0); the Max Rubner-Institut is to be named as
publisher. The citation is the one the publisher asks for.

Changes made by cookpal:

- **Excerpt:** only the components cookpal uses are extracted, and prepared dishes that are never used as an ingredient are left out.
- **Harmonisation:** energy is recomputed from the macronutrients with the EU conversion factors (Regulation (EU) No 1169/2011), matching values from other sources; saturated fat and sugar are capped at fat and carbohydrates.
- **Additions:** names, synonyms and translations, portion weights and densities are added. Added values are marked by origin in the dataset.

## USDA FoodData Central

> U.S. Department of Agriculture, Agricultural Research Service. FoodData Central, 2019.
> fdc.nal.usda.gov. Foundation Foods (release 2026-04-30) and SR Legacy (2018-04).

FoodData Central data is in the public domain (CC0 1.0). The citation above is given as USDA
requests.

Changes made by cookpal:

- **Excerpt:** only foods without an equivalent in the BLS are included: Foundation Foods, SR Legacy spices and herbs, and single SR Legacy foods listed in `curation/inclusion.yaml`. Restaurant food is left out. SR Legacy also contributes portion weights.
- **Portion weights:** also used for BLS foods that FDC duplicates.
- **Harmonisation to EU definitions:** available carbohydrates, protein as N × 6.25, salt from sodium, and energy recomputed with EU factors.
- **Additions:** German names are added.
