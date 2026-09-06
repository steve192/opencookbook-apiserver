import CloseIcon from '@mui/icons-material/Close';
import {Box, Dialog, DialogContent, DialogTitle, Divider, IconButton, Typography, useMediaQuery, useTheme} from '@mui/material';
import {formatValue} from './fieldValues';
import {FieldDefinition} from './types';

export function DetailsDialog<T>(props: {
  title: string,
  row?: T,
  fields: FieldDefinition<T>[],
  onClose: () => void,
}) {
  const fullScreen = useMediaQuery(useTheme().breakpoints.down('sm'));

  return (
    <Dialog open={!!props.row} onClose={props.onClose} fullWidth maxWidth="sm" fullScreen={fullScreen}>
      <DialogTitle sx={{display: 'flex', alignItems: 'center', gap: 1}}>
        <Box sx={{flexGrow: 1}}>{props.title}</Box>
        <IconButton onClick={props.onClose} aria-label="Close"><CloseIcon /></IconButton>
      </DialogTitle>
      <DialogContent dividers>
        {props.row && props.fields.map((field) => (
          <Box key={field.key} sx={{py: 1}}>
            <Typography variant="caption" color="text.secondary">{field.label}</Typography>
            <Box sx={{wordBreak: 'break-word'}}>
              {field.render ?
                field.render(props.row as T) :
                <Typography variant="body2">{formatValue(field, props.row as T)}</Typography>}
            </Box>
            <Divider sx={{mt: 1}} />
          </Box>
        ))}
      </DialogContent>
    </Dialog>
  );
}
