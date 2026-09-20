import BlockIcon from '@mui/icons-material/Block';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import {Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, List, ListItem, ListItemText,
  Typography} from '@mui/material';
import {useCallback, useMemo, useState} from 'react';
import {ConfidenceBand, Recipe, RelinkApi, RelinkChange, RelinkDecision, RelinkProposal, RelinkRun,
  RelinkScope} from '../api';
import {CollectionScreen} from '../components/collection/CollectionScreen';
import {BulkAction, FieldDefinition, RowAction} from '../components/collection/types';
import {describeFood, formatConfidence} from '../components/foodLabels';
import {FormFieldDefinition} from '../components/form/types';
import {decisionField, ReviewedRunScreen, RunPreview, RunReviewHeader, runStatusField,
  useRunReview} from '../components/reviewedRuns';
import {StatTiles} from '../components/StatTiles';
import {useAsyncData} from '../hooks/useAsyncData';

const SCOPE_LABELS: Record<RelinkScope, string> = {
  NEVER_MATCHED_OR_UNLINKED: 'Never matched or unlinked',
  AUTOMATIC_BELOW_CONFIDENCE: 'Matched below a confidence',
  ALL_AUTOMATIC: 'Everything matched',
  RETIRED_FOODS: 'Matched to retired foods',
  OLDER_MATCHER: 'Matched by an older matcher',
};

const CHANGE_LABELS: Record<RelinkChange, string> = {
  NEW_LINK: 'New link',
  CHANGED: 'Changed',
  UNLINKED: 'Unlinked',
  CONFIDENCE: 'Confidence',
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
  runStatusField(),
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
  decisionField(),
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

const preview: RunPreview<RelinkRun, PreviewForm> = {
  title: 'Preview a relink run',
  fields: previewFormFields,
  initialValues: {scope: 'NEVER_MATCHED_OR_UNLINKED', belowConfidence: null},
  start: (form) => RelinkApi.preview(form.scope, form.belowConfidence),
};

const revertMessage = (run: RelinkRun) =>
  'Put back the links run ' + run.id + ' changed? Ingredients linked since stay as they are.';

export const RelinkScreen = () => (
  <ReviewedRunScreen
    title="Relink runs"
    api={RelinkApi}
    fields={runFields}
    preview={preview}
    revertMessage={revertMessage}
    renderReview={(runId, onBack) => <RunReview runId={runId} onBack={onBack} />}
  />
);

const RunReview = (props: {runId: number, onBack: () => void}) => {
  const {runId} = props;
  const review = useRunReview(RelinkApi, runId);
  const {proposals, runner, previewed} = review;
  const [showingRecipesOf, setShowingRecipesOf] = useState<RelinkProposal>();

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
  const run = review.run.data;

  return (
    <>
      <CollectionScreen
        title={'Run ' + runId + (run ? ': ' + SCOPE_LABELS[run.scope] : '')}
        fields={proposalFields}
        data={proposals}
        getRowId={(proposal) => proposal.id}
        detailsTitle={(proposal) => proposal.name}
        emptyMessage="The run proposes no change"
        header={
          <RunReviewHeader
            review={review}
            acceptedCount={accepted.length}
            applyMessage={'Link ' + acceptedIngredients + ' ingredient(s) of ' + accepted.length +
              ' accepted proposal(s)? Ingredients changed since the preview are skipped.'}
            unit="ingredient"
            onBack={props.onBack}
            actions={<Button onClick={acceptSilent}>Accept all silent</Button>}
          >
            <StatTiles stats={stats} />
          </RunReviewHeader>
        }
        rowActions={rowActions}
        bulkActions={bulkActions}
      />
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
