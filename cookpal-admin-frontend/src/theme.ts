import {createTheme} from '@mui/material';

export const NAVIGATION_BACKGROUND = '#09212E';

export const adminTheme = createTheme({
  palette: {
    mode: 'light',
    primary: {main: '#12556e'},
    background: {default: '#f4f6f8', paper: '#ffffff'},
  },
  shape: {borderRadius: 8},
  typography: {
    fontFamily: 'Roboto, system-ui, Avenir, Helvetica, Arial, sans-serif',
    h6: {fontSize: '1.1rem'},
  },
  components: {
    MuiButton: {defaultProps: {disableElevation: true}, styleOverrides: {root: {textTransform: 'none'}}},
    MuiTooltip: {defaultProps: {enterTouchDelay: 0}},
  },
});
