import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import {Chip} from '@mui/material';
import {useMemo, useState} from 'react';
import {Recipe, RecipeType, RecipesApi} from '../api';
import {CollectionScreen} from '../components/collection/CollectionScreen';
import {BulkAction, FieldDefinition, RowAction} from '../components/collection/types';
import {EntityFormDialog} from '../components/form/EntityFormDialog';
import {FormFieldDefinition} from '../components/form/types';
import {StatTiles} from '../components/StatTiles';
import {useActionRunner} from '../hooks/useActionRunner';
import {useCollection} from '../hooks/useAsyncData';

interface RecipeForm {
  title: string;
  servings: number | null;
  preparationTime: number | null;
  totalTime: number | null;
  recipeType: RecipeType | '';
  preparationSteps: string[];
}

const fields: FieldDefinition<Recipe>[] = [
  {key: 'title', label: 'Title', width: 240},
  {key: 'ownerEmailAddress', label: 'Owner', width: 220},
  {
    key: 'recipeType',
    label: 'Kind',
    width: 130,
    render: (recipe) => recipe.recipeType ?
      <Chip size="small" label={recipe.recipeType.toLowerCase()} /> :
      <span>-</span>,
  },
  {key: 'id', label: 'Id', kind: 'number', width: 80, importance: 'secondary'},
  {key: 'servings', label: 'Servings', kind: 'number', width: 100, importance: 'secondary'},
  {key: 'ingredientCount', label: 'Ingredients', kind: 'number', width: 110, importance: 'secondary'},
  {key: 'stepCount', label: 'Steps', kind: 'number', width: 90, importance: 'secondary'},
  {
    key: 'imageCount',
    label: 'Images',
    kind: 'number',
    width: 90,
    importance: 'secondary',
    value: (recipe) => recipe.imageUuids.length,
  },
  {key: 'createdOn', label: 'Created', kind: 'datetime', width: 180, importance: 'secondary'},
  {key: 'preparationTime', label: 'Preparation (min)', kind: 'number', importance: 'reference'},
  {key: 'totalTime', label: 'Total (min)', kind: 'number', importance: 'reference'},
  {key: 'recipeSource', label: 'Source', importance: 'reference'},
  {key: 'recipeGroups', label: 'Groups', kind: 'list', importance: 'reference'},
  {key: 'ingredients', label: 'Needed ingredients', kind: 'list', importance: 'reference'},
  {key: 'preparationSteps', label: 'Preparation steps', kind: 'list', importance: 'reference'},
  {key: 'lastChange', label: 'Last change', kind: 'datetime', importance: 'reference'},
];

const formFields: FormFieldDefinition<RecipeForm>[] = [
  {name: 'title', label: 'Title', type: 'text', required: true},
  {name: 'servings', label: 'Servings', type: 'number'},
  {name: 'preparationTime', label: 'Preparation time (minutes)', type: 'number'},
  {name: 'totalTime', label: 'Total time (minutes)', type: 'number'},
  {
    name: 'recipeType',
    label: 'Kind',
    type: 'select',
    options: [
      {value: '', label: 'Unspecified'},
      {value: 'VEGAN', label: 'Vegan'},
      {value: 'VEGETARIAN', label: 'Vegetarian'},
      {value: 'MEAT', label: 'Meat'},
    ],
  },
  {name: 'preparationSteps', label: 'Preparation steps', type: 'stringList'},
];

export const RecipesScreen = () => {
  const recipes = useCollection(RecipesApi.getAll);
  const runner = useActionRunner(recipes.reload);
  const [editing, setEditing] = useState<Recipe>();

  const stats = useMemo(() => [
    {label: 'Recipes', value: recipes.data.length},
    {label: 'With images', value: recipes.data.filter((recipe) => recipe.imageUuids.length > 0).length},
    {label: 'Owners', value: new Set(recipes.data.map((recipe) => recipe.ownerUserId)).size},
  ], [recipes.data]);

  // Memoised: the table rebuilds every column when these change identity.
  const rowActions = useMemo<RowAction<Recipe>[]>(() => [
    {label: 'Edit', icon: <EditIcon fontSize="small" />, onRun: setEditing},
    {
      label: 'Delete',
      icon: <DeleteIcon fontSize="small" />,
      color: 'error',
      confirm: (recipe) => 'Delete "' + recipe.title + '"? Its images and any share of it go with it.',
      onRun: (recipe) => runner.run('Deleted "' + recipe.title + '"',
          () => RecipesApi.delete(recipe.id)),
    },
  ], [runner]);

  const bulkActions = useMemo<BulkAction<Recipe>[]>(() => [
    {
      label: 'Delete',
      icon: <DeleteIcon fontSize="small" />,
      confirm: (selected) => 'Delete ' + selected.length + ' recipe(s)? This cannot be undone.',
      onRun: (selected) => runner.runAll('Deleted', selected,
          (recipe) => RecipesApi.delete(recipe.id)),
    },
  ], [runner]);

  return (
    <>
      <CollectionScreen
        title="Recipes"
        fields={fields}
        data={recipes}
        getRowId={(recipe) => recipe.id}
        detailsTitle={(recipe) => recipe.title}
        header={<StatTiles stats={stats} />}
        rowActions={rowActions}
        bulkActions={bulkActions}
      />

      <EntityFormDialog<RecipeForm>
        open={!!editing}
        title={'Edit "' + (editing?.title ?? '') + '"'}
        fields={formFields}
        initialValues={{
          title: editing?.title ?? '',
          servings: editing?.servings ?? null,
          preparationTime: editing?.preparationTime ?? null,
          totalTime: editing?.totalTime ?? null,
          recipeType: editing?.recipeType ?? '',
          preparationSteps: editing?.preparationSteps ?? [],
        }}
        onClose={() => setEditing(undefined)}
        onSubmit={async (values) => {
          const saved = await runner.run('Saved "' + values.title + '"',
              () => RecipesApi.update(editing!.id, {
                title: values.title,
                servings: values.servings,
                preparationTime: values.preparationTime,
                totalTime: values.totalTime,
                recipeType: values.recipeType === '' ? null : values.recipeType,
                preparationSteps: values.preparationSteps
                    .filter((step) => step.trim().length > 0),
              }));
          if (saved) {
            setEditing(undefined);
          }
        }}
      />
    </>
  );
};
