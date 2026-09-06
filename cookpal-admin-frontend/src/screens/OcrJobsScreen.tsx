import DeleteIcon from '@mui/icons-material/Delete';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import {Alert, Box, Button, Chip, LinearProgress, Paper, Stack, Typography} from '@mui/material';
import {useCallback, useMemo} from 'react';
import {MlApi, MlJob, MlJobStatus, MlQuota, MlStatistics} from '../api';
import {CollectionScreen} from '../components/collection/CollectionScreen';
import {BulkAction, FieldDefinition, RowAction} from '../components/collection/types';
import {StatTiles} from '../components/StatTiles';
import {useActionRunner} from '../hooks/useActionRunner';
import {AsyncData, useAsyncData, useCollection} from '../hooks/useAsyncData';

const NO_STATISTICS: MlStatistics = {
  available: false,
  totalJobs: 0,
  jobsByStatus: {} as Record<MlJobStatus, number>,
  recentFailures: [],
};

const NO_QUOTA: MlQuota = {dailyLimit: 0, users: []};

const STATUS_COLOURS: Record<MlJobStatus, 'default' | 'info' | 'success' | 'error' | 'warning'> = {
  QUEUED: 'info',
  PROCESSING: 'info',
  COMPLETED: 'success',
  FAILED: 'error',
  CANCELLED: 'warning',
};

const fields: FieldDefinition<MlJob>[] = [
  {key: 'ownerEmailAddress', label: 'Scanned by', width: 220},
  {
    key: 'status',
    label: 'State',
    width: 130,
    render: (job) => <Chip size="small" color={STATUS_COLOURS[job.status]} label={job.status} />,
  },
  {key: 'createdOn', label: 'Started', kind: 'datetime', width: 180},
  {key: 'errorMessage', label: 'Problem', width: 240, importance: 'secondary'},
  {key: 'jobType', label: 'Kind', width: 140, importance: 'secondary'},
  {key: 'queuePosition', label: 'In queue', kind: 'number', width: 110, importance: 'secondary'},
  {
    key: 'countsTowardsQuota',
    label: 'Counts',
    kind: 'boolean',
    width: 100,
    importance: 'secondary',
  },
  {key: 'finishedAt', label: 'Finished', kind: 'datetime', width: 180, importance: 'secondary'},
  {key: 'id', label: 'Scan id', importance: 'reference'},
  {key: 'remoteJobId', label: 'Subsystem id', importance: 'reference'},
  {key: 'errorCode', label: 'Error code', importance: 'reference'},
  {key: 'errorRetryable', label: 'Worth retrying', kind: 'boolean', importance: 'reference'},
  {key: 'hasResult', label: 'Has a result', kind: 'boolean', importance: 'reference'},
];

export const OcrJobsScreen = () => {
  const jobs = useCollection(MlApi.getJobs);
  const statistics = useAsyncData(MlApi.statistics, NO_STATISTICS);
  const quota = useAsyncData(MlApi.quota, NO_QUOTA);

  const reload = useCallback(() => {
    jobs.reload();
    statistics.reload();
    quota.reload();
  }, [jobs.reload, statistics.reload, quota.reload]);
  const runner = useActionRunner(reload);

  const stats = useMemo(() => [
    {
      label: 'Subsystem',
      value: statistics.data.available ? 'Reachable' : 'Unreachable',
      color: statistics.data.available ? 'success.main' : 'error.main',
    },
    {label: 'Scans', value: statistics.data.totalJobs},
    {label: 'Running', value: (statistics.data.jobsByStatus.QUEUED ?? 0) +
      (statistics.data.jobsByStatus.PROCESSING ?? 0)},
    {label: 'Failed', value: statistics.data.jobsByStatus.FAILED ?? 0, color: 'error.main'},
  ], [statistics.data]);

  // Memoised: the table rebuilds every column when these change identity.
  const rowActions = useMemo<RowAction<MlJob>[]>(() => [
    {
      label: 'Reset',
      icon: <RestartAltIcon fontSize="small" />,
      confirm: (job) => 'Reset this scan? It is stopped if it is still running, and it stops ' +
        'counting against ' + (job.ownerEmailAddress ?? 'its owner') + '\'s allowance.',
      onRun: (job) => runner.run('Reset the scan', () => MlApi.resetJob(job.id)),
    },
    {
      label: 'Delete',
      icon: <DeleteIcon fontSize="small" />,
      color: 'error',
      confirm: () => 'Delete this scan and its result?',
      onRun: (job) => runner.run('Deleted the scan', () => MlApi.deleteJob(job.id)),
    },
  ], [runner]);

  const bulkActions = useMemo<BulkAction<MlJob>[]>(() => [
    {
      label: 'Reset',
      icon: <RestartAltIcon fontSize="small" />,
      confirm: (selected) => 'Reset ' + selected.length + ' scan(s)?',
      onRun: (selected) => runner.runAll('Reset', selected, (job) => MlApi.resetJob(job.id)),
    },
    {
      label: 'Delete',
      icon: <DeleteIcon fontSize="small" />,
      confirm: (selected) => 'Delete ' + selected.length + ' scan(s) and their results?',
      onRun: (selected) => runner.runAll('Deleted', selected, (job) => MlApi.deleteJob(job.id)),
    },
  ], [runner]);

  if (jobs.errorStatus === 404) {
    return (
      <Alert severity="info">
        This instance has no machine learning subsystem configured, so nothing is being scanned.
      </Alert>
    );
  }

  return (
    <CollectionScreen
      title="Recipe scans"
      fields={fields}
      data={jobs}
      getRowId={(job) => job.id}
      detailsTitle={(job) => 'Scan by ' + (job.ownerEmailAddress ?? 'somebody')}
      emptyMessage="Nobody has scanned a recipe yet"
      header={
        <>
          <StatTiles stats={stats} />
          <QuotaPanel
            quota={quota}
            onReset={(userId, emailAddress) => runner.run(
                'Gave ' + emailAddress + ' their allowance back',
                () => MlApi.resetQuota(userId),
            )}
          />
        </>
      }
      rowActions={rowActions}
      bulkActions={bulkActions}
    />
  );
};

const QuotaPanel = (props: {
  quota: AsyncData<MlQuota>,
  onReset: (userId: number, emailAddress: string) => void,
}) => {
  const {dailyLimit, users} = props.quota.data;

  return (
    <Paper variant="outlined" sx={{p: 2, mb: 2}}>
      <Stack direction="row" alignItems="center" spacing={1} sx={{mb: 1}}>
        <Typography variant="subtitle1">Today&apos;s allowance</Typography>
        <Chip size="small" label={dailyLimit > 0 ? dailyLimit + ' scans per person' : 'No limit'} />
      </Stack>
      {props.quota.loading && <LinearProgress />}
      {users.length === 0 ? (
        <Typography variant="body2" color="text.secondary">Nobody has scanned anything today</Typography>
      ) : (
        <Stack spacing={1}>
          {users.map((usage) => (
            <Stack
              key={usage.userId}
              direction={{xs: 'column', sm: 'row'}}
              spacing={1}
              alignItems={{xs: 'stretch', sm: 'center'}}
            >
              <Typography variant="body2" sx={{flexGrow: 1, wordBreak: 'break-word'}}>
                {usage.emailAddress}
              </Typography>
              <Box sx={{minWidth: 140}}>
                <Typography variant="body2" color={usage.exhausted ? 'error.main' : undefined}>
                  {usage.used}{dailyLimit > 0 ? ' of ' + dailyLimit : ''} used
                </Typography>
                {dailyLimit > 0 && (
                  <LinearProgress
                    variant="determinate"
                    color={usage.exhausted ? 'error' : 'primary'}
                    value={Math.min(100, (usage.used / dailyLimit) * 100)}
                  />
                )}
              </Box>
              <Button
                size="small"
                startIcon={<RestartAltIcon />}
                onClick={() => props.onReset(usage.userId, usage.emailAddress)}
              >
                Give back
              </Button>
            </Stack>
          ))}
        </Stack>
      )}
    </Paper>
  );
};
