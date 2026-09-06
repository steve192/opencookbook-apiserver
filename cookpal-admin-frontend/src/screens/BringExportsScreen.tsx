import DeleteIcon from '@mui/icons-material/Delete';
import {Chip} from '@mui/material';
import {BringExport, BringExportsApi} from '../api';
import {useMemo} from 'react';
import {CollectionScreen} from '../components/collection/CollectionScreen';
import {BulkAction, FieldDefinition, RowAction} from '../components/collection/types';
import {useActionRunner} from '../hooks/useActionRunner';
import {useCollection} from '../hooks/useAsyncData';

const fields: FieldDefinition<BringExport>[] = [
  {key: 'ownerEmailAddress', label: 'Exported by', width: 240},
  {key: 'ingredientCount', label: 'Items', kind: 'number', width: 90},
  {
    key: 'expired',
    label: 'State',
    kind: 'boolean',
    width: 110,
    render: (bringExport) => bringExport.expired ?
      <Chip size="small" label="Lapsed" /> :
      <Chip size="small" color="success" label="Fetchable" />,
  },
  {key: 'createdOn', label: 'Exported', kind: 'datetime', width: 180, importance: 'secondary'},
  {key: 'expiresAt', label: 'Lapses', kind: 'datetime', width: 180, importance: 'secondary'},
  {key: 'baseAmount', label: 'Servings', kind: 'number', width: 100, importance: 'secondary'},
  {key: 'id', label: 'Id', importance: 'reference'},
  {key: 'ingredients', label: 'Shopping list', kind: 'list', importance: 'reference'},
];

export const BringExportsScreen = () => {
  const exports = useCollection(BringExportsApi.getAll);
  const runner = useActionRunner(exports.reload);

  // Memoised: the table rebuilds every column when these change identity.
  const rowActions = useMemo<RowAction<BringExport>[]>(() => [
    {
      label: 'Delete',
      icon: <DeleteIcon fontSize="small" />,
      color: 'error',
      confirm: () => 'Delete this export? Bring can no longer fetch it.',
      onRun: (bringExport) => runner.run('Deleted the export',
          () => BringExportsApi.delete(bringExport.id)),
    },
  ], [runner]);

  const bulkActions = useMemo<BulkAction<BringExport>[]>(() => [
    {
      label: 'Delete',
      icon: <DeleteIcon fontSize="small" />,
      confirm: (selected) => 'Delete ' + selected.length + ' export(s)?',
      onRun: (selected) => runner.runAll('Deleted', selected,
          (bringExport) => BringExportsApi.delete(bringExport.id)),
    },
  ], [runner]);

  return (
    <CollectionScreen
      title="Bring exports"
      fields={fields}
      data={exports}
      getRowId={(bringExport) => bringExport.id}
      detailsTitle={(bringExport) => 'Export by ' + (bringExport.ownerEmailAddress ?? 'somebody')}
      emptyMessage="Nobody has handed a shopping list to Bring recently"
      rowActions={rowActions}
      bulkActions={bulkActions}
    />
  );
};
