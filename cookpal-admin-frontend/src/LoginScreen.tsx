import React, {useContext, useState} from 'react';
import {Container, TextField, Button, Typography} from '@mui/material';
import {styled} from '@mui/system';
import RestAPI from './RestAPI';
import {AppContext} from './AppContext';

const StyledContainer = styled(Container)({
  marginTop: '8rem',
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  justifyContent: 'center', // Added to center vertically
  height: '100vh', // Added to ensure full viewport height
});

const StyledForm = styled('form')({
  width: '100%',
  marginTop: '1rem',
});

const StyledSubmitButton = styled(Button)({
  margin: '3rem 0 2rem',
});

const StyledTextField = styled(TextField)({
  '& .MuiOutlinedInput-root': {
    '& fieldset': {
      borderColor: 'white',
    },
    '&:hover fieldset': {
      borderColor: 'white',
    },
    '&.Mui-focused fieldset': {
      borderColor: 'white',
    },
    '& input': {
      color: 'white',
    },
  },
  '& label.Mui-focused': {
    color: 'white',
  },
  '& .MuiFormLabel-root': {
    color: 'white',
  },
});

export const LoginScreen: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const appContext = useContext(AppContext);

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    RestAPI.authenticate(email, password).then(() => {
      appContext?.setLoggedIn(true);
    });
  };

  return (
    <StyledContainer maxWidth="xs">
      <Typography component="h1" variant="h5">
        Sign in
      </Typography>
      <StyledForm onSubmit={handleSubmit}>
        <StyledTextField
          variant="outlined"
          margin="normal"
          required
          fullWidth
          id="email"
          label="Email Address"
          name="email"
          autoComplete="email"
          autoFocus
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <StyledTextField
          variant="outlined"
          margin="normal"
          required
          fullWidth
          name="password"
          label="Password"
          type="password"
          id="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <StyledSubmitButton
          type="submit"
          fullWidth
          variant="contained"
          color="primary"
        >
          Sign In
        </StyledSubmitButton>
      </StyledForm>
    </StyledContainer>
  );
};
