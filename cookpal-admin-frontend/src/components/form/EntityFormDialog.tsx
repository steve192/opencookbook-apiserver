import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import {Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Divider, FormControlLabel, IconButton, MenuItem, Stack, Switch, TextField, Typography, useMediaQuery, useTheme} from '@mui/material';
import {Fragment, useEffect, useRef, useState} from 'react';
import {FormFieldDefinition, FormValue, FormValues, PairListEntry,
  PairListFieldDefinition} from './types';


export function EntityFormDialog<V extends FormValues<V>>(props: {
  open: boolean,
  title: string,
  fields: FormFieldDefinition<V>[],
  initialValues: V,
  submitLabel?: string,
  onSubmit: (values: V) => void | Promise<void>,
  onClose: () => void,
}) {
  const fullScreen = useMediaQuery(useTheme().breakpoints.down('sm'));
  const [values, setValues] = useState<V>(props.initialValues);
  const [touched, setTouched] = useState(false);
  const [saving, setSaving] = useState(false);

  // Only on opening: resetting on every render would wipe what is being typed.
  const wasOpen = useRef(false);
  useEffect(() => {
    if (props.open && !wasOpen.current) {
      setValues(props.initialValues);
      setTouched(false);
    }
    wasOpen.current = props.open;
  }, [props.open, props.initialValues]);

  // Functional update: two fields changing in one tick must not undo each other.
  const set = (name: keyof V & string, value: FormValue) =>
    setValues((current) => ({...current, [name]: value} as V));

  const missing = props.fields.filter((field) => field.required && isEmpty(values[field.name]));

  const submit = async () => {
    setTouched(true);
    if (missing.length > 0) {
      return;
    }
    setSaving(true);
    try {
      await props.onSubmit(values);
    } finally {
      setSaving(false);
    }
  };

  let lastSection: string | undefined;

  return (
    <Dialog open={props.open} onClose={props.onClose} fullWidth maxWidth="sm" fullScreen={fullScreen}>
      <DialogTitle>{props.title}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2} sx={{pt: 1}}>
          {props.fields.map((field) => {
            const section = field.section !== lastSection ? field.section : undefined;
            lastSection = field.section;
            return (
              <Fragment key={field.name}>
                {section && (
                  <Box>
                    <Typography variant="overline" color="text.secondary">{section}</Typography>
                    <Divider />
                  </Box>
                )}
                <FormField
                  field={field}
                  value={values[field.name]}
                  invalid={touched && field.required === true && isEmpty(values[field.name])}
                  onChange={(value) => set(field.name, value)}
                />
              </Fragment>
            );
          })}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={props.onClose}>Cancel</Button>
        <Button variant="contained" onClick={submit} disabled={saving}>
          {props.submitLabel ?? 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function FormField<V>(props: {
  field: FormFieldDefinition<V>,
  value: FormValue,
  invalid: boolean,
  onChange: (value: FormValue) => void,
}) {
  const {field, value} = props;
  const common = {
    label: field.label,
    fullWidth: true,
    size: 'small' as const,
    error: props.invalid,
    helperText: props.invalid ? field.label + ' is needed' : field.helperText,
  };

  switch (field.type) {
    case 'switch':
      return (
        <FormControlLabel
          control={<Switch checked={!!value} onChange={(event) => props.onChange(event.target.checked)} />}
          label={field.label}
        />
      );
    case 'select':
      return (
        <TextField {...common} select value={String(value ?? '')}
          onChange={(event) => props.onChange(event.target.value === '' ? null : event.target.value)}>
          {field.options.map((option) => (
            <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
          ))}
        </TextField>
      );
    case 'number':
      return (
        <TextField {...common} type="number" value={value ?? ''}
          onChange={(event) => props.onChange(event.target.value === '' ?
            null : Number(event.target.value))} />
      );
    case 'multiline':
      return (
        <TextField {...common} multiline minRows={3} value={String(value ?? '')}
          onChange={(event) => props.onChange(event.target.value)} />
      );
    case 'stringList':
      return (
        <TextField {...common} multiline minRows={3}
          helperText={field.helperText ?? 'One per line'}
          value={((value as string[]) ?? []).join('\n')}
          onChange={(event) => props.onChange(event.target.value.split('\n'))} />
      );
    case 'pairList':
      return (
        <PairList
          field={field}
          value={(value as PairListEntry[]) ?? []}
          onChange={props.onChange}
        />
      );
    default:
      return (
        <TextField {...common} value={String(value ?? '')}
          onChange={(event) => props.onChange(event.target.value)} />
      );
  }
}

function PairList<V>(props: {
  field: PairListFieldDefinition<V>,
  value: PairListEntry[],
  onChange: (value: FormValue) => void,
}) {
  const [first, second] = props.field.pairKeys;
  const [firstLabel, secondLabel] = props.field.pairLabels;

  const change = (index: number, key: string, entry: string) => props.onChange(
      props.value.map((existing, position) =>
        position === index ? {...existing, [key]: entry} : existing),
  );

  return (
    <Box>
      <Typography variant="overline" color="text.secondary">{props.field.label}</Typography>
      <Stack spacing={1}>
        {props.value.map((entry, index) => (
          <Stack direction="row" spacing={1} key={index} alignItems="center">
            <TextField
              size="small"
              label={firstLabel}
              sx={{width: 120}}
              value={String(entry[first] ?? '')}
              onChange={(event) => change(index, first, event.target.value)}
            />
            <TextField
              size="small"
              label={secondLabel}
              fullWidth
              value={String(entry[second] ?? '')}
              onChange={(event) => change(index, second, event.target.value)}
            />
            <IconButton
              aria-label="Remove"
              onClick={() => props.onChange(props.value.filter((_, position) => position !== index))}
            >
              <DeleteOutlineIcon />
            </IconButton>
          </Stack>
        ))}
        <Box>
          <Button
            size="small"
            startIcon={<AddIcon />}
            onClick={() => props.onChange([...props.value, {[first]: '', [second]: ''}])}
          >
            Add
          </Button>
        </Box>
      </Stack>
    </Box>
  );
}

function isEmpty(value: unknown): boolean {
  return value === null || value === undefined || value === '' ||
    (Array.isArray(value) && value.length === 0);
}
