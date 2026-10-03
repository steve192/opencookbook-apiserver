import {Alert, Box, Button, Stack, TextField, Typography} from '@mui/material';
import {FormEvent, useEffect, useRef, useState} from 'react';
import {InstanceApi, errorMessage} from '../api';
import {useSession} from '../AppContext';
import {CenteredPage} from '../components/CenteredPage';
import {InstanceDetails} from '../components/instance/InstanceDetails';

// Step 1: the first visitor creates the administrator account and is signed in with it. A failure
// may mean that the setup is done after all (another visitor was first), so the state is read again.
const AdministratorForm = (props: {onFailure: () => void}) => {
  const {signIn} = useSession();
  const [emailAddress, setEmailAddress] = useState('');
  const [password, setPassword] = useState('');
  const [repeated, setRepeated] = useState('');
  const [error, setError] = useState<string>();
  const [working, setWorking] = useState(false);

  const mismatch = repeated !== '' && repeated !== password;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setWorking(true);
    setError(undefined);
    try {
      await InstanceApi.setup(emailAddress, password);
      await signIn(emailAddress, password);
    } catch (cause) {
      setError(errorMessage(cause));
      props.onFailure();
    } finally {
      setWorking(false);
    }
  };

  return (
    <CenteredPage width="xs">
      <Stack spacing={1} sx={{mb: 3}}>
        <Typography variant="h5" component="h1">Set up Cookpal</Typography>
        <Typography variant="body2" color="text.secondary">
          This instance has no administrator yet. Create the account that manages it.
        </Typography>
      </Stack>
      <form onSubmit={submit}>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            label="Email address"
            type="email"
            autoComplete="username"
            autoFocus
            required
            fullWidth
            value={emailAddress}
            onChange={(event) => setEmailAddress(event.target.value)}
          />
          <TextField
            label="Password"
            type="password"
            autoComplete="new-password"
            required
            fullWidth
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <TextField
            label="Repeat the password"
            type="password"
            autoComplete="new-password"
            required
            fullWidth
            error={mismatch}
            helperText={mismatch ? 'The passwords do not match' : undefined}
            value={repeated}
            onChange={(event) => setRepeated(event.target.value)}
          />
          <Button
            type="submit"
            variant="contained"
            size="large"
            fullWidth
            disabled={working || mismatch}
          >
            {working ? 'Creating...' : 'Create the administrator'}
          </Button>
        </Stack>
      </form>
    </CenteredPage>
  );
};

// Step 2: how the instance is configured, and who may sign up.
const ConfigurationStep = (props: {onFinish: () => void}) => (
  <CenteredPage width="md">
    <Stack spacing={2}>
      <Box>
        <Typography variant="h5" component="h1">Your instance</Typography>
        <Typography variant="body2" color="text.secondary">
          Check what works and choose who may sign up. All of this can be changed later on the
          Instance screen.
        </Typography>
      </Box>
      <InstanceDetails />
      <Button variant="contained" size="large" onClick={props.onFinish}>Finish</Button>
    </Stack>
  </CenteredPage>
);

export const SetupScreen = ({onFinish, reloadInstance}: {onFinish: () => void, reloadInstance: () => void}) => {
  const {signedIn} = useSession();

  // The session can end during step 2; the stale state would show step 1 again.
  const wasSignedIn = useRef(signedIn);
  useEffect(() => {
    if (wasSignedIn.current && !signedIn) {
      reloadInstance();
    }
    wasSignedIn.current = signedIn;
  }, [signedIn, reloadInstance]);

  return signedIn ?
    <ConfigurationStep onFinish={onFinish} /> :
    <AdministratorForm onFailure={reloadInstance} />;
};
