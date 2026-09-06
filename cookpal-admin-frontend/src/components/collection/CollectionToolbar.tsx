import AddIcon from '@mui/icons-material/Add';
import RefreshIcon from '@mui/icons-material/Refresh';
import SearchIcon from '@mui/icons-material/Search';
import SwapVertIcon from '@mui/icons-material/SwapVert';
import {Box, Button, Chip, IconButton, InputAdornment, MenuItem, Stack, TextField, Tooltip, Typography} from '@mui/material';
import {FieldDefinition} from './types';

export interface SortState<T> {
  key: string;
  descending: boolean;
  set: (key: string, descending: boolean) => void;
  fields: FieldDefinition<T>[];
}

export function CollectionToolbar<T>(props: {
  title: string,
  total: number,
  shown: number,
  selectedCount: number,
  query: string,
  onQueryChange: (query: string) => void,
  onReload: () => void,
  onCreate?: () => void,
  createLabel?: string,
  toolbarActions?: {label: string, icon: React.ReactNode, onRun: () => void}[],
  actions: {label: string, icon: React.ReactNode, onRun: () => void}[],
  sort?: SortState<T>,
}) {
  return (
    <Box sx={{mb: 1.5}}>
      <Stack
        direction={{xs: 'column', sm: 'row'}}
        spacing={1}
        alignItems={{xs: 'stretch', sm: 'center'}}
        sx={{mb: 1}}
      >
        <Stack direction="row" spacing={1} alignItems="center" sx={{flexGrow: 1, minWidth: 0}}>
          <Typography variant="h6" noWrap>{props.title}</Typography>
          <Chip size="small" label={props.shown === props.total ?
            props.total : props.shown + ' of ' + props.total} />
        </Stack>

        <TextField
          size="small"
          placeholder="Search"
          value={props.query}
          onChange={(event) => props.onQueryChange(event.target.value)}
          sx={{width: {xs: '100%', sm: 260}}}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment>
            ),
          }}
        />

        {props.sort && (
          <Stack direction="row" spacing={0.5} alignItems="center">
            <TextField
              select
              size="small"
              label="Sort by"
              value={props.sort.key}
              onChange={(event) => props.sort?.set(event.target.value, props.sort.descending)}
              sx={{flexGrow: 1}}
            >
              {props.sort.fields.map((field) => (
                <MenuItem key={field.key} value={field.key}>{field.label}</MenuItem>
              ))}
            </TextField>
            <Tooltip title={props.sort.descending ? 'Descending' : 'Ascending'}>
              <IconButton
                onClick={() => props.sort?.set(props.sort.key, !props.sort.descending)}
                aria-label="Reverse the order"
              >
                <SwapVertIcon />
              </IconButton>
            </Tooltip>
          </Stack>
        )}

        <Stack direction="row" spacing={1}>
          {(props.toolbarActions ?? []).map((action) => (
            <Button key={action.label} size="small" startIcon={action.icon} onClick={action.onRun}>
              {action.label}
            </Button>
          ))}
          <Tooltip title="Reload">
            <IconButton onClick={props.onReload} aria-label="Reload"><RefreshIcon /></IconButton>
          </Tooltip>
          {props.onCreate && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={props.onCreate}>
              {props.createLabel ?? 'Add'}
            </Button>
          )}
        </Stack>
      </Stack>

      {props.selectedCount > 0 && (
        <Stack
          direction="row"
          spacing={1}
          alignItems="center"
          sx={{p: 1, borderRadius: 1, bgcolor: 'action.selected', flexWrap: 'wrap'}}
        >
          <Typography variant="body2" sx={{mr: 1}}>{props.selectedCount} selected</Typography>
          {props.actions.map((action) => (
            <Button
              key={action.label}
              size="small"
              startIcon={action.icon}
              onClick={action.onRun}
            >
              {action.label}
            </Button>
          ))}
        </Stack>
      )}
    </Box>
  );
}
