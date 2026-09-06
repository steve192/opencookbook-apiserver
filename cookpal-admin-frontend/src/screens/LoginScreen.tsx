import LoginIcon from '@mui/icons-material/Login';
import {Alert, Box, Button, Container, Paper, Stack, TextField, Typography} from '@mui/material';
import {FormEvent, useState} from 'react';
import {ApiError} from '../api';
import {useSession} from '../AppContext';
import {NAVIGATION_BACKGROUND} from '../theme';

export const LoginScreen = () => {
  const {signIn} = useSession();
  const [emailAddress, setEmailAddress] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string>();
  const [signingIn, setSigningIn] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSigningIn(true);
    setError(undefined);
    try {
      await signIn(emailAddress, password);
    } catch (cause) {
      setError(cause instanceof ApiError && cause.status === 401 ?
        'That email address and password do not match' :
        (cause as Error).message);
    } finally {
      setSigningIn(false);
    }
  };

  return (
    <Box sx={{
      minHeight: '100dvh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      p: 2,
      backgroundColor: NAVIGATION_BACKGROUND,
    }}>
      <Container maxWidth="xs" disableGutters>
        <Paper sx={{p: {xs: 3, sm: 4}, width: '100%'}} elevation={6}>
          <Stack spacing={1} sx={{mb: 3}}>
            <Typography variant="h5" component="h1">Cookpal admin</Typography>
            <Typography variant="body2" color="text.secondary">
              Sign in with an account that has the administrator role.
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
                autoComplete="current-password"
                required
                fullWidth
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
              <Button
                type="submit"
                variant="contained"
                size="large"
                fullWidth
                disabled={signingIn}
                startIcon={<LoginIcon />}
              >
                {signingIn ? 'Signing in...' : 'Sign in'}
              </Button>
            </Stack>
          </form>
        </Paper>
      </Container>
    </Box>
  );
};
