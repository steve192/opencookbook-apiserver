import {Box, Paper, Typography} from '@mui/material';

export interface Stat {
  label: string;
  value?: number | string;
  color?: string;
}

export const StatTiles = (props: {stats: Stat[]}) => (
  <Box sx={{
    display: 'grid',
    gridTemplateColumns: {xs: 'repeat(2, 1fr)', sm: 'repeat(auto-fit, minmax(160px, 1fr))'},
    gap: 1.5,
    mb: 2,
  }}>
    {props.stats.map((stat) => (
      <Paper key={stat.label} variant="outlined" sx={{px: 2, py: 1.5}}>
        <Typography variant="h5" color={stat.color}>{stat.value ?? '-'}</Typography>
        <Typography variant="body2" color="text.secondary" noWrap>{stat.label}</Typography>
      </Paper>
    ))}
  </Box>
);
