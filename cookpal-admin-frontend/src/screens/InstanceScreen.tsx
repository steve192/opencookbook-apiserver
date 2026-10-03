import {Box} from '@mui/material';
import {InstanceDetails} from '../components/instance/InstanceDetails';

export const InstanceScreen = () => (
  <Box sx={{overflow: 'auto', minHeight: 0}}>
    <InstanceDetails />
  </Box>
);
