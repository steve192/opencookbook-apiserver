import DeleteIcon from '@mui/icons-material/Delete';
import {Chip} from '@mui/material';
import {useMemo} from 'react';
import {Ingredient, IngredientsApi, LinkSource} from '../api';
import {CollectionScreen} from '../components/collection/CollectionScreen';
import {BulkAction, FieldDefinition, RowAction} from '../components/collection/types';
import {StatTiles} from '../components/StatTiles';
import {useActionRunner} from '../hooks/useActionRunner';
import {useCollection} from '../hooks/useAsyncData';

const LINK_SOURCE_LABELS: Record<LinkSource, string> = {
  AUTO: 'Matched',
  USER: 'By the owner',
  ADMIN: 'By an admin',
};

const fields: FieldDefinition<Ingredient>[] = [
  {key: 'name', label: 'Name', width: 220},
  {key: 'catalogueFoodName', label: 'Linked to', width: 220},
  {
    key: 'linkSource',
    label: 'Link',
    width: 140,
    value: (ingredient) => ingredient.linkSource && LINK_SOURCE_LABELS[ingredient.linkSource],
    render: (ingredient) => ingredient.excludedFromNutrition ?
      <Chip size="small" label="Not a food" /> :
      ingredient.linkSource ?
        <Chip size="small" color="primary" label={LINK_SOURCE_LABELS[ingredient.linkSource]} /> :
        <Chip size="small" color="warning" label="Unlinked" />,
  },
  {key: 'ownerEmailAddress', label: 'Owner', width: 220, importance: 'secondary'},
  {key: 'linkConfidence', label: 'Confidence', kind: 'number', width: 120, importance: 'secondary'},
  {key: 'id', label: 'Id', kind: 'number', width: 80, importance: 'secondary'},
  {key: 'catalogueFoodKey', label: 'Catalogue key', importance: 'reference'},
  {key: 'linkMatcherVersion', label: 'Matcher version', kind: 'number', importance: 'reference'},
  {key: 'linkedAt', label: 'Linked', kind: 'datetime', importance: 'reference'},
  {key: 'excludedFromNutrition', label: 'Not a food', kind: 'boolean', importance: 'reference'},
  {key: 'additionalInfo', label: 'Additional info', importance: 'reference'},
  {key: 'createdOn', label: 'Created', kind: 'datetime', importance: 'reference'},
  {key: 'lastChange', label: 'Last change', kind: 'datetime', importance: 'reference'},
];

// Nothing to add or edit: ingredients are what users named in their recipes.
export const IngredientsScreen = () => {
  const ingredients = useCollection(IngredientsApi.getAll);
  const runner = useActionRunner(ingredients.reload);

  const stats = useMemo(() => [
    {label: 'Ingredients', value: ingredients.data.length},
    {label: 'Linked', value: ingredients.data.filter((one) => one.catalogueFoodId !== null).length},
    {
      label: 'Unlinked',
      value: ingredients.data
          .filter((one) => one.catalogueFoodId === null && !one.excludedFromNutrition).length,
      color: 'warning.main',
    },
  ], [ingredients.data]);

  // Memoised: the table rebuilds every column when these change identity.
  const rowActions = useMemo<RowAction<Ingredient>[]>(() => [
    {
      label: 'Delete',
      icon: <DeleteIcon fontSize="small" />,
      color: 'error',
      confirm: (ingredient) => 'Delete "' + ingredient.name + '"? Only possible while no recipe uses it.',
      onRun: (ingredient) => runner.run('Deleted ' + ingredient.name,
          () => IngredientsApi.delete(ingredient.id)),
    },
  ], [runner]);

  const bulkActions = useMemo<BulkAction<Ingredient>[]>(() => [
    {
      label: 'Delete',
      icon: <DeleteIcon fontSize="small" />,
      confirm: (selected) => 'Delete ' + selected.length + ' ingredient(s)? Those a recipe uses stay.',
      onRun: (selected) => runner.runAll('Deleted', selected,
          (ingredient) => IngredientsApi.delete(ingredient.id)),
    },
  ], [runner]);

  return (
    <CollectionScreen
      title="Ingredients"
      fields={fields}
      data={ingredients}
      getRowId={(ingredient) => ingredient.id}
      detailsTitle={(ingredient) => ingredient.name}
      header={<StatTiles stats={stats} />}
      emptyMessage="Nobody has named an ingredient yet"
      rowActions={rowActions}
      bulkActions={bulkActions}
    />
  );
};
