import {Box, IconButton, Tooltip, useMediaQuery, useTheme} from '@mui/material';
import {DataGrid, GridColDef, GridRowSelectionModel, GridToolbar} from '@mui/x-data-grid';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import {useEffect, useMemo, useState} from 'react';
import {formatFieldValue, importanceOf, valueOf} from './fieldValues';
import {FieldDefinition, RowAction, RowId} from './types';

export function CollectionTable<T>(props: {
  rows: T[],
  fields: FieldDefinition<T>[],
  getRowId: (row: T) => RowId,
  rowActions: RowAction<T>[],
  onShowDetails: (row: T) => void,
  selection: RowId[],
  onSelectionChange: (selection: RowId[]) => void,
  loading: boolean,
}) {
  const theme = useTheme();
  const roomForEverything = useMediaQuery(theme.breakpoints.up('lg'));
  const {fields, rowActions, onShowDetails, getRowId} = props;

  const columns = useMemo<GridColDef[]>(() => {
    const fieldColumns = fields
        .filter((field) => importanceOf(field) !== 'reference')
        .map((field) => ({
          field: field.key,
          headerName: field.label,
          minWidth: field.width ?? 140,
          flex: 1,
          type: columnTypeOf(field),
          valueGetter: (params: {row: T}) => valueOf(field, params.row),
          renderCell: field.render ?
            (params: {row: T}) => field.render?.(params.row) :
            undefined,
          valueFormatter: field.kind === 'datetime' ?
            (params: {value: unknown}) => formatFieldValue(field.kind, params.value) :
            undefined,
        } as GridColDef));

    return [...fieldColumns, {
      field: '__actions',
      headerName: 'Actions',
      sortable: false,
      filterable: false,
      disableExport: true,
      minWidth: 60 + rowActions.length * 44,
      renderCell: (params) => (
        <Box>
          <Tooltip title="Details">
            <IconButton size="small" onClick={() => onShowDetails(params.row)} aria-label="Details">
              <InfoOutlinedIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          {rowActions
              .filter((action) => !action.hidden?.(params.row))
              .map((action) => (
                <Tooltip title={action.label} key={action.label}>
                  <IconButton
                    size="small"
                    color={action.color ?? 'default'}
                    onClick={() => action.onRun(params.row)}
                    aria-label={action.label}
                  >
                    {action.icon}
                  </IconButton>
                </Tooltip>
              ))}
        </Box>
      ),
    }];
  }, [fields, rowActions, onShowDetails]);

  // Secondary fields start hidden on a narrow screen; the column chooser has the last word.
  const defaultVisibility = useMemo(() => {
    if (roomForEverything) {
      return {};
    }
    return Object.fromEntries(fields
        .filter((field) => importanceOf(field) === 'secondary')
        .map((field) => [field.key, false]));
  }, [fields, roomForEverything]);

  const [columnVisibility, setColumnVisibility] = useState(defaultVisibility);
  useEffect(() => setColumnVisibility(defaultVisibility), [defaultVisibility]);

  return (
    <DataGrid
      rows={props.rows}
      columns={columns}
      getRowId={(row) => getRowId(row as T)}
      loading={props.loading}
      columnVisibilityModel={columnVisibility}
      onColumnVisibilityModelChange={setColumnVisibility}
      slots={{toolbar: GridToolbar}}
      slotProps={{toolbar: {printOptions: {disableToolbarButton: true}}}}
      initialState={{pagination: {paginationModel: {page: 0, pageSize: 50}}}}
      pageSizeOptions={[25, 50, 100, 500]}
      rowSelectionModel={props.selection}
      onRowSelectionModelChange={(model: GridRowSelectionModel) =>
        props.onSelectionChange(model as RowId[])}
      checkboxSelection
      disableRowSelectionOnClick
      onRowDoubleClick={(params) => onShowDetails(params.row)}
      sx={{'minHeight': 300, '& .MuiDataGrid-cell:focus': {outline: 'none'}}}
    />
  );
}

function columnTypeOf<T>(field: FieldDefinition<T>) {
  switch (field.kind) {
    case 'number':
      return 'number';
    case 'boolean':
      return 'boolean';
    default:
      return 'string';
  }
}
