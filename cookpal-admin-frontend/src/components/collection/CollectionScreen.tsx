import {Alert, Box, LinearProgress, Paper, Typography, useMediaQuery, useTheme} from '@mui/material';
import {ReactNode, useMemo, useState} from 'react';
import {AsyncData} from '../../hooks/useAsyncData';
import {ConfirmDialog, Confirmation} from '../ConfirmDialog';
import {CollectionCards} from './CollectionCards';
import {CollectionTable} from './CollectionTable';
import {CollectionToolbar} from './CollectionToolbar';
import {DetailsDialog} from './DetailsDialog';
import {compareValues, importanceOf, searchableText, valueOf} from './fieldValues';
import {BulkAction, FieldDefinition, RowAction, RowId} from './types';

// Every list in this panel: a table where there is room for one, cards where there is not.
export function CollectionScreen<T>(props: {
  title: string,
  fields: FieldDefinition<T>[],
  data: AsyncData<T[]>,
  getRowId: (row: T) => RowId,
  rowActions?: RowAction<T>[],
  bulkActions?: BulkAction<T>[],
  onCreate?: () => void,
  createLabel?: string,
  toolbarActions?: {label: string, icon: ReactNode, onRun: () => void}[],
  header?: ReactNode,
  detailsTitle?: (row: T) => string,
  emptyMessage?: string,
}) {
  const asCards = useMediaQuery(useTheme().breakpoints.down('md'));
  const [query, setQuery] = useState('');
  const [sortKey, setSortKey] = useState(props.fields[0]?.key ?? '');
  const [sortDescending, setSortDescending] = useState(false);
  const [selection, setSelection] = useState<RowId[]>([]);
  const [details, setDetails] = useState<T>();
  const [confirmation, setConfirmation] = useState<Confirmation>();

  const rows = props.data.data;

  const matching = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) {
      return rows;
    }
    return rows.filter((row) => searchableText(props.fields, row).includes(needle));
  }, [rows, query, props.fields]);

  // The table sorts itself through its own column headers; this order is what the cards use.
  const shown = useMemo(() => {
    if (!asCards) {
      return matching;
    }
    const field = props.fields.find((candidate) => candidate.key === sortKey);
    if (!field) {
      return matching;
    }
    return [...matching].sort((left, right) => {
      const order = compareValues(valueOf(field, left), valueOf(field, right));
      return sortDescending ? -order : order;
    });
  }, [matching, asCards, sortKey, sortDescending, props.fields]);

  const {getRowId, rowActions: declaredRowActions, bulkActions: declaredBulkActions} = props;

  const selectedRows = useMemo(
      () => rows.filter((row) => selection.includes(getRowId(row))),
      [rows, selection, getRowId],
  );

  // Held steady: the table rebuilds every column when these change identity, so the screens
  // hand in memoised arrays and setConfirmation is the stable setter from useState.
  const rowActions = useMemo(() => (declaredRowActions ?? []).map((action) => ({
    ...action,
    onRun: (row: T) => {
      if (!action.confirm) {
        action.onRun(row);
        return;
      }
      setConfirmation({
        title: action.label,
        message: action.confirm(row),
        confirmLabel: action.label,
        destructive: action.color === 'error',
        onConfirm: () => action.onRun(row),
      });
    },
  })), [declaredRowActions]);

  const bulkActions = useMemo(() => (declaredBulkActions ?? []).map((action) => {
    const run = () => {
      action.onRun(selectedRows);
      setSelection([]);
    };
    return {
      label: action.label,
      icon: action.icon,
      onRun: () => {
        if (!action.confirm) {
          run();
          return;
        }
        setConfirmation({
          title: action.label,
          message: action.confirm(selectedRows),
          confirmLabel: action.label,
          destructive: action.destructive !== false,
          onConfirm: run,
        });
      },
    };
  }), [declaredBulkActions, selectedRows]);

  return (
    <Box sx={{display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0}}>
      {props.header}
      <CollectionToolbar
        title={props.title}
        total={rows.length}
        shown={shown.length}
        selectedCount={selection.length}
        query={query}
        onQueryChange={setQuery}
        onReload={props.data.reload}
        onCreate={props.onCreate}
        createLabel={props.createLabel}
        toolbarActions={props.toolbarActions}
        actions={bulkActions}
        // Only the cards are sorted from here; the table has its own column headers.
        sort={asCards ? {
          key: sortKey,
          descending: sortDescending,
          set: (key, descending) => {
            setSortKey(key);
            setSortDescending(descending);
          },
          fields: props.fields.filter((field) => importanceOf(field) !== 'reference'),
        } : undefined}
      />

      {props.data.error && <Alert severity="error" sx={{mb: 1}}>{props.data.error}</Alert>}
      {props.data.loading && <LinearProgress sx={{mb: 1}} />}

      {!props.data.loading && shown.length === 0 ? (
        <Paper variant="outlined" sx={{p: 4, textAlign: 'center'}}>
          <Typography color="text.secondary">
            {query ? 'Nothing matches "' + query + '"' : (props.emptyMessage ?? 'Nothing here yet')}
          </Typography>
        </Paper>
      ) : (
        <Box sx={{flexGrow: 1, minHeight: 0, overflow: asCards ? 'auto' : 'hidden'}}>
          {asCards ? (
            <CollectionCards
              rows={shown}
              fields={props.fields}
              getRowId={getRowId}
              rowActions={rowActions}
              onShowDetails={setDetails}
              selection={selection}
              onSelectionChange={setSelection}
            />
          ) : (
            <CollectionTable
              rows={shown}
              fields={props.fields}
              getRowId={getRowId}
              rowActions={rowActions}
              onShowDetails={setDetails}
              selection={selection}
              onSelectionChange={setSelection}
              loading={props.data.loading}
            />
          )}
        </Box>
      )}

      <DetailsDialog
        title={details && props.detailsTitle ? props.detailsTitle(details) : props.title}
        row={details}
        fields={props.fields}
        onClose={() => setDetails(undefined)}
      />
      <ConfirmDialog
        confirmation={confirmation}
        onClose={() => setConfirmation(undefined)}
      />
    </Box>
  );
}
