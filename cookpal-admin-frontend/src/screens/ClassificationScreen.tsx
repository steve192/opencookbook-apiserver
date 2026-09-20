import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import {Button, Chip, Typography} from '@mui/material';
import {useCallback, useMemo} from 'react';
import {ClassificationApi, ClassificationDecision, ClassificationProposal, ClassificationRun,
  ClassificationScope} from '../api';
import {CollectionScreen} from '../components/collection/CollectionScreen';
import {BulkAction, FieldDefinition, RowAction} from '../components/collection/types';
import {DIET_COLORS, DIET_LABELS, DIETS, isDiet} from '../components/diets';
import {FormFieldDefinition} from '../components/form/types';
import {decisionField, ReviewedRunScreen, RunPreview, RunReviewHeader, runStatusField,
  useRunReview} from '../components/reviewedRuns';
import {StatTiles} from '../components/StatTiles';

const SCOPE_LABELS: Record<ClassificationScope, string> = {
  NEVER_CLASSIFIED: 'Never classified',
  DERIVED_ONLY: 'Everything derived before',
};

// Values arrive encoded by the run's kind; diets get their colour, anything else a plain chip.
const classified = (value: string | null) =>
  value ? <Chip size="small" color={isDiet(value) ? DIET_COLORS[value] : 'default'} label={value} /> : <span>-</span>;

const runFields: FieldDefinition<ClassificationRun>[] = [
  {key: 'createdOn', label: 'Started', kind: 'datetime', width: 180},
  {key: 'scope', label: 'Which recipes', width: 220, value: (run) => SCOPE_LABELS[run.scope]},
  runStatusField(),
  {key: 'startedByEmailAddress', label: 'Started by', width: 200},
  {key: 'proposalCount', label: 'Decidable', kind: 'number', width: 110},
  {key: 'unreadableCount', label: 'Unreadable', kind: 'number', width: 110, importance: 'secondary'},
  {key: 'skippedCount', label: 'Skipped', kind: 'number', width: 100, importance: 'secondary'},
  {key: 'appliedCount', label: 'Applied', kind: 'number', width: 100, importance: 'secondary'},
  {key: 'basis', label: 'Based on', importance: 'reference'},
  {key: 'appliedAt', label: 'Applied at', kind: 'datetime', importance: 'reference'},
  {key: 'revertedAt', label: 'Reverted at', kind: 'datetime', importance: 'reference'},
  {key: 'id', label: 'Id', kind: 'number', importance: 'reference'},
];

const proposalFields: FieldDefinition<ClassificationProposal>[] = [
  {key: 'recipeTitle', label: 'Recipe', width: 240},
  {key: 'previousValue', label: 'Now', width: 150, render: (proposal) => classified(proposal.previousValue)},
  {key: 'proposedValue', label: 'Proposed', width: 150, render: (proposal) => classified(proposal.proposedValue)},
  {key: 'reason', label: 'Because', width: 320},
  decisionField(),
  {key: 'previousSource', label: 'Set by', width: 110, importance: 'secondary'},
  {key: 'recipeId', label: 'Recipe id', kind: 'number', importance: 'reference'},
  {key: 'id', label: 'Id', kind: 'number', importance: 'reference'},
];

interface PreviewForm {
  scope: ClassificationScope;
}

const previewFormFields: FormFieldDefinition<PreviewForm>[] = [
  {
    name: 'scope',
    label: 'Which recipes',
    type: 'select',
    required: true,
    options: Object.entries(SCOPE_LABELS).map(([value, label]) => ({value, label})),
    helperText: 'A value its owner set is never included',
  },
];

const preview: RunPreview<ClassificationRun, PreviewForm> = {
  title: 'Preview a classification run',
  fields: previewFormFields,
  initialValues: {scope: 'NEVER_CLASSIFIED'},
  start: (form) => ClassificationApi.preview('DIET', form.scope),
};

const revertMessage = (run: ClassificationRun) =>
  'Put back what run ' + run.id + ' changed? Recipes classified since stay as they are.';

// A skipped proposal has nothing to decide: the run could not read the recipe
const isDecidable = (proposal: ClassificationProposal) => proposal.decision !== 'SKIPPED';

export const ClassificationScreen = () => (
  <ReviewedRunScreen
    title="Diet classification runs"
    api={ClassificationApi}
    fields={runFields}
    preview={preview}
    revertMessage={revertMessage}
    renderReview={(runId, onBack) => <RunReview runId={runId} onBack={onBack} />}
  />
);

const RunReview = (props: {runId: number, onBack: () => void}) => {
  const {runId} = props;
  const review = useRunReview(ClassificationApi, runId);
  const {proposals, runner, previewed} = review;

  const stats = useMemo(() => {
    const count = (predicate: (proposal: ClassificationProposal) => boolean) =>
      proposals.data.filter(predicate).length;
    return [
      ...DIETS.map((diet) => ({label: DIET_LABELS[diet], value: count((proposal) => proposal.proposedValue === diet)})),
      {label: 'Unreadable', value: count((proposal) => !isDecidable(proposal)), color: 'warning.main'},
      {label: 'Pending', value: count((proposal) => proposal.decision === 'PENDING')},
      {label: 'Accepted', value: count((proposal) => proposal.decision === 'ACCEPTED'), color: 'success.main'},
    ];
  }, [proposals.data]);

  const decide = useCallback((selected: ClassificationProposal[], decision: ClassificationDecision) =>
    runner.run(decision === 'ACCEPTED' ? 'Accepted ' + selected.length : 'Rejected ' + selected.length,
        () => ClassificationApi.decide(runId, selected.map((proposal) => proposal.id), decision)),
  [runner, runId]);

  const rowActions = useMemo<RowAction<ClassificationProposal>[]>(() => [
    {label: 'Accept', icon: <CheckIcon fontSize="small" />,
      hidden: (proposal) => !previewed || !isDecidable(proposal),
      onRun: (proposal) => decide([proposal], 'ACCEPTED')},
    {label: 'Reject', icon: <CloseIcon fontSize="small" />,
      hidden: (proposal) => !previewed || !isDecidable(proposal),
      onRun: (proposal) => decide([proposal], 'REJECTED')},
  ], [decide, previewed]);

  const bulkActions = useMemo<BulkAction<ClassificationProposal>[]>(() => previewed ? [
    {label: 'Accept', icon: <CheckIcon fontSize="small" />,
      onRun: (selected) => decide(selected.filter(isDecidable), 'ACCEPTED')},
    {label: 'Reject', icon: <CloseIcon fontSize="small" />,
      onRun: (selected) => decide(selected.filter(isDecidable), 'REJECTED')},
  ] : [], [decide, previewed]);

  const acceptAllPending = () => {
    const pending = proposals.data.filter((proposal) => proposal.decision === 'PENDING');
    if (pending.length > 0) {
      decide(pending, 'ACCEPTED');
    }
  };

  const accepted = proposals.data.filter((proposal) => proposal.decision === 'ACCEPTED').length;
  const unreadable = proposals.data.filter((proposal) => !isDecidable(proposal)).length;
  const run = review.run.data;

  return (
    <CollectionScreen
      title={'Run ' + runId + (run ? ': ' + SCOPE_LABELS[run.scope] : '')}
      fields={proposalFields}
      data={proposals}
      getRowId={(proposal) => proposal.id}
      detailsTitle={(proposal) => proposal.recipeTitle}
      emptyMessage="The run proposes no change"
      header={
        <RunReviewHeader
          review={review}
          acceptedCount={accepted}
          applyMessage={'Set the diet of ' + accepted + ' recipe(s)? Recipes whose diet changed since the preview ' +
            'are skipped.'}
          unit="recipe"
          onBack={props.onBack}
          actions={<Button onClick={acceptAllPending}>Accept all pending</Button>}
        >
          {unreadable > 0 && (
            <Typography variant="body2" color="text.secondary" sx={{mb: 1}}>
              {unreadable} recipe(s) could not be read because an ingredient is not linked to the catalogue. The
              &quot;Because&quot; column names the ingredient; linking it makes the recipe classifiable on the next run.
            </Typography>
          )}
          <StatTiles stats={stats} />
        </RunReviewHeader>
      }
      rowActions={rowActions}
      bulkActions={bulkActions}
    />
  );
};
