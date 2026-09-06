import {Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle} from '@mui/material';

export interface Confirmation {
  title: string;
  message: string;
  confirmLabel?: string;
  destructive?: boolean;
  onConfirm: () => void;
}

export const ConfirmDialog = (props: {
  confirmation?: Confirmation,
  onClose: () => void,
}) => (
  <Dialog open={!!props.confirmation} onClose={props.onClose} fullWidth maxWidth="xs">
    <DialogTitle>{props.confirmation?.title}</DialogTitle>
    <DialogContent>
      <DialogContentText>{props.confirmation?.message}</DialogContentText>
    </DialogContent>
    <DialogActions>
      <Button onClick={props.onClose}>Cancel</Button>
      <Button
        variant="contained"
        color={props.confirmation?.destructive ? 'error' : 'primary'}
        onClick={() => {
          props.confirmation?.onConfirm();
          props.onClose();
        }}
      >
        {props.confirmation?.confirmLabel ?? 'Confirm'}
      </Button>
    </DialogActions>
  </Dialog>
);
