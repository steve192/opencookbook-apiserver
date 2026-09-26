import BlockIcon from '@mui/icons-material/Block';
import DownloadIcon from '@mui/icons-material/Download';
import LinkIcon from '@mui/icons-material/Link';
import TranslateIcon from '@mui/icons-material/Translate';
import {Box, Button, Paper, Stack, Typography} from '@mui/material';
import {useMemo, useState} from 'react';
import {toast} from 'react-toastify';
import {CatalogueApi, Coverage, errorMessage, IngredientsApi, NameRulesApi, NutritionReportsApi,
  UnmatchedName} from '../api';
import {CollectionScreen} from '../components/collection/CollectionScreen';
import {FieldDefinition, RowAction} from '../components/collection/types';
import {describeFood, formatConfidence} from '../components/foodLabels';
import {EntityFormDialog} from '../components/form/EntityFormDialog';
import {FormFieldDefinition} from '../components/form/types';
import {NutritionTurnedOff} from '../components/NutritionTurnedOff';
import {StatTiles} from '../components/StatTiles';
import {useActionRunner} from '../hooks/useActionRunner';
import {useCollection} from '../hooks/useAsyncData';

const OTHER_FOOD = 'other';

const fields: FieldDefinition<UnmatchedName>[] = [
  {key: 'name', label: 'Name', width: 220},
  {key: 'userCount', label: 'Users', kind: 'number', width: 90},
  {key: 'useCount', label: 'Recipe lines', kind: 'number', width: 120},
  {
    key: 'candidates',
    label: 'Most likely foods',
    width: 420,
    value: (row) => row.candidates
        .map((candidate) => describeFood(candidate.food) + ' ' + formatConfidence(candidate.confidence))
        .join(', '),
  },
  {key: 'language', label: 'Language', width: 100, importance: 'secondary'},
  {key: 'ingredientIds', label: 'Ingredient ids', kind: 'list', importance: 'reference'},
];

interface FoodChoiceForm {
  food: string;
  otherFoodId: number | null;
  ingredientId: string;
}

const chosenFoodId = (form: FoodChoiceForm): number | null =>
  form.food === OTHER_FOOD ? form.otherFoodId : Number(form.food);

const foodChoiceFields = (name: UnmatchedName, withIngredient: boolean): FormFieldDefinition<FoodChoiceForm>[] => [
  {
    name: 'food',
    label: 'Food',
    type: 'select',
    required: true,
    options: [
      ...name.candidates.map((candidate) => ({
        value: String(candidate.food.id),
        label: describeFood(candidate.food) + ', ' + formatConfidence(candidate.confidence),
      })),
      {value: OTHER_FOOD, label: 'Another food, by its id in the catalogue'},
    ],
  },
  {name: 'otherFoodId', label: 'Id of another food', type: 'number'},
  ...(withIngredient ? [{
    name: 'ingredientId' as const,
    label: 'Ingredient',
    type: 'select' as const,
    required: true,
    options: name.ingredientIds.map((id) => ({value: String(id), label: 'Ingredient ' + id})),
    helperText: 'The ingredients of every user writing this name; see Ingredients for their owners',
  }] : []),
];

const download = (fileName: string, content: string) => {
  const url = URL.createObjectURL(new Blob([content], {type: 'text/tab-separated-values'}));
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
};

export const UnmatchedNamesScreen = () => {
  const names = useCollection(NutritionReportsApi.unmatchedNames);
  const runner = useActionRunner(names.reload);
  const [naming, setNaming] = useState<UnmatchedName>();
  const [linking, setLinking] = useState<UnmatchedName>();

  const stats = useMemo(() => [
    {label: 'Names', value: names.data.length},
    {label: 'Without a candidate', value: names.data.filter((name) => name.candidates.length === 0).length},
    {label: 'Recipe lines', value: names.data.reduce((sum, name) => sum + name.useCount, 0)},
  ], [names.data]);

  const rowActions = useMemo<RowAction<UnmatchedName>[]>(() => [
    {label: 'Add as name of a food', icon: <TranslateIcon fontSize="small" />, onRun: setNaming},
    {label: 'Link one ingredient', icon: <LinkIcon fontSize="small" />, onRun: setLinking},
    {
      label: 'Not a food',
      icon: <BlockIcon fontSize="small" />,
      color: 'error',
      confirm: (name) => 'Never link "' + name.name + '" to any food? The rule is listed under Name rules.',
      onRun: (name) => runner.run('"' + name.name + '" is no food', () => NameRulesApi.add(name.name, 'NOT_A_FOOD', null)),
    },
  ], [runner]);

  const exportNames = async () => {
    try {
      download('names-production.tsv', await NutritionReportsApi.namesExport());
    } catch (cause) {
      toast.error('Export failed: ' + errorMessage(cause));
    }
  };

  const addName = async (form: FoodChoiceForm) => {
    const foodId = chosenFoodId(form);
    if (naming && foodId !== null && await runner.run('Added "' + naming.name + '"; preview a run to link existing ingredients',
        () => CatalogueApi.addName(foodId, naming.language ?? 'de', naming.name))) {
      setNaming(undefined);
    }
  };

  const linkIngredient = async (form: FoodChoiceForm) => {
    const foodId = chosenFoodId(form);
    if (linking && foodId !== null && await runner.run('Linked ingredient ' + form.ingredientId,
        () => IngredientsApi.link(Number(form.ingredientId), foodId))) {
      setLinking(undefined);
    }
  };

  if (names.errorStatus === 404) {
    return <NutritionTurnedOff />;
  }

  const initialChoice = (name?: UnmatchedName): FoodChoiceForm => ({
    food: name?.candidates[0] ? String(name.candidates[0].food.id) : OTHER_FOOD,
    otherFoodId: null,
    ingredientId: name?.ingredientIds.length === 1 ? String(name.ingredientIds[0]) : '',
  });

  return (
    <>
      <CollectionScreen
        title="Unmatched names"
        fields={fields}
        data={names}
        getRowId={(name) => name.name + '|' + name.language}
        detailsTitle={(name) => name.name}
        emptyMessage="Every name users wrote is linked silently or decided"
        header={
          <>
            <CoveragePanel />
            <StatTiles stats={stats} />
          </>
        }
        toolbarActions={[{label: 'Export names', icon: <DownloadIcon />, onRun: exportNames}]}
        rowActions={rowActions}
      />
      <EntityFormDialog<FoodChoiceForm>
        open={!!naming}
        title={'Add "' + (naming?.name ?? '') + '" as a name'}
        fields={naming ? foodChoiceFields(naming, false) : []}
        initialValues={initialChoice(naming)}
        submitLabel="Add name"
        onClose={() => setNaming(undefined)}
        onSubmit={addName}
      />
      <EntityFormDialog<FoodChoiceForm>
        open={!!linking}
        title={'Link one "' + (linking?.name ?? '') + '"'}
        fields={linking ? foodChoiceFields(linking, true) : []}
        initialValues={initialChoice(linking)}
        submitLabel="Link"
        onClose={() => setLinking(undefined)}
        onSubmit={linkIngredient}
      />
    </>
  );
};

// Slow on large instances, so only on request.
const CoveragePanel = () => {
  const [coverage, setCoverage] = useState<Coverage>();
  const [running, setRunning] = useState(false);

  const run = async () => {
    setRunning(true);
    try {
      setCoverage(await NutritionReportsApi.coverage());
    } catch (cause) {
      toast.error('Coverage report failed: ' + errorMessage(cause));
    } finally {
      setRunning(false);
    }
  };

  const share = (count: number) => coverage && coverage.recipeCount > 0 ?
    Math.round(100 * count / coverage.recipeCount) + ' %' : '-';

  return (
    <Paper variant="outlined" sx={{p: 2, mb: 2}}>
      <Stack direction="row" spacing={2} sx={{alignItems: 'center', mb: coverage ? 2 : 0}}>
        <Typography variant="subtitle1" sx={{flexGrow: 1}}>Coverage over all recipes</Typography>
        <Button onClick={run} disabled={running}>{running ? 'Calculating…' : 'Run coverage report'}</Button>
      </Stack>
      {coverage && (
        <>
          <StatTiles stats={[
            {label: 'Recipes', value: coverage.recipeCount},
            {label: 'Complete', value: share(coverage.recipesByStatus.COMPLETE), color: 'success.main'},
            {label: 'Incomplete', value: share(coverage.recipesByStatus.INCOMPLETE), color: 'warning.main'},
            {label: 'Unavailable', value: share(coverage.recipesByStatus.UNAVAILABLE), color: 'error.main'},
            {label: 'Warning lines', value: coverage.warningLineCount + ' of ' + coverage.lineCount},
          ]} />
          <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', sm: '1fr 1fr'}, gap: 2}}>
            <CountList title="Why lines warn" counts={coverage.warningCauses} />
            <CountList title="Names warning most" counts={coverage.namesCausingWarnings} />
          </Box>
        </>
      )}
    </Paper>
  );
};

const CountList = (props: {title: string, counts: {key: string, count: number}[]}) => (
  <Box>
    <Typography variant="body2" gutterBottom sx={{fontWeight: 'bold'}}>{props.title}</Typography>
    {props.counts.map((entry) => (
      <Stack key={entry.key} direction="row" sx={{justifyContent: 'space-between'}}>
        <Typography variant="body2" noWrap>{entry.key}</Typography>
        <Typography variant="body2" color="text.secondary">{entry.count}</Typography>
      </Stack>
    ))}
  </Box>
);
