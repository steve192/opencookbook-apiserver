import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import {Box, Button, Card, CardActions, CardContent, Checkbox, IconButton, Stack, Tooltip, Typography} from '@mui/material';
import {useEffect, useState} from 'react';
import {formatValue, importanceOf} from './fieldValues';
import {FieldDefinition, RowAction, RowId} from './types';

const PAGE_SIZE = 50;

// The collection on a phone: one card per entity, carrying the always-visible fields.
export function CollectionCards<T>(props: {
  rows: T[],
  fields: FieldDefinition<T>[],
  getRowId: (row: T) => RowId,
  rowActions: RowAction<T>[],
  onShowDetails: (row: T) => void,
  selection: RowId[],
  onSelectionChange: (selection: RowId[]) => void,
}) {
  const [headline, ...rest] = props.fields.filter((field) => importanceOf(field) === 'primary');

  // Thousands of cards at once is what makes a phone feel broken.
  const [shown, setShown] = useState(PAGE_SIZE);
  useEffect(() => setShown(PAGE_SIZE), [props.rows]);

  const toggle = (id: RowId) => props.onSelectionChange(
      props.selection.includes(id) ?
        props.selection.filter((selected) => selected !== id) :
        [...props.selection, id],
  );

  return (
    <Stack spacing={1.5} sx={{pb: 2}}>
      {props.rows.slice(0, shown).map((row) => {
        const id = props.getRowId(row);
        return (
          <Card key={String(id)} variant="outlined">
            <CardContent sx={{pb: 0}}>
              <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1}}>
                <Checkbox
                  sx={{mt: -1, ml: -1.5}}
                  checked={props.selection.includes(id)}
                  onChange={() => toggle(id)}
                  inputProps={{'aria-label': 'Select'}}
                />
                <Box sx={{minWidth: 0, flexGrow: 1}}>
                  <Typography variant="subtitle1" sx={{wordBreak: 'break-word'}}>
                    {headline && (headline.render ? headline.render(row) : formatValue(headline, row))}
                  </Typography>
                </Box>
              </Box>
              <Box sx={{mt: 1}}>
                {rest.map((field) => (
                  <Box key={field.key} sx={{display: 'flex', gap: 1, py: 0.25}}>
                    <Typography variant="body2" color="text.secondary" sx={{minWidth: 120}}>
                      {field.label}
                    </Typography>
                    <Box sx={{minWidth: 0, wordBreak: 'break-word'}}>
                      {field.render ?
                        field.render(row) :
                        <Typography variant="body2">{formatValue(field, row)}</Typography>}
                    </Box>
                  </Box>
                ))}
              </Box>
            </CardContent>
            <CardActions sx={{justifyContent: 'flex-end'}}>
              <Tooltip title="Details">
                <IconButton onClick={() => props.onShowDetails(row)} aria-label="Details">
                  <InfoOutlinedIcon />
                </IconButton>
              </Tooltip>
              {props.rowActions
                  .filter((action) => !action.hidden?.(row))
                  .map((action) => (
                    <Tooltip title={action.label} key={action.label}>
                      <IconButton
                        color={action.color ?? 'default'}
                        onClick={() => action.onRun(row)}
                        aria-label={action.label}
                      >
                        {action.icon}
                      </IconButton>
                    </Tooltip>
                  ))}
            </CardActions>
          </Card>
        );
      })}
      {props.rows.length > shown && (
        <Button onClick={() => setShown(shown + PAGE_SIZE)}>
          Show more ({props.rows.length - shown} left)
        </Button>
      )}
    </Stack>
  );
}
