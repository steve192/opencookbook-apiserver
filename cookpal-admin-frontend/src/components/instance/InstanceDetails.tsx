import {Stack} from '@mui/material';
import {InstanceApi, InstanceOverview as Overview} from '../../api';
import {useAsyncData} from '../../hooks/useAsyncData';
import {InstanceChecks} from './InstanceChecks';
import {InstanceOverview} from './InstanceOverview';
import {RegistrationSettings} from './RegistrationSettings';

const loadOverview = () => InstanceApi.overview();

// Shared by the second step of the setup and the Instance screen.
export const InstanceDetails = () => {
  const overview = useAsyncData<Overview | undefined>(loadOverview, undefined);

  return (
    <Stack spacing={1.5}>
      <InstanceOverview overview={overview.data} loading={overview.loading} error={overview.error} />
      <InstanceChecks mailConfigured={overview.data?.mail.configured ?? false} />
      <RegistrationSettings />
    </Stack>
  );
};
