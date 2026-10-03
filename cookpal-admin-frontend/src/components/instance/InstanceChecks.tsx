import RefreshIcon from '@mui/icons-material/Refresh';
import SendIcon from '@mui/icons-material/Send';
import {Alert, Box, Button, Chip, LinearProgress, Paper, Stack, Typography} from '@mui/material';
import {InstanceApi, InstanceCheck, InstanceCheckKind} from '../../api';
import {useActionRunner} from '../../hooks/useActionRunner';
import {useCollection} from '../../hooks/useAsyncData';

const CHECK_LABELS: Record<InstanceCheckKind, string> = {
  MAIL: 'Mail server',
  RECIPE_IMPORT: 'Recipe import service',
  RECIPE_SCAN: 'Recipe scan service',
};

const STATUS_CHIPS: Record<InstanceCheck['status'], {label: string, color: 'success' | 'error' | 'default'}> = {
  OK: {label: 'Working', color: 'success'},
  FAILED: {label: 'Failing', color: 'error'},
  NOT_CONFIGURED: {label: 'Not configured', color: 'default'},
};

const doNothing = () => undefined;

// Live checks run when this is shown and again on request: they reach out to other services and can
// take a few seconds.
export const InstanceChecks = (props: {mailConfigured: boolean}) => {
  const checks = useCollection(InstanceApi.checks);
  // A test mail says nothing new about the other checks, so they stay as they are.
  const runner = useActionRunner(doNothing);

  return (
    <Paper variant="outlined" sx={{p: 2}}>
      <Stack direction="row" sx={{alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 1}}>
        <Typography variant="overline" color="text.secondary">Live checks</Typography>
        <Stack direction="row" spacing={1}>
          <Button size="small" startIcon={<RefreshIcon />} onClick={checks.reload} disabled={checks.loading}>
            Run again
          </Button>
          <Button
            size="small"
            startIcon={<SendIcon />}
            disabled={!props.mailConfigured}
            onClick={() => runner.run('Sent a test mail to you', InstanceApi.sendTestMail)}
          >
            Send test mail
          </Button>
        </Stack>
      </Stack>
      {checks.error && <Alert severity="error" sx={{mt: 1}}>{checks.error}</Alert>}
      {checks.loading && <LinearProgress sx={{mt: 1}} />}
      <Stack spacing={1} sx={{mt: 1}}>
        {checks.data.map((entry) => (
          <Box key={entry.check} sx={{display: 'flex', flexWrap: 'wrap', columnGap: 2, alignItems: 'center'}}>
            <Typography variant="body2" sx={{width: 190, flexShrink: 0}}>
              {CHECK_LABELS[entry.check]}
            </Typography>
            <Chip size="small" {...STATUS_CHIPS[entry.status]} />
            {entry.detail && (
              <Typography variant="caption" color="text.secondary" sx={{overflowWrap: 'anywhere'}}>
                {entry.detail}
              </Typography>
            )}
          </Box>
        ))}
      </Stack>
    </Paper>
  );
};
