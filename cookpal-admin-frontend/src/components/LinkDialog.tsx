import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import {Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField} from '@mui/material';
import {useState} from 'react';
import {toast} from 'react-toastify';

export interface LinkContent {
  title: string;
  link: string;
  note: string;
}

// Without a secure context there is no clipboard api; the selectable field is the fallback.
const copyToClipboard = (text: string) =>
  Promise.resolve().then(() => navigator.clipboard.writeText(text))
      .then(() => toast.success('Copied to the clipboard'))
      .catch(() => toast.error('Copying failed, select the link and copy it by hand'));

// Invitation and password reset links are handed over by the admin, so they are shown in full.
export const LinkDialog = (props: {content?: LinkContent, onClose: () => void}) => {
  // Kept after closing, so the text does not change while the dialog fades out.
  const [shown, setShown] = useState(props.content);
  if (props.content && props.content !== shown) {
    setShown(props.content);
  }

  return (
    <Dialog open={!!props.content} onClose={props.onClose} fullWidth maxWidth="sm">
      <DialogTitle>{shown?.title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{pt: 1}}>
          {shown?.note && <Alert severity="info">{shown.note}</Alert>}
          <TextField
            label="Link"
            value={shown?.link ?? ''}
            fullWidth
            size="small"
            slotProps={{input: {readOnly: true}}}
            onFocus={(event) => event.target.select()}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={props.onClose}>Close</Button>
        <Button
          variant="contained"
          startIcon={<ContentCopyIcon />}
          onClick={() => copyToClipboard(shown?.link ?? '')}
        >
          Copy link
        </Button>
      </DialogActions>
    </Dialog>
  );
};
