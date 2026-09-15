import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import BlockIcon from '@mui/icons-material/Block';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import DeleteSweepIcon from '@mui/icons-material/DeleteSweep';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import RateReviewIcon from '@mui/icons-material/RateReview';
import UndoIcon from '@mui/icons-material/Undo';
import {Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, List, ListItem, ListItemText,
  Stack, Typography} from '@mui/material';
import {useCallback, useMemo, useState} from 'react';
import {toast} from 'react-toastify';
import {ConfidenceBand, errorMessage, Recipe, RelinkApi, RelinkChange, RelinkDecision, RelinkProposal, RelinkRun,
  RelinkRunStatus, RelinkScope} from '../api';
import {CollectionScreen} from '../components/collection/CollectionScreen';
import {BulkAction, FieldDefinition, RowAction} from '../components/collection/types';
import {ConfirmDialog, Confirmation} from '../components/ConfirmDialog';
import {describeFood, formatConfidence} from '../components/foodLabels';
import {EntityFormDialog} from '../components/form/EntityFormDialog';
import {FormFieldDefinition} from '../components/form/types';
import {NutritionTurnedOff} from '../components/NutritionTurnedOff';
import {StatTiles} from '../components/StatTiles';
import {useActionRunner} from '../hooks/useActionRunner';
import {useAsyncData, useCollection} from '../hooks/useAsyncData';

const SCOPE_LABELS: Record<RelinkScope, string> = {
  NEVER_MATCHED_OR_UNLINKED: 'Never matched or unlinked',
  AUTOMATIC_BELOW_CONFIDENCE: 'Matched below a confidence',
  ALL_AUTOMATIC: 'Everything matched',
  RETIRED_FOODS: 'Matched to retired foods',
  OLDER_MATCHER: 'Matched by an older matcher',
};

const STATUS_COLORS: Record<RelinkRunStatus, 'default' | 'primary' | 'success' | 'warning'> = {
  PREVIEWED: 'primary',
  APPLIED: 'success',
  REVERTED: 'warning',
  DISCARDED: 'default',
};

const CHANGE_LABELS: Record<RelinkChange, string> = {
  NEW_LINK: 'New link',
  CHANGED: 'Changed',
  UNLINKED: 'Unlinked',
  CONFIDENCE: 'Confidence',
};

const DECISION_COLORS: Record<RelinkDecision, 'default' | 'success' | 'error'> = {
  PENDING: 'default',
  ACCEPTED: 'success',
  REJECTED: 'error',
};

const BAND_COLORS: Record<ConfidenceBand, 'success' | 'warning' | 'default'> = {
  SILENT: 'success',
  UNCERTAIN: 'warning',
  NONE: 'default',
};

const runFields: FieldDefinition<RelinkRun>[] = [
  {key: 'createdOn', label: 'Started', kind: 'datetime', width: 180},
  {key: 'scope', label: 'Scope', width: 220, value: (run) => SCOPE_LABELS[run.scope] +
    (run.belowConfidence === null ? '' : ' ' + formatConfidence(run.belowConfidence))},
  {
    key: 'status',
    label: 'Status',
    width: 120,
    render: (run) => <Chip size="small" color={STATUS_COLORS[run.status]} label={run.status} />,
  },
  {key: 'startedByEmailAddress', label: 'Started by', width: 200},
  {key: 'proposalCount', label: 'Proposals', kind: 'number', width: 110},
  {key: 'ingredientCount', label: 'Ingredients', kind: 'number', width: 110, importance: 'secondary'},
  {key: 'appliedCount', label: 'Applied', kind: 'number', width: 100, importance: 'secondary'},
  {key: 'skippedCount', label: 'Skipped', kind: 'number', width: 100, importance: 'secondary'},
  {key: 'unchangedCount', label: 'Unchanged', kind: 'number', importance: 'reference'},
  {key: 'datasetLabel', label: 'Dataset', importance: 'reference'},
  {key: 'matcherVersion', label: 'Matcher version', kind: 'number', importance: 'reference'},
  {key: 'appliedAt', label: 'Applied at', kind: 'datetime', importance: 'reference'},
  {key: 'revertedAt', label: 'Reverted at', kind: 'datetime', importance: 'reference'},
  {key: 'id', label: 'Id', kind: 'number', importance: 'reference'},
];

const proposalFields: FieldDefinition<RelinkProposal>[] = [
  {key: 'name', label: 'Name', width: 200},
  {key: 'oldFood', label: 'Now', width: 220, value: (proposal) => describeFood(proposal.oldFood)},
  {key: 'newFood', label: 'Proposed', width: 220, value: (proposal) => describeFood(proposal.newFood)},
  {key: 'newConfidence', label: 'Confidence', kind: 'number', width: 120,
    render: (proposal) => proposal.band ?
      <Chip size="small" color={BAND_COLORS[proposal.band]} label={formatConfidence(proposal.newConfidence)} /> :
      '-'},
  {key: 'decision', label: 'Decision', width: 120,
    render: (proposal) => <Chip size="small" color={DECISION_COLORS[proposal.decision]} label={proposal.decision} />},
  {key: 'change', label: 'Change', width: 120, importance: 'secondary', value: (proposal) => CHANGE_LABELS[proposal.change]},
  {key: 'ingredientCount', label: 'Ingredients', kind: 'number', width: 110, importance: 'secondary'},
  {key: 'language', label: 'Language', width: 100, importance: 'secondary'},
  {key: 'band', label: 'Band', importance: 'reference'},
  {key: 'id', label: 'Id', kind: 'number', importance: 'reference'},
];

interface PreviewForm {
  scope: RelinkScope;
  belowConfidence: number | null;
}

const previewFormFields: FormFieldDefinition<PreviewForm>[] = [
  {
    name: 'scope',
    label: 'Which ingredients',
    type: 'select',
    required: true,
    options: Object.entries(SCOPE_LABELS).map(([value, label]) => ({value, label})),
    helperText: 'Ingredients their owner or an administrator linked are never included',
  },
  {
    name: 'belowConfidence',
    label: 'Below confidence (0 to 1)',
    type: 'number',
    helperText: 'Only for "Matched below a confidence"',
  },
];

export const RelinkScreen = () => {
  const [reviewing, setReviewing] = useState<number>();
  return reviewing === undefined ?
    <RunHistory onReview={setReviewing} /> :
    <RunReview runId={reviewing} onBack={() => setReviewing(undefined)} />;
};

const RunHistory = (props: {onReview: (runId: number) => void}) => {
  const runs = useCollection(RelinkApi.getRuns);
  const runner = useActionRunner(runs.reload);
  const [previewing, setPreviewing] = useState(false);

  const preview = async (form: PreviewForm) => {
    try {
      const run = await RelinkApi.preview(form.scope, form.belowConfidence);
      setPreviewing(false);
      props.onReview(run.id);
    } catch (cause) {
      toast.error('Preview failed: ' + errorMessage(cause));
    }
  };

  const {onReview} = props;
  const rowActions = useMemo<RowAction<RelinkRun>[]>(() => [
    {label: 'Review', icon: <RateReviewIcon fontSize="small" />, onRun: (run) => onReview(run.id)},
    {
      label: 'Revert',
      icon: <UndoIcon fontSize="small" />,
      color: 'error',
      hidden: (run) => run.status !== 'APPLIED',
      confirm: (run) => 'Put back the links run ' + run.id + ' changed? Ingredients linked since stay as they are.',
      onRun: (run) => runner.run('Reverted run ' + run.id, () => RelinkApi.revert(run.id)),
    },
    {
      label: 'Discard',
      icon: <DeleteSweepIcon fontSize="small" />,
      hidden: (run) => run.status !== 'PREVIEWED',
      onRun: (run) => runner.run('Discarded run ' + run.id, () => RelinkApi.discard(run.id)),
    },
  ], [runner, onReview]);

  if (runs.errorStatus === 404) {
    return <NutritionTurnedOff />;
  }

  return (
    <>
      <CollectionScreen
        title="Relink runs"
        fields={runFields}
        data={runs}
        getRowId={(run) => run.id}
        detailsTitle={(run) => 'Run ' + run.id}
        emptyMessage="No run yet"
        onCreate={() => setPreviewing(true)}
        createLabel="Preview a run"
        rowActions={rowActions}
      />
      <EntityFormDialog<PreviewForm>
        open={previewing}
        title="Preview a relink run"
        fields={previewFormFields}
        initialValues={{scope: 'NEVER_MATCHED_OR_UNLINKED', belowConfidence: null}}
        submitLabel="Preview"
        onClose={() => setPreviewing(false)}
        onSubmit={preview}
      />
    </>
  );
};

const RunReview = (props: {runId: number, onBack: () => void}) => {
  const {runId, onBack} = props;
  const loadRun = useCallback(() => RelinkApi.getRun(runId), [runId]);
  const loadProposals = useCallback(() => RelinkApi.getProposals(runId), [runId]);
  const run = useAsyncData<RelinkRun | undefined>(loadRun, undefined);
  const proposals = useCollection(loadProposals);
  const reload = useCallback(() => {
    run.reload();
    proposals.reload();
  }, [run.reload, proposals.reload]);
  const runner = useActionRunner(reload);
  const [confirmation, setConfirmation] = useState<Confirmation>();
  const [showingRecipesOf, setShowingRecipesOf] = useState<RelinkProposal>();

  const previewed = run.data?.status === 'PREVIEWED';

  const stats = useMemo(() => {
    const count = (predicate: (proposal: RelinkProposal) => boolean) => proposals.data.filter(predicate).length;
    return [
      {label: 'New links', value: count((proposal) => proposal.change === 'NEW_LINK')},
      {label: 'Changed', value: count((proposal) => proposal.change === 'CHANGED')},
      {label: 'Unlinked', value: count((proposal) => proposal.change === 'UNLINKED'), color: 'warning.main'},
      {label: 'Confidence', value: count((proposal) => proposal.change === 'CONFIDENCE')},
      {label: 'Silent', value: count((proposal) => proposal.band === 'SILENT')},
      {label: 'Uncertain', value: count((proposal) => proposal.band === 'UNCERTAIN')},
      {label: 'Pending', value: count((proposal) => proposal.decision === 'PENDING')},
      {label: 'Accepted', value: count((proposal) => proposal.decision === 'ACCEPTED'), color: 'success.main'},
    ];
  }, [proposals.data]);

  const decide = useCallback((selected: RelinkProposal[], decision: RelinkDecision, remember: boolean) =>
    runner.run(decision === 'ACCEPTED' ? 'Accepted ' + selected.length : 'Rejected ' + selected.length,
        () => RelinkApi.decide(runId, selected.map((proposal) => proposal.id), decision, remember)),
  [runner, runId]);

  const rowActions = useMemo<RowAction<RelinkProposal>[]>(() => [
    {label: 'Affected recipes', icon: <MenuBookIcon fontSize="small" />, onRun: setShowingRecipesOf},
    {label: 'Accept', icon: <CheckIcon fontSize="small" />, hidden: () => !previewed,
      onRun: (proposal) => decide([proposal], 'ACCEPTED', false)},
    {label: 'Reject', icon: <CloseIcon fontSize="small" />, hidden: () => !previewed,
      onRun: (proposal) => decide([proposal], 'REJECTED', false)},
  ], [decide, previewed]);

  const bulkActions = useMemo<BulkAction<RelinkProposal>[]>(() => previewed ? [
    {label: 'Accept', icon: <CheckIcon fontSize="small" />, onRun: (selected) => decide(selected, 'ACCEPTED', false)},
    {label: 'Reject for this run', icon: <CloseIcon fontSize="small" />,
      onRun: (selected) => decide(selected, 'REJECTED', false)},
    {
      label: 'Reject and remember',
      icon: <BlockIcon fontSize="small" />,
      confirm: (selected) => 'Reject ' + selected.length + ' proposal(s) and never propose these foods for these ' +
        'names again? The rules are listed under Name rules.',
      onRun: (selected) => decide(selected, 'REJECTED', true),
    },
  ] : [], [decide, previewed]);

  const acceptSilent = () => {
    const silent = proposals.data.filter((proposal) => proposal.band === 'SILENT' && proposal.decision === 'PENDING');
    if (silent.length > 0) {
      decide(silent, 'ACCEPTED', false);
    }
  };

  const accepted = proposals.data.filter((proposal) => proposal.decision === 'ACCEPTED');
  const acceptedIngredients = accepted.reduce((sum, proposal) => sum + proposal.ingredientCount, 0);

  // An applied run can't be decided on anymore, so applying nothing would waste it.
  const confirmApply = () => setConfirmation({
    title: 'Apply run ' + runId,
    message: 'Link ' + acceptedIngredients + ' ingredient(s) of ' + accepted.length + ' accepted proposal(s)? ' +
      (proposals.data.length - accepted.length) + ' proposal(s) not accepted stay as they are and can no longer be ' +
      'applied from this run. Ingredients changed since the preview are skipped. The run can be reverted afterwards.',
    confirmLabel: 'Apply',
    onConfirm: () => runner.run('Applied run ' + runId, () => RelinkApi.apply(runId)),
  });

  return (
    <>
      <CollectionScreen
        title={'Run ' + runId + (run.data ? ': ' + SCOPE_LABELS[run.data.scope] : '')}
        fields={proposalFields}
        data={proposals}
        getRowId={(proposal) => proposal.id}
        detailsTitle={(proposal) => proposal.name}
        emptyMessage="The run proposes no change"
        header={
          <>
            <Stack direction="row" spacing={1} alignItems="center" sx={{mb: 2}} flexWrap="wrap" useFlexGap>
              <Button startIcon={<ArrowBackIcon />} onClick={onBack}>All runs</Button>
              {run.data && <Chip color={STATUS_COLORS[run.data.status]} label={run.data.status} />}
              <Box sx={{flexGrow: 1}} />
              {previewed && (
                <>
                  <Button onClick={acceptSilent}>Accept all silent</Button>
                  <Button onClick={() => runner.run('Discarded run ' + runId, () => RelinkApi.discard(runId))}>
                    Discard
                  </Button>
                  <Button variant="contained" onClick={confirmApply} disabled={accepted.length === 0}>
                    Apply {accepted.length} accepted
                  </Button>
                </>
              )}
            </Stack>
            {run.data && run.data.status !== 'PREVIEWED' && (
              <Typography variant="body2" color="text.secondary" sx={{mb: 1}}>
                Applied to {run.data.appliedCount} ingredient(s), {run.data.skippedCount} skipped as changed since
                the preview.
              </Typography>
            )}
            <StatTiles stats={stats} />
          </>
        }
        rowActions={rowActions}
        bulkActions={bulkActions}
      />
      <ConfirmDialog confirmation={confirmation} onClose={() => setConfirmation(undefined)} />
      {showingRecipesOf && (
        <AffectedRecipesDialog runId={runId} proposal={showingRecipesOf} onClose={() => setShowingRecipesOf(undefined)} />
      )}
    </>
  );
};

const AffectedRecipesDialog = (props: {runId: number, proposal: RelinkProposal, onClose: () => void}) => {
  const {runId, proposal} = props;
  const load = useCallback(() => RelinkApi.getSampleRecipes(runId, proposal.id), [runId, proposal.id]);
  const recipes = useAsyncData<Recipe[]>(load, []);

  return (
    <Dialog open onClose={props.onClose} fullWidth maxWidth="sm">
      <DialogTitle>Recipes using {proposal.name}</DialogTitle>
      <DialogContent dividers>
        {recipes.error && <Typography color="error">{recipes.error}</Typography>}
        <List dense>
          {recipes.data.map((recipe) => (
            <ListItem key={recipe.id} disableGutters>
              <ListItemText
                primary={recipe.title + ' (' + (recipe.ownerEmailAddress ?? 'no owner') + ')'}
                secondary={recipe.ingredients.join(', ')}
              />
            </ListItem>
          ))}
        </List>
      </DialogContent>
      <DialogActions>
        <Button onClick={props.onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};
