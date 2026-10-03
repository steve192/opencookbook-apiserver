import {Box, Container, Paper} from '@mui/material';
import {ReactNode} from 'react';
import {NAVIGATION_BACKGROUND} from '../theme';

// The page around the forms shown before the panel itself: sign in and setup.
export const CenteredPage = (props: {width: 'xs' | 'md', children: ReactNode}) => (
  <Box sx={{
    minHeight: '100dvh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    p: 2,
    backgroundColor: NAVIGATION_BACKGROUND,
  }}>
    <Container maxWidth={props.width} disableGutters>
      <Paper sx={{p: {xs: 3, sm: 4}, width: '100%'}} elevation={6}>{props.children}</Paper>
    </Container>
  </Box>
);
