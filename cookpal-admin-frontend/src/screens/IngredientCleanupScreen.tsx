import CleaningServicesIcon from '@mui/icons-material/CleaningServices';
import EditIcon from '@mui/icons-material/Edit';
import {Alert, Chip} from '@mui/material';
import {useCallback, useMemo, useState} from 'react';
import {toast} from 'react-toastify';
import {IngredientsApi, NameCleanupLine, NameCleanupProposal} from '../api';
import {CollectionScreen} from '../components/collection/CollectionScreen';
import {BulkAction, FieldDefinition, RowAction} from '../components/collection/types';
import {EntityFormDialog} from '../components/form/EntityFormDialog';
import {FormFieldDefinition} from '../components/form/types';
import {useActionRunner} from '../hooks/useActionRunner';
import {useCollection} from '../hooks/useAsyncData';

const amountAndUnit = (amount: number | null, unit: string | null) =>
  [amount === null || amount === 0 ? '' : amount, unit ?? ''].join(' ').trim() || '-';

const describeLine = (line: NameCleanupLine) =>
  line.recipeTitle + ': ' + amountAndUnit(line.amount, line.unit) + ' → ' + amountAndUnit(line.newAmount, line.newUnit);

interface NameForm {
  name: string;
}

const nameFormFields: FormFieldDefinition<NameForm>[] = [
  {name: 'name', label: 'Name after cleaning', type: 'text', required: true},
];

// Moves amounts and units out of names ("200g Schmelzkäse") into recipe lines. Not revertible.
export const IngredientCleanupScreen = () => {
  const proposals = useCollection(IngredientsApi.nameCleanupPreview);
  const runner = useActionRunner(proposals.reload);
  // Corrected names by ingredient id.
  const [corrections, setCorrections] = useState<Record<number, string>>({});
  const [editing, setEditing] = useState<NameCleanupProposal>();

  const nameOf = useCallback((proposal: NameCleanupProposal) =>
    corrections[proposal.ingredientId] ?? proposal.proposedName, [corrections]);

  const fields = useMemo<FieldDefinition<NameCleanupProposal>[]>(() => [
    {key: 'name', label: 'Name now', width: 240},
    {
      key: 'proposedName',
      label: 'Name after cleaning',
      width: 240,
      value: nameOf,
      render: (proposal) => <>
        {nameOf(proposal)}
        {proposal.mergesIntoIngredientId !== null && <Chip size="small" label="merges" sx={{ml: 1}} />}
      </>,
    },
    {key: 'amount', label: 'From the name', width: 140, value: (proposal) => amountAndUnit(proposal.amount, proposal.unit)},
    {key: 'lines', label: 'Recipe lines', kind: 'list', width: 320, value: (proposal) => proposal.lines.map(describeLine)},
    {key: 'ownerEmailAddress', label: 'Owner', width: 220, importance: 'secondary'},
    {key: 'ingredientId', label: 'Id', kind: 'number', importance: 'reference'},
  ], [nameOf]);

  const apply = useCallback((selected: NameCleanupProposal[]) => runner.run('Cleaned ' + selected.length + ' name(s)', async () => {
    const outcome = await IngredientsApi.applyNameCleanup(selected.map((proposal) => ({
      ingredientId: proposal.ingredientId,
      name: nameOf(proposal),
      lastChange: proposal.lastChange,
    })));
    if (outcome.skipped > 0) {
      toast.warning(outcome.skipped + ' skipped: changed since the preview. Check them again.');
    }
  }), [runner, nameOf]);

  const rowActions = useMemo<RowAction<NameCleanupProposal>[]>(() => [
    {label: 'Correct name', icon: <EditIcon fontSize="small" />, onRun: setEditing},
    {
      label: 'Clean',
      icon: <CleaningServicesIcon fontSize="small" />,
      confirm: (proposal) => 'Clean "' + proposal.name + '" to "' + nameOf(proposal) + '"? This cannot be undone.',
      onRun: (proposal) => apply([proposal]),
    },
  ], [apply, nameOf]);

  const bulkActions = useMemo<BulkAction<NameCleanupProposal>[]>(() => [{
    label: 'Clean selected',
    icon: <CleaningServicesIcon fontSize="small" />,
    confirm: (selected) => 'Clean ' + selected.length + ' ingredient name(s)? Amounts and units move into the recipe lines; ' +
      'this cannot be undone.',
    onRun: apply,
  }], [apply]);

  return (
    <>
      <CollectionScreen
        title="Ingredient name cleanup"
        fields={fields}
        data={proposals}
        getRowId={(proposal) => proposal.ingredientId}
        detailsTitle={(proposal) => proposal.name}
        emptyMessage="No ingredient name holds an amount or unit"
        header={
          <Alert severity="info" sx={{mb: 2}}>
            Check the proposed names, correct the odd one, then clean what is right. What a name holds replaces the
            amount and unit of its recipe lines. Food links stay; preview a relink run afterwards.
          </Alert>
        }
        rowActions={rowActions}
        bulkActions={bulkActions}
      />
      <EntityFormDialog<NameForm>
        open={!!editing}
        title={'Correct "' + (editing?.name ?? '') + '"'}
        fields={nameFormFields}
        initialValues={{name: editing ? nameOf(editing) : ''}}
        submitLabel="Keep"
        onClose={() => setEditing(undefined)}
        onSubmit={(form) => {
          if (editing) {
            setCorrections((current) => ({...current, [editing.ingredientId]: form.name.trim()}));
            setEditing(undefined);
          }
        }}
      />
    </>
  );
};
