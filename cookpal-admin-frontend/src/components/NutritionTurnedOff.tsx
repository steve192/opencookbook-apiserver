import {Alert} from '@mui/material';

// Shown when nutrition endpoints answer 404.
export const NutritionTurnedOff = () => (
  <Alert severity="info">
    Nutrition estimation is turned off on this instance. Set opencookbook.nutrition.enabled to true
    to import the catalogue.
  </Alert>
);
