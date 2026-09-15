import BlockIcon from '@mui/icons-material/Block';
import TranslateIcon from '@mui/icons-material/Translate';
import {Alert} from '@mui/material';
import {useMemo} from 'react';
import {CatalogueApi, NameRulesApi, NutritionReportsApi, UserCorrection} from '../api';
import {CollectionScreen} from '../components/collection/CollectionScreen';
import {FieldDefinition, RowAction} from '../components/collection/types';
import {describeFood, formatConfidence} from '../components/foodLabels';
import {NutritionTurnedOff} from '../components/NutritionTurnedOff';
import {useActionRunner} from '../hooks/useActionRunner';
import {useCollection} from '../hooks/useAsyncData';

const fields: FieldDefinition<UserCorrection>[] = [
  {key: 'name', label: 'Name', width: 200},
  {key: 'userCount', label: 'Users', kind: 'number', width: 90},
  {key: 'food', label: 'Users linked it to', width: 260,
    value: (correction) => correction.food ? describeFood(correction.food) : 'No food'},
  {key: 'matcherFood', label: 'Matcher suggests', width: 260,
    value: (correction) => correction.matcherFood ?
      describeFood(correction.matcherFood) + ', ' + formatConfidence(correction.matcherConfidence) : 'Nothing'},
  {key: 'language', label: 'Language', width: 100, importance: 'secondary'},
];

// Adopting a correction teaches this instance's catalogue; existing ingredients follow in a relink run.
export const UserCorrectionsScreen = () => {
  const corrections = useCollection(NutritionReportsApi.userCorrections);
  const runner = useActionRunner(corrections.reload);

  const rowActions = useMemo<RowAction<UserCorrection>[]>(() => [
    {
      label: 'Add as name of this food',
      icon: <TranslateIcon fontSize="small" />,
      hidden: (correction) => correction.food === null,
      confirm: (correction) => 'Add "' + correction.name + '" as a name of ' + describeFood(correction.food) +
        '? New ingredients are linked to it; preview a relink run for existing ones.',
      onRun: (correction) => {
        const food = correction.food;
        return food && runner.run('Added "' + correction.name + '"',
            () => CatalogueApi.addName(food.id, correction.language ?? 'de', correction.name));
      },
    },
    {
      label: 'Mark as no food',
      icon: <BlockIcon fontSize="small" />,
      hidden: (correction) => correction.food !== null,
      confirm: (correction) => 'Never link "' + correction.name + '" to any food?',
      onRun: (correction) => runner.run('"' + correction.name + '" is no food',
          () => NameRulesApi.add(correction.name, 'NOT_A_FOOD', null)),
    },
  ], [runner]);

  if (corrections.errorStatus === 404) {
    return <NutritionTurnedOff />;
  }

  return (
    <CollectionScreen
      title="User corrections"
      fields={fields}
      data={corrections}
      getRowId={(correction) => [correction.name, correction.language, correction.food?.id].join('|')}
      detailsTitle={(correction) => correction.name}
      emptyMessage="The matcher agrees with every link users made"
      header={
        <Alert severity="info" sx={{mb: 2}}>
          Carry corrections many users made into the curation of the next dataset release as well, so every
          instance learns them.
        </Alert>
      }
      rowActions={rowActions}
    />
  );
};
