import CallMergeIcon from '@mui/icons-material/CallMerge';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import TranslateIcon from '@mui/icons-material/Translate';
import {Accordion, AccordionDetails, AccordionSummary, Box, Button, Chip, Dialog, DialogActions,
  DialogContent, DialogTitle, MenuItem, Paper, Stack, TextField, Typography} from '@mui/material';
import {useCallback, useMemo, useState} from 'react';
import {toast} from 'react-toastify';
import {CatalogueApi, CatalogueFood, CatalogueFoodSummary, CustomFoodDraft, errorMessage, NutritionDataset,
  Nutrients} from '../api';
import {CollectionScreen} from '../components/collection/CollectionScreen';
import {FieldDefinition, RowAction} from '../components/collection/types';
import {EntityFormDialog} from '../components/form/EntityFormDialog';
import {FormFieldDefinition, PairListEntry} from '../components/form/types';
import {NutritionTurnedOff} from '../components/NutritionTurnedOff';
import {StatTiles} from '../components/StatTiles';
import {useActionRunner} from '../hooks/useActionRunner';
import {useAsyncData, useCollection} from '../hooks/useAsyncData';

const LANGUAGES = ['de', 'en'];

const NO_DATASET: NutritionDataset = {label: '', checksum: '', foods: 0, attributions: [], sources: [], imports: []};

const NUTRIENTS: {key: keyof Nutrients, label: string}[] = [
  {key: 'energyKcal', label: 'Energy (kcal)'},
  {key: 'energyKj', label: 'Energy (kJ)'},
  {key: 'fat', label: 'Fat (g)'},
  {key: 'saturatedFat', label: 'Saturated fat (g)'},
  {key: 'carbohydrates', label: 'Carbohydrates (g)'},
  {key: 'sugar', label: 'Sugar (g)'},
  {key: 'fibre', label: 'Fibre (g)'},
  {key: 'protein', label: 'Protein (g)'},
  {key: 'salt', label: 'Salt (g)'},
];

const LEGACY_KEY_PREFIX = 'legacy-';
const isLegacy = (food: CatalogueFoodSummary) => food.catalogueKey.startsWith(LEGACY_KEY_PREFIX);

const originLabel = (food: CatalogueFoodSummary) => {
  if (food.retired) {
    return 'Retired';
  }
  if (isLegacy(food)) {
    return 'Legacy';
  }
  return food.origin === 'CUSTOM' ? 'Custom' : food.sourceType ?? 'Dataset';
};

const ORIGIN_COLORS: Record<string, 'warning' | 'secondary' | 'primary' | 'default'> = {
  Retired: 'warning',
  Legacy: 'secondary',
  Custom: 'primary',
};

const fields: FieldDefinition<CatalogueFoodSummary>[] = [
  {key: 'displayNameDe', label: 'Name (de)', width: 240},
  {key: 'displayNameEn', label: 'Name (en)', width: 240, importance: 'secondary'},
  {
    key: 'origin',
    label: 'Origin',
    width: 150,
    // Searching for "legacy", "custom" or "retired" filters the list by origin.
    value: originLabel,
    render: (food) => <Chip size="small" color={ORIGIN_COLORS[originLabel(food)] ?? 'default'} label={originLabel(food)} />,
  },
  {key: 'energyKcal', label: 'Energy (kcal)', kind: 'number', width: 130},
  {key: 'variantOfKey', label: 'Variant of', width: 150, importance: 'secondary'},
  {key: 'nameCount', label: 'Names', kind: 'number', width: 100, importance: 'secondary'},
  {key: 'portionCount', label: 'Portions', kind: 'number', width: 100, importance: 'secondary'},
  {key: 'catalogueKey', label: 'Key', importance: 'reference'},
  {key: 'sourceName', label: 'Source name', importance: 'reference'},
  {key: 'id', label: 'Id', kind: 'number', importance: 'reference'},
];

interface CustomFoodForm extends Record<keyof Nutrients, number | null> {
  names: PairListEntry[];
  portions: PairListEntry[];
  densityGPerMl: number | null;
  negligible: boolean;
}

const foodFormFields: FormFieldDefinition<CustomFoodForm>[] = [
  {
    name: 'names',
    label: 'Names - the first per language is shown',
    type: 'pairList',
    pairKeys: ['languageIsoCode', 'name'],
    pairLabels: ['Language', 'Name'],
    required: true,
  },
  ...NUTRIENTS.map((nutrient) => ({
    name: nutrient.key,
    label: nutrient.label,
    type: 'number' as const,
    section: 'Per 100 g',
  })),
  {
    name: 'portions',
    label: 'Portions',
    type: 'pairList',
    pairKeys: ['unitKey', 'grams'],
    pairLabels: ['Unit', 'Grams'],
    section: 'Amounts',
    helperText: 'A count unit such as piece, slice or clove',
  },
  {name: 'densityGPerMl', label: 'Density (g per ml)', type: 'number', section: 'Amounts'},
  {name: 'negligible', label: 'Contributes next to nothing (water, salt)', type: 'switch', section: 'Amounts'},
];

const EMPTY_FOOD_FORM: CustomFoodForm = {
  names: [{languageIsoCode: 'de', name: ''}, {languageIsoCode: 'en', name: ''}],
  portions: [],
  densityGPerMl: null,
  negligible: false,
  ...NUTRIENTS.reduce((nutrients, {key}) => ({...nutrients, [key]: null}), {} as Nutrients),
};

const toForm = (food: CatalogueFood): CustomFoodForm => ({
  names: food.names.map((name) => ({languageIsoCode: name.languageIsoCode, name: name.name})),
  portions: food.portions.map((portion) => ({unitKey: portion.unitKey, grams: portion.grams})),
  densityGPerMl: food.densityGPerMl,
  negligible: food.negligible,
  ...food.nutrients,
});

const toDraft = (form: CustomFoodForm): CustomFoodDraft => {
  const shownLanguages = new Set<string>();
  return {
    names: form.names
        .map((entry) => ({languageIsoCode: String(entry.languageIsoCode).trim(), name: String(entry.name).trim()}))
        .filter((entry) => entry.name.length > 0)
        .map((entry) => {
          const display = !shownLanguages.has(entry.languageIsoCode);
          shownLanguages.add(entry.languageIsoCode);
          return {...entry, display};
        }),
    nutrients: NUTRIENTS.reduce((nutrients, {key}) => ({...nutrients, [key]: form[key]}), {} as Nutrients),
    densityGPerMl: form.densityGPerMl,
    negligible: form.negligible,
    portions: form.portions
        .filter((entry) => String(entry.unitKey).trim().length > 0)
        .map((entry) => ({unitKey: String(entry.unitKey).trim(), grams: Number(entry.grams)})),
  };
};

interface MergeForm {
  targetId: number | null;
}

const mergeFormFields: FormFieldDefinition<MergeForm>[] = [{
  name: 'targetId',
  label: 'Id of the food to merge into',
  type: 'number',
  required: true,
  helperText: 'Linked ingredients and names move there; this food is deleted',
}];

export const CatalogueScreen = () => {
  const foods = useCollection(CatalogueApi.getFoods);
  const dataset = useAsyncData(CatalogueApi.dataset, NO_DATASET);
  const reload = useCallback(() => {
    foods.reload();
    dataset.reload();
  }, [foods.reload, dataset.reload]);
  const runner = useActionRunner(reload);

  const [editing, setEditing] = useState<CatalogueFood>();
  const [adding, setAdding] = useState(false);
  const [naming, setNaming] = useState<CatalogueFood>();
  const [merging, setMerging] = useState<CatalogueFoodSummary>();

  // The list carries summaries; editing and naming need the whole food.
  const openWithFood = useCallback((open: (food: CatalogueFood) => void) =>
    async (summary: CatalogueFoodSummary) => {
      try {
        open(await CatalogueApi.getFood(summary.id));
      } catch (cause) {
        toast.error('Loading ' + summary.catalogueKey + ' failed: ' +
          errorMessage(cause));
      }
    }, []);

  const stats = useMemo(() => {
    const latest = dataset.data.imports[0];
    return [
      {label: 'Foods', value: foods.data.length},
      {label: 'Custom', value: foods.data.filter((food) => food.origin === 'CUSTOM').length},
      {label: 'Legacy, to merge or keep', value: foods.data.filter(isLegacy).length},
      {label: 'Retired', value: foods.data.filter((food) => food.retired).length},
      {label: 'Dataset ' + dataset.data.label, value: latest?.status ?? 'Not imported',
        color: latest?.status === 'FAILED' ? 'error.main' : undefined},
    ];
  }, [foods.data, dataset.data]);

  const save = async (form: CustomFoodForm) => {
    const draft = toDraft(form);
    if (editing) {
      if (await runner.run('Saved', () => CatalogueApi.update(editing.id, draft))) {
        setEditing(undefined);
      }
    } else if (await runner.run('Added', () => CatalogueApi.create(draft))) {
      setAdding(false);
    }
  };

  const merge = async (form: MergeForm) => {
    if (merging && form.targetId !== null &&
      await runner.run('Merged ' + merging.catalogueKey, () => CatalogueApi.merge(merging.id, Number(form.targetId)))) {
      setMerging(undefined);
    }
  };

  // Memoised: the table rebuilds every column when these change identity.
  const rowActions = useMemo<RowAction<CatalogueFoodSummary>[]>(() => {
    const isDataset = (food: CatalogueFoodSummary) => food.origin !== 'CUSTOM';
    return [
      {label: 'Names', icon: <TranslateIcon fontSize="small" />, onRun: openWithFood(setNaming)},
      {label: 'Edit', icon: <EditIcon fontSize="small" />, hidden: isDataset, onRun: openWithFood(setEditing)},
      {label: 'Merge into', icon: <CallMergeIcon fontSize="small" />, hidden: isDataset, onRun: setMerging},
      {
        label: 'Delete',
        icon: <DeleteIcon fontSize="small" />,
        color: 'error',
        hidden: isDataset,
        confirm: (food) => 'Delete ' + (food.displayNameDe ?? food.catalogueKey) +
          '? Only possible while no ingredient links to it; merge it otherwise.',
        onRun: (food) => runner.run('Deleted ' + food.catalogueKey, () => CatalogueApi.delete(food.id)),
      },
    ];
  }, [runner, openWithFood]);

  if (foods.errorStatus === 404) {
    return <NutritionTurnedOff />;
  }

  return (
    <>
      <CollectionScreen
        title="Nutrition catalogue"
        fields={fields}
        data={foods}
        getRowId={(food) => food.id}
        detailsTitle={(food) => food.displayNameDe ?? food.catalogueKey}
        emptyMessage="The catalogue is empty until the shipped dataset has been imported"
        header={
          <>
            <StatTiles stats={stats} />
            <DatasetPanel dataset={dataset.data} />
          </>
        }
        onCreate={() => setAdding(true)}
        createLabel="Add custom food"
        rowActions={rowActions}
      />

      <EntityFormDialog<CustomFoodForm>
        open={adding || !!editing}
        title={editing ? 'Edit ' + editing.catalogueKey : 'Add a custom food'}
        fields={foodFormFields}
        initialValues={editing ? toForm(editing) : EMPTY_FOOD_FORM}
        submitLabel={editing ? 'Save' : 'Add'}
        onClose={() => {
          setEditing(undefined);
          setAdding(false);
        }}
        onSubmit={save}
      />

      <EntityFormDialog<MergeForm>
        open={!!merging}
        title={'Merge ' + (merging?.displayNameDe ?? merging?.catalogueKey ?? '')}
        fields={mergeFormFields}
        initialValues={{targetId: null}}
        submitLabel="Merge"
        onClose={() => setMerging(undefined)}
        onSubmit={merge}
      />

      {naming && (
        <NamesDialog
          food={naming}
          onClose={() => setNaming(undefined)}
          onAdd={(language, name) => runner.run('Added ' + name,
              async () => setNaming(await CatalogueApi.addName(naming.id, language, name)))}
          onRemove={(language, name) => runner.run('Removed ' + name,
              async () => setNaming(await CatalogueApi.removeName(naming.id, language, name)))}
        />
      )}
    </>
  );
};

// Attributions are required by the source licenses.
const DatasetPanel = (props: {dataset: NutritionDataset}) => {
  const latest = props.dataset.imports[0];
  return (
    <Paper variant="outlined" sx={{p: 2, mb: 2}}>
      <Stack spacing={1}>
        {props.dataset.attributions.map((attribution) => (
          <Typography key={attribution.source} variant="body2" color="text.secondary">
            {attribution.text} (<a href={attribution.licenseUrl} target="_blank" rel="noreferrer">
              {attribution.license}</a>)
          </Typography>
        ))}
        {latest?.report && (
          <Accordion disableGutters variant="outlined">
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
              Report of the import of {latest.label}
            </AccordionSummary>
            <AccordionDetails>
              <pre style={{margin: 0, overflowX: 'auto', whiteSpace: 'pre-wrap'}}>{latest.report}</pre>
            </AccordionDetails>
          </Accordion>
        )}
      </Stack>
    </Paper>
  );
};

// Only admin-added names can be removed.
const NamesDialog = (props: {
  food: CatalogueFood,
  onClose: () => void,
  onAdd: (language: string, name: string) => Promise<boolean>,
  onRemove: (language: string, name: string) => Promise<boolean>,
}) => {
  const [language, setLanguage] = useState(LANGUAGES[0]);
  const [name, setName] = useState('');

  const add = async () => {
    if (name.trim() && await props.onAdd(language, name.trim())) {
      setName('');
    }
  };

  return (
    <Dialog open onClose={props.onClose} fullWidth maxWidth="sm">
      <DialogTitle>Names of {props.food.sourceName ?? props.food.catalogueKey}</DialogTitle>
      <DialogContent dividers>
        <Box sx={{display: 'flex', flexWrap: 'wrap', gap: 1, mb: 2}}>
          {props.food.names.map((existing) => (
            <Chip
              key={existing.languageIsoCode + existing.name}
              label={existing.languageIsoCode + ': ' + existing.name}
              color={existing.display ? 'primary' : 'default'}
              variant={existing.origin === 'ADMIN' ? 'outlined' : 'filled'}
              onDelete={existing.origin === 'ADMIN' ?
                () => props.onRemove(existing.languageIsoCode, existing.name) : undefined}
            />
          ))}
        </Box>
        <Stack direction="row" spacing={1}>
          <TextField select size="small" label="Language" value={language} sx={{width: 120}}
            onChange={(event) => setLanguage(event.target.value)}>
            {LANGUAGES.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}
          </TextField>
          <TextField size="small" label="Another name" fullWidth value={name}
            onChange={(event) => setName(event.target.value)}
            onKeyDown={(event) => event.key === 'Enter' && add()} />
          <Button onClick={add}>Add</Button>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={props.onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};
