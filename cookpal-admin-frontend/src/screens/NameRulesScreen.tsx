import DeleteIcon from '@mui/icons-material/Delete';
import {Chip} from '@mui/material';
import {useMemo, useState} from 'react';
import {NameRule, NameRuleKind, NameRulesApi} from '../api';
import {CollectionScreen} from '../components/collection/CollectionScreen';
import {FieldDefinition, RowAction} from '../components/collection/types';
import {describeFood} from '../components/foodLabels';
import {EntityFormDialog} from '../components/form/EntityFormDialog';
import {FormFieldDefinition} from '../components/form/types';
import {NutritionTurnedOff} from '../components/NutritionTurnedOff';
import {useActionRunner} from '../hooks/useActionRunner';
import {useCollection} from '../hooks/useAsyncData';

const KIND_LABELS: Record<NameRuleKind, string> = {
  NEVER_LINK_TO: 'Never link to',
  NOT_A_FOOD: 'Not a food',
};

const fields: FieldDefinition<NameRule>[] = [
  {key: 'name', label: 'Name', width: 220},
  {
    key: 'kind',
    label: 'Rule',
    width: 140,
    value: (rule) => KIND_LABELS[rule.kind],
    render: (rule) => <Chip size="small" color={rule.kind === 'NOT_A_FOOD' ? 'default' : 'primary'}
      label={KIND_LABELS[rule.kind]} />,
  },
  {key: 'food', label: 'Food', width: 260, value: (rule) => describeFood(rule.food)},
  {key: 'createdByEmailAddress', label: 'Decided by', width: 220, importance: 'secondary'},
  {key: 'createdOn', label: 'Decided on', kind: 'datetime', width: 180, importance: 'secondary'},
  {key: 'id', label: 'Id', kind: 'number', importance: 'reference'},
];

interface RuleForm {
  name: string;
  kind: NameRuleKind;
  catalogueFoodId: number | null;
}

const ruleFormFields: FormFieldDefinition<RuleForm>[] = [
  {name: 'name', label: 'Ingredient name', type: 'text', required: true,
    helperText: 'Compared as matching compares names: ignoring case and extra spaces'},
  {name: 'kind', label: 'Rule', type: 'select', required: true,
    options: Object.entries(KIND_LABELS).map(([value, label]) => ({value, label}))},
  {name: 'catalogueFoodId', label: 'Id of the food never to link to', type: 'number',
    helperText: 'Only for "Never link to"'},
];

// Deleting a rule keeps the links made while it held.
export const NameRulesScreen = () => {
  const rules = useCollection(NameRulesApi.getAll);
  const runner = useActionRunner(rules.reload);
  const [adding, setAdding] = useState(false);

  const rowActions = useMemo<RowAction<NameRule>[]>(() => [{
    label: 'Delete',
    icon: <DeleteIcon fontSize="small" />,
    color: 'error',
    confirm: (rule) => 'Delete the rule for "' + rule.name + '"? The next relink run may propose it again.',
    onRun: (rule) => runner.run('Deleted the rule for ' + rule.name, () => NameRulesApi.delete(rule.id)),
  }], [runner]);

  const add = async (form: RuleForm) => {
    const foodId = form.kind === 'NEVER_LINK_TO' ? form.catalogueFoodId : null;
    if (await runner.run('Added a rule for ' + form.name, () => NameRulesApi.add(form.name, form.kind, foodId))) {
      setAdding(false);
    }
  };

  if (rules.errorStatus === 404) {
    return <NutritionTurnedOff />;
  }

  return (
    <>
      <CollectionScreen
        title="Name rules"
        fields={fields}
        data={rules}
        getRowId={(rule) => rule.id}
        detailsTitle={(rule) => rule.name}
        emptyMessage="No rule yet. Rejecting a proposal with 'remember' adds one."
        onCreate={() => setAdding(true)}
        createLabel="Add rule"
        rowActions={rowActions}
      />
      <EntityFormDialog<RuleForm>
        open={adding}
        title="Add a name rule"
        fields={ruleFormFields}
        initialValues={{name: '', kind: 'NOT_A_FOOD', catalogueFoodId: null}}
        submitLabel="Add"
        onClose={() => setAdding(false)}
        onSubmit={add}
      />
    </>
  );
};
