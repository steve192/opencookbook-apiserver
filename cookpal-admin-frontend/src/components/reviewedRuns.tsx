import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import DeleteSweepIcon from '@mui/icons-material/DeleteSweep';
import RateReviewIcon from '@mui/icons-material/RateReview';
import UndoIcon from '@mui/icons-material/Undo';
import {Box, Button, Chip, Stack, Typography} from '@mui/material';
import {ReactNode, useCallback, useMemo, useState} from 'react';
import {toast} from 'react-toastify';
import {errorMessage, ReviewDecision, ReviewedRun, ReviewedRunStatus} from '../api';
import {ActionRunner, useActionRunner} from '../hooks/useActionRunner';
import {AsyncData, useAsyncData, useCollection} from '../hooks/useAsyncData';
import {CollectionScreen} from './collection/CollectionScreen';
import {FieldDefinition, RowAction} from './collection/types';
import {ConfirmDialog, Confirmation} from './ConfirmDialog';
import {EntityFormDialog} from './form/EntityFormDialog';
import {FormFieldDefinition, FormValues} from './form/types';
import {NutritionTurnedOff} from './NutritionTurnedOff';

// Shared by every run that changes many rows under review: preview, decide, apply, revert.

export interface ReviewedRunApi<Run extends ReviewedRun, Proposal> {
  getRuns: () => Promise<Run[]>;
  getRun: (id: number) => Promise<Run>;
  getProposals: (id: number) => Promise<Proposal[]>;
  apply: (id: number) => Promise<Run>;
  revert: (id: number) => Promise<Run>;
  discard: (id: number) => Promise<Run>;
}

const STATUS_COLORS: Record<ReviewedRunStatus, 'default' | 'primary' | 'success' | 'warning'> = {
  PREVIEWED: 'primary',
  APPLIED: 'success',
  REVERTED: 'warning',
  DISCARDED: 'default',
};

const DECISION_COLORS: Record<ReviewDecision, 'default' | 'success' | 'error'> = {
  PENDING: 'default',
  ACCEPTED: 'success',
  REJECTED: 'error',
  SKIPPED: 'default',
};

const StatusChip = (props: {status: ReviewedRunStatus, small?: boolean}) =>
  <Chip size={props.small ? 'small' : 'medium'} color={STATUS_COLORS[props.status]} label={props.status} />;

export const runStatusField = <Run extends ReviewedRun>(): FieldDefinition<Run> =>
  ({key: 'status', label: 'Status', width: 120, render: (run) => <StatusChip status={run.status} small />});

export const decisionField = <Proposal extends {decision: ReviewDecision}>(): FieldDefinition<Proposal> =>
  ({key: 'decision', label: 'Decision', width: 120,
    render: (proposal) => <Chip size="small" color={DECISION_COLORS[proposal.decision]} label={proposal.decision} />});

export interface RunPreview<Run, Form extends FormValues<Form>> {
  title: string;
  fields: FormFieldDefinition<Form>[];
  initialValues: Form;
  start: (form: Form) => Promise<Run>;
}

interface RunHistoryProps<Run extends ReviewedRun, Proposal, Form extends FormValues<Form>> {
  title: string;
  api: ReviewedRunApi<Run, Proposal>;
  fields: FieldDefinition<Run>[];
  preview: RunPreview<Run, Form>;
  revertMessage: (run: Run) => string;
}

// The run history, and the review of one run once it is picked or previewed.
export function ReviewedRunScreen<Run extends ReviewedRun, Proposal, Form extends FormValues<Form>>(
    props: RunHistoryProps<Run, Proposal, Form> & {renderReview: (runId: number, onBack: () => void) => ReactNode}) {
  const [reviewing, setReviewing] = useState<number>();
  return reviewing === undefined ?
    <RunHistory {...props} onReview={setReviewing} /> :
    <>{props.renderReview(reviewing, () => setReviewing(undefined))}</>;
}

function RunHistory<Run extends ReviewedRun, Proposal, Form extends FormValues<Form>>(
    props: RunHistoryProps<Run, Proposal, Form> & {onReview: (runId: number) => void}) {
  const {api, onReview, revertMessage, preview} = props;
  const runs = useCollection(api.getRuns);
  const runner = useActionRunner(runs.reload);
  const [previewing, setPreviewing] = useState(false);

  const startPreview = async (form: Form) => {
    try {
      const run = await preview.start(form);
      setPreviewing(false);
      onReview(run.id);
    } catch (cause) {
      toast.error('Preview failed: ' + errorMessage(cause));
    }
  };

  const rowActions = useMemo<RowAction<Run>[]>(() => [
    {label: 'Review', icon: <RateReviewIcon fontSize="small" />, onRun: (run) => onReview(run.id)},
    {
      label: 'Revert',
      icon: <UndoIcon fontSize="small" />,
      color: 'error',
      hidden: (run) => run.status !== 'APPLIED',
      confirm: revertMessage,
      onRun: (run) => runner.run('Reverted run ' + run.id, () => api.revert(run.id)),
    },
    {
      label: 'Discard',
      icon: <DeleteSweepIcon fontSize="small" />,
      hidden: (run) => run.status !== 'PREVIEWED',
      onRun: (run) => runner.run('Discarded run ' + run.id, () => api.discard(run.id)),
    },
  ], [api, runner, onReview, revertMessage]);

  if (runs.errorStatus === 404) {
    return <NutritionTurnedOff />;
  }

  return (
    <>
      <CollectionScreen
        title={props.title}
        fields={props.fields}
        data={runs}
        getRowId={(run) => run.id}
        detailsTitle={(run) => 'Run ' + run.id}
        emptyMessage="No run yet"
        onCreate={() => setPreviewing(true)}
        createLabel="Preview a run"
        rowActions={rowActions}
      />
      <EntityFormDialog<Form>
        open={previewing}
        title={preview.title}
        fields={preview.fields}
        initialValues={preview.initialValues}
        submitLabel="Preview"
        onClose={() => setPreviewing(false)}
        onSubmit={startPreview}
      />
    </>
  );
}

export interface RunReview<Run extends ReviewedRun, Proposal> {
  api: ReviewedRunApi<Run, Proposal>;
  runId: number;
  run: AsyncData<Run | undefined>;
  proposals: AsyncData<Proposal[]>;
  /** Reloads the run and its proposals after every action. */
  runner: ActionRunner;
  /** Only a previewed run can still be decided on. */
  previewed: boolean;
}

export function useRunReview<Run extends ReviewedRun, Proposal>(
    api: ReviewedRunApi<Run, Proposal>, runId: number): RunReview<Run, Proposal> {
  const loadRun = useCallback(() => api.getRun(runId), [api, runId]);
  const loadProposals = useCallback(() => api.getProposals(runId), [api, runId]);
  const run = useAsyncData<Run | undefined>(loadRun, undefined);
  const proposals = useCollection(loadProposals);
  const {reload: reloadRun} = run;
  const {reload: reloadProposals} = proposals;
  const reload = useCallback(() => {
    reloadRun();
    reloadProposals();
  }, [reloadRun, reloadProposals]);
  const runner = useActionRunner(reload);
  return {api, runId, run, proposals, runner, previewed: run.data?.status === 'PREVIEWED'};
}

export function RunReviewHeader<Run extends ReviewedRun, Proposal>(props: {
  review: RunReview<Run, Proposal>,
  acceptedCount: number,
  // What applying the accepted proposals will do, confirmed before it is done
  applyMessage: string,
  // What the run changes, for the summary once it is applied
  unit: string,
  onBack: () => void,
  actions?: ReactNode,
  children?: ReactNode,
}) {
  const {review} = props;
  const {api, runId} = review;
  const run = review.run.data;
  const [confirmation, setConfirmation] = useState<Confirmation>();

  // An applied run can't be decided on anymore, so applying is confirmed first.
  const confirmApply = () => setConfirmation({
    title: 'Apply run ' + runId,
    message: props.applyMessage + ' Proposals not accepted can no longer be applied from this run. The run can be ' +
      'reverted afterwards.',
    confirmLabel: 'Apply',
    onConfirm: () => review.runner.run('Applied run ' + runId, () => api.apply(runId)),
  });

  return (
    <>
      <Stack direction="row" spacing={1} alignItems="center" sx={{mb: 2}} flexWrap="wrap" useFlexGap>
        <Button startIcon={<ArrowBackIcon />} onClick={props.onBack}>All runs</Button>
        {run && <StatusChip status={run.status} />}
        <Box sx={{flexGrow: 1}} />
        {review.previewed && (
          <>
            {props.actions}
            <Button onClick={() => review.runner.run('Discarded run ' + runId, () => api.discard(runId))}>
              Discard
            </Button>
            <Button variant="contained" onClick={confirmApply} disabled={props.acceptedCount === 0}>
              Apply {props.acceptedCount} accepted
            </Button>
          </>
        )}
      </Stack>
      {run?.appliedAt && (
        <Typography variant="body2" color="text.secondary" sx={{mb: 1}}>
          Applied to {run.appliedCount} {props.unit}(s), {run.skippedCount} skipped as changed since the preview.
        </Typography>
      )}
      {props.children}
      <ConfirmDialog confirmation={confirmation} onClose={() => setConfirmation(undefined)} />
    </>
  );
}
