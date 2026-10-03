import {MenuItem, Paper, TextField, Typography} from '@mui/material';
import {InstanceApi, InstanceSettings, SignupMode} from '../../api';
import {useActionRunner} from '../../hooks/useActionRunner';
import {useAsyncData} from '../../hooks/useAsyncData';

const MODE_OPTIONS: {value: SignupMode, label: string}[] = [
  {value: 'OPEN', label: 'Open: anyone can sign up'},
  {value: 'INVITATION_ONLY', label: 'Invitation only: accounts are created from invitation links'},
];

const loadSettings = () => InstanceApi.settings();

export const RegistrationSettings = () => {
  const settings = useAsyncData<InstanceSettings | undefined>(loadSettings, undefined);
  const runner = useActionRunner(settings.reload);

  return (
    <Paper variant="outlined" sx={{p: 2}}>
      <Typography variant="overline" color="text.secondary">Registration</Typography>
      <TextField
        select
        fullWidth
        size="small"
        label="Who can sign up"
        sx={{mt: 1}}
        value={settings.data?.signupMode ?? ''}
        disabled={!settings.data}
        error={!!settings.error}
        helperText={settings.error}
        onChange={(event) => runner.run('Saved the registration mode',
            () => InstanceApi.updateSettings({signupMode: event.target.value as SignupMode}))}
      >
        {MODE_OPTIONS.map((option) => (
          <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
        ))}
      </TextField>
    </Paper>
  );
};
