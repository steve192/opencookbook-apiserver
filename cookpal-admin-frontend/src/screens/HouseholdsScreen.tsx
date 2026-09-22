import GroupRemoveIcon from '@mui/icons-material/GroupRemove';
import {Chip, Stack} from '@mui/material';
import {useMemo} from 'react';
import {Household, HouseholdsApi} from '../api';
import {CollectionScreen} from '../components/collection/CollectionScreen';
import {BulkAction, FieldDefinition, RowAction} from '../components/collection/types';
import {useActionRunner} from '../hooks/useActionRunner';
import {useCollection} from '../hooks/useAsyncData';

const sharingCount = (household: Household) =>
  household.members.filter((member) => member.shareRecipes).length;

const fields: FieldDefinition<Household>[] = [
  {key: 'name', label: 'Household', width: 220},
  {key: 'memberCount', label: 'Members', kind: 'number', width: 100},
  {
    key: 'sharingCount',
    label: 'Sharing a cookbook',
    kind: 'number',
    width: 180,
    value: sharingCount,
    render: (household) => (
      <Chip size="small" label={sharingCount(household) + ' of ' + household.memberCount} />
    ),
  },
  {
    key: 'members',
    label: 'Who is in it',
    width: 360,
    importance: 'secondary',
    render: (household) => (
      <Stack direction="row" gap={0.5} flexWrap="wrap">
        {household.members.map((member) => (
          <Chip key={member.userId} size="small" label={member.emailAddress}
            color={member.shareRecipes ? 'success' : 'default'} />
        ))}
      </Stack>
    ),
  },
  {key: 'createdOn', label: 'Started', kind: 'datetime', width: 180, importance: 'secondary'},
  {key: 'id', label: 'Household id', importance: 'reference'},
];

/**
 * Dissolving a household deletes no recipe: a household owns none.
 *
 * @return {ReactNode} the households screen
 */
export const HouseholdsScreen = () => {
  const households = useCollection(HouseholdsApi.getAll);
  const runner = useActionRunner(households.reload);

  // Memoised: the table rebuilds every column when these change identity.
  const rowActions = useMemo<RowAction<Household>[]>(() => [
    {
      label: 'Dissolve',
      icon: <GroupRemoveIcon fontSize="small" />,
      color: 'error',
      confirm: (household) => 'Dissolve "' + household.name + '"? Its ' + household.memberCount +
        ' member(s) stop seeing each other\'s cookbooks. No recipe is deleted.',
      onRun: (household) => runner.run('Dissolved "' + household.name + '"',
          () => HouseholdsApi.dissolve(household.id)),
    },
  ], [runner]);

  const bulkActions = useMemo<BulkAction<Household>[]>(() => [
    {
      label: 'Dissolve',
      icon: <GroupRemoveIcon fontSize="small" />,
      confirm: (selected) => 'Dissolve ' + selected.length +
        ' household(s)? No recipe is deleted.',
      onRun: (selected) => runner.runAll('Dissolved', selected,
          (household) => HouseholdsApi.dissolve(household.id)),
    },
  ], [runner]);

  return (
    <CollectionScreen
      title="Households"
      fields={fields}
      data={households}
      getRowId={(household) => household.id}
      detailsTitle={(household) => household.name}
      emptyMessage="Nobody shares a cookbook on this instance"
      rowActions={rowActions}
      bulkActions={bulkActions}
    />
  );
};
