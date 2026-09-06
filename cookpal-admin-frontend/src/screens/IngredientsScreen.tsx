import DataObjectIcon from '@mui/icons-material/DataObject';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import {Accordion, AccordionDetails, AccordionSummary, Alert, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, TextField} from '@mui/material';
import {useCallback, useMemo, useState} from 'react';
import {AlternativeName, Ingredient, IngredientDraft, IngredientsApi} from '../api';
import {CollectionScreen} from '../components/collection/CollectionScreen';
import {BulkAction, FieldDefinition, RowAction} from '../components/collection/types';
import {EntityFormDialog} from '../components/form/EntityFormDialog';
import {FormFieldDefinition} from '../components/form/types';
import {StatTiles} from '../components/StatTiles';
import {useActionRunner} from '../hooks/useActionRunner';
import {useCollection} from '../hooks/useAsyncData';

interface IngredientForm {
  name: string;
  additionalInfo: string | null;
  alternativeNames: AlternativeName[];
  nutrientsEnergy: number | null;
  nutrientsFat: number | null;
  nutrientsSaturatedFat: number | null;
  nutrientsCarbohydrates: number | null;
  nutrientsSugar: number | null;
  nutrientsProtein: number | null;
  nutrientsSalt: number | null;
}

const fields: FieldDefinition<Ingredient>[] = [
  {key: 'name', label: 'Name', width: 220},
  {
    key: 'publicIngredient',
    label: 'Public',
    kind: 'boolean',
    width: 110,
    render: (ingredient) => ingredient.publicIngredient ?
      <Chip size="small" color="primary" label="Public" /> :
      <Chip size="small" label="Private" />,
  },
  {key: 'nutrientsEnergy', label: 'Energy (kcal)', kind: 'number', width: 130},
  {key: 'id', label: 'Id', kind: 'number', width: 80, importance: 'secondary'},
  {key: 'ownerEmailAddress', label: 'Owner', width: 200, importance: 'secondary'},
  {key: 'nutrientsFat', label: 'Fat (g)', kind: 'number', width: 110, importance: 'secondary'},
  {key: 'nutrientsCarbohydrates', label: 'Carbs (g)', kind: 'number', width: 110, importance: 'secondary'},
  {key: 'nutrientsProtein', label: 'Protein (g)', kind: 'number', width: 110, importance: 'secondary'},
  {key: 'nutrientsSalt', label: 'Salt (g)', kind: 'number', width: 100, importance: 'secondary'},
  {key: 'nutrientsSaturatedFat', label: 'Saturated fat (g)', kind: 'number', importance: 'reference'},
  {key: 'nutrientsSugar', label: 'Sugar (g)', kind: 'number', importance: 'reference'},
  {key: 'additionalInfo', label: 'Additional info', importance: 'reference'},
  {key: 'aliasForName', label: 'Alias for', importance: 'reference'},
  {
    key: 'alternativeNames',
    label: 'Alternative names',
    kind: 'list',
    importance: 'reference',
    value: (ingredient) => ingredient.alternativeNames
        .map((name) => name.languageIsoCode + ': ' + name.alternativeName),
  },
  {key: 'createdOn', label: 'Created', kind: 'datetime', importance: 'reference'},
  {key: 'lastChange', label: 'Last change', kind: 'datetime', importance: 'reference'},
];

const formFields: FormFieldDefinition<IngredientForm>[] = [
  {name: 'name', label: 'Name', type: 'text', required: true},
  {name: 'additionalInfo', label: 'Additional info', type: 'text'},
  {
    name: 'alternativeNames',
    label: 'Alternative names',
    type: 'pairList',
    pairKeys: ['languageIsoCode', 'alternativeName'],
    pairLabels: ['Language', 'Name'],
  },
  {name: 'nutrientsEnergy', label: 'Energy (kcal)', type: 'number', section: 'Nutrients per 100 g'},
  {name: 'nutrientsFat', label: 'Fat (g)', type: 'number', section: 'Nutrients per 100 g'},
  {name: 'nutrientsSaturatedFat', label: 'Saturated fat (g)', type: 'number', section: 'Nutrients per 100 g'},
  {name: 'nutrientsCarbohydrates', label: 'Carbohydrates (g)', type: 'number', section: 'Nutrients per 100 g'},
  {name: 'nutrientsSugar', label: 'Sugar (g)', type: 'number', section: 'Nutrients per 100 g'},
  {name: 'nutrientsProtein', label: 'Protein (g)', type: 'number', section: 'Nutrients per 100 g'},
  {name: 'nutrientsSalt', label: 'Salt (g)', type: 'number', section: 'Nutrients per 100 g'},
];

const EMPTY_FORM: IngredientForm = {
  name: '',
  additionalInfo: null,
  alternativeNames: [],
  nutrientsEnergy: null,
  nutrientsFat: null,
  nutrientsSaturatedFat: null,
  nutrientsCarbohydrates: null,
  nutrientsSugar: null,
  nutrientsProtein: null,
  nutrientsSalt: null,
};

export const IngredientsScreen = () => {
  const ingredients = useCollection(IngredientsApi.getAll);
  const runner = useActionRunner(ingredients.reload);
  const [editing, setEditing] = useState<Ingredient>();
  const [adding, setAdding] = useState(false);
  const [addingFromJson, setAddingFromJson] = useState(false);

  const stats = useMemo(() => [
    {label: 'Ingredients', value: ingredients.data.length},
    {label: 'Public', value: ingredients.data.filter((one) => one.publicIngredient).length},
    {
      label: 'Public without nutrients',
      value: ingredients.data
          .filter((one) => one.publicIngredient && one.nutrientsEnergy === null).length,
    },
  ], [ingredients.data]);

  // Left open on a failure, so the nutrient table that was typed in is not lost.
  const save = async (values: IngredientForm) => {
    const draft: IngredientDraft = {
      ...values,
      alternativeNames: values.alternativeNames
          .filter((name) => name.alternativeName.trim().length > 0),
    };
    if (editing) {
      if (await runner.run('Saved ' + values.name,
          () => IngredientsApi.update(editing.id, draft))) {
        setEditing(undefined);
      }
    } else if (await runner.run('Added ' + values.name, () => IngredientsApi.create(draft))) {
      setAdding(false);
    }
  };

  // Memoised: the table rebuilds every column when these change identity.
  const rowActions = useMemo<RowAction<Ingredient>[]>(() => [
    {label: 'Edit', icon: <EditIcon fontSize="small" />, onRun: setEditing},
    {
      label: 'Delete',
      icon: <DeleteIcon fontSize="small" />,
      color: 'error',
      confirm: (ingredient) => 'Delete "' + ingredient.name + '"?',
      onRun: (ingredient) => runner.run('Deleted ' + ingredient.name,
          () => IngredientsApi.delete(ingredient.id)),
    },
  ], [runner]);

  const bulkActions = useMemo<BulkAction<Ingredient>[]>(() => [
    {
      label: 'Delete',
      icon: <DeleteIcon fontSize="small" />,
      confirm: (selected) => 'Delete ' + selected.length + ' ingredient(s)?',
      onRun: (selected) => runner.runAll('Deleted', selected,
          (ingredient) => IngredientsApi.delete(ingredient.id)),
    },
  ], [runner]);

  const importFromJson = useCallback(async (drafts: IngredientDraft[]) => {
    if (await runner.runAll('Added', drafts, (draft) => IngredientsApi.create(draft))) {
      setAddingFromJson(false);
    }
  }, [runner]);

  return (
    <>
      <CollectionScreen
        title="Ingredients"
        fields={fields}
        data={ingredients}
        getRowId={(ingredient) => ingredient.id}
        detailsTitle={(ingredient) => ingredient.name}
        header={<StatTiles stats={stats} />}
        onCreate={() => setAdding(true)}
        createLabel="Add public ingredient"
        toolbarActions={[{
          label: 'From JSON',
          icon: <DataObjectIcon fontSize="small" />,
          onRun: () => setAddingFromJson(true),
        }]}
        rowActions={rowActions}
        bulkActions={bulkActions}
      />

      <EntityFormDialog<IngredientForm>
        open={adding || !!editing}
        title={editing ? 'Edit ' + editing.name : 'Add a public ingredient'}
        fields={formFields}
        initialValues={editing ? {
          name: editing.name,
          additionalInfo: editing.additionalInfo,
          alternativeNames: editing.alternativeNames,
          nutrientsEnergy: editing.nutrientsEnergy,
          nutrientsFat: editing.nutrientsFat,
          nutrientsSaturatedFat: editing.nutrientsSaturatedFat,
          nutrientsCarbohydrates: editing.nutrientsCarbohydrates,
          nutrientsSugar: editing.nutrientsSugar,
          nutrientsProtein: editing.nutrientsProtein,
          nutrientsSalt: editing.nutrientsSalt,
        } : EMPTY_FORM}
        submitLabel={editing ? 'Save' : 'Add'}
        onClose={() => {
          setEditing(undefined);
          setAdding(false);
        }}
        onSubmit={save}
      />

      <JsonImportDialog
        open={addingFromJson}
        onClose={() => setAddingFromJson(false)}
        onImport={importFromJson}
      />
    </>
  );
};

const JSON_EXAMPLE = JSON.stringify({
  name: 'Potato',
  alternativeNames: [{languageIsoCode: 'en', alternativeName: 'Spud'}],
  nutrientsEnergy: 77,
  nutrientsFat: 0.1,
  nutrientsSaturatedFat: 0,
  nutrientsCarbohydrates: 17,
  nutrientsSugar: 0.8,
  nutrientsProtein: 2,
  nutrientsSalt: 0,
}, null, 2);

const PROMPT = `Generate a JSON object for an ingredient with this schema:
{
  name: string,
  alternativeNames: Array<{ languageIsoCode: string, alternativeName: string }>,
  nutrientsEnergy: number,  // kcal per 100g
  nutrientsFat: number,     // g per 100g
  nutrientsSaturatedFat: number,
  nutrientsCarbohydrates: number,
  nutrientsSugar: number,
  nutrientsProtein: number,
  nutrientsSalt: number
}

Give several common alternative names in English (en) and German (de); they are used to
recognise the same thing under another name. Real values only, no ranges. Answer with JSON
only, it is parsed by a machine. A JSON array adds several ingredients at once.

Do this for: Potato`;

// Nutrient tables are tedious to type, so a whole ingredient can be pasted in as JSON.
const JsonImportDialog = (props: {
  open: boolean,
  onClose: () => void,
  onImport: (drafts: IngredientDraft[]) => Promise<void>,
}) => {
  const [json, setJson] = useState('');
  const [error, setError] = useState<string>();

  const submit = async () => {
    let drafts: IngredientDraft[];
    try {
      const parsed: unknown = JSON.parse(json);
      drafts = (Array.isArray(parsed) ? parsed : [parsed])
          .map((entry) => ({...EMPTY_FORM, ...entry}));
    } catch (cause) {
      setError('That is not valid JSON: ' + (cause as Error).message);
      return;
    }
    // What the server makes of it is reported by the runner, as a toast per ingredient.
    setError(undefined);
    await props.onImport(drafts);
    setJson('');
  };

  return (
    <Dialog open={props.open} onClose={props.onClose} fullWidth maxWidth="sm">
      <DialogTitle>Add ingredients from JSON</DialogTitle>
      <DialogContent dividers>
        {error && <Alert severity="error" sx={{mb: 2}}>{error}</Alert>}
        <TextField
          label="One ingredient, or an array of them"
          multiline
          minRows={8}
          fullWidth
          value={json}
          onChange={(event) => setJson(event.target.value)}
        />
        <Accordion sx={{mt: 2}}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            Example and a prompt that produces it
          </AccordionSummary>
          <AccordionDetails>
            <pre style={{overflowX: 'auto', whiteSpace: 'pre-wrap'}}>{JSON_EXAMPLE}</pre>
            <pre style={{overflowX: 'auto', whiteSpace: 'pre-wrap'}}>{PROMPT}</pre>
          </AccordionDetails>
        </Accordion>
      </DialogContent>
      <DialogActions>
        <Button onClick={props.onClose}>Cancel</Button>
        <Button variant="contained" onClick={submit}>Add</Button>
      </DialogActions>
    </Dialog>
  );
};
