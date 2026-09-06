import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import LockIcon from '@mui/icons-material/Lock';
import ManageAccountsIcon from '@mui/icons-material/ManageAccounts';
import PasswordIcon from '@mui/icons-material/Password';
import {Chip} from '@mui/material';
import {useCallback, useMemo, useState} from 'react';
import {Role, User, UsersApi} from '../api';
import {BulkAction, FieldDefinition, RowAction} from '../components/collection/types';
import {CollectionScreen} from '../components/collection/CollectionScreen';
import {EntityFormDialog} from '../components/form/EntityFormDialog';
import {FormFieldDefinition} from '../components/form/types';
import {StatTiles} from '../components/StatTiles';
import {useActionRunner} from '../hooks/useActionRunner';
import {useCollection} from '../hooks/useAsyncData';

interface UserForm {
  emailAddress: string;
  activated: boolean;
  role: Role | '';
}

const ROLE_OPTIONS = [
  {value: '', label: 'No role'},
  {value: 'ADMIN', label: 'Administrator'},
  {value: 'DEMO', label: 'Demo account (cannot be deleted)'},
];

const fields: FieldDefinition<User>[] = [
  {key: 'emailAddress', label: 'Email address', width: 260},
  {
    key: 'activated',
    label: 'Activated',
    kind: 'boolean',
    width: 110,
    render: (user) => user.activated ?
      <Chip size="small" color="success" label="Active" /> :
      <Chip size="small" color="warning" label="Locked" />,
  },
  {
    key: 'roles',
    label: 'Role',
    width: 130,
    render: (user) => user.roles ?
      <Chip size="small" label={user.roles} color={user.roles === 'ADMIN' ? 'primary' : 'default'} /> :
      <span>-</span>,
  },
  {key: 'userId', label: 'Id', kind: 'number', width: 80, importance: 'secondary'},
  {key: 'recipeCount', label: 'Recipes', kind: 'number', width: 100, importance: 'secondary'},
  {key: 'ingredientCount', label: 'Ingredients', kind: 'number', width: 110, importance: 'secondary'},
  {key: 'createdOn', label: 'Signed up', kind: 'datetime', width: 180, importance: 'secondary'},
  {key: 'lastChange', label: 'Last change', kind: 'datetime', importance: 'reference'},
];

const userFormFields: FormFieldDefinition<UserForm>[] = [
  {name: 'emailAddress', label: 'Email address', type: 'text', required: true},
  {name: 'activated', label: 'Activated', type: 'switch'},
  {name: 'role', label: 'Role', type: 'select', options: ROLE_OPTIONS},
];

const roleFormFields: FormFieldDefinition<{role: Role | ''}>[] = [
  {name: 'role', label: 'Role', type: 'select', options: ROLE_OPTIONS},
];

export const UsersScreen = () => {
  const users = useCollection(UsersApi.getAll);
  const runner = useActionRunner(users.reload);
  const [editing, setEditing] = useState<User>();
  const [assigningRoleTo, setAssigningRoleTo] = useState<User[]>();

  const stats = useMemo(() => [
    {label: 'Accounts', value: users.data.length},
    {label: 'Activated', value: users.data.filter((user) => user.activated).length},
    {label: 'Administrators', value: users.data.filter((user) => user.roles === 'ADMIN').length},
    {label: 'Recipes held', value: users.data.reduce((sum, user) => sum + user.recipeCount, 0)},
  ], [users.data]);

  const setActivation = useCallback((people: User[], activated: boolean) => runner.runAll(
      activated ? 'Activated' : 'Locked', people,
      (user) => activated ? UsersApi.activate(user.userId) : UsersApi.deactivate(user.userId),
  ), [runner]);

  // The update replaces every field, so the untouched ones are sent back as they stand.
  const setRole = useCallback((people: User[], role: Role | '') =>
    runner.runAll('Set the role', people, (user) => UsersApi.update(user.userId, {
      emailAddress: user.emailAddress,
      activated: user.activated,
      role: role === '' ? null : role,
    })), [runner]);

  // Memoised: the table rebuilds every column when these change identity.
  const rowActions = useMemo<RowAction<User>[]>(() => [
    {label: 'Edit', icon: <EditIcon fontSize="small" />, onRun: setEditing},
    {
      label: 'Activate',
      icon: <CheckCircleIcon fontSize="small" />,
      hidden: (user) => user.activated,
      onRun: (user) => setActivation([user], true),
    },
    {
      label: 'Lock',
      icon: <LockIcon fontSize="small" />,
      hidden: (user) => !user.activated,
      onRun: (user) => setActivation([user], false),
    },
    {
      label: 'Send password reset',
      icon: <PasswordIcon fontSize="small" />,
      confirm: (user) => 'Send a password reset mail to ' + user.emailAddress + '?',
      onRun: (user) => runner.run('Sent a password reset mail',
          () => UsersApi.sendPasswordReset(user.userId)),
    },
    {
      label: 'Delete',
      icon: <DeleteIcon fontSize="small" />,
      color: 'error',
      confirm: (user) => 'Delete ' + user.emailAddress +
        ' and everything they own? This cannot be undone.',
      onRun: (user) => runner.run('Deleted ' + user.emailAddress,
          () => UsersApi.delete(user.userId)),
    },
  ], [runner, setActivation]);

  const bulkActions = useMemo<BulkAction<User>[]>(() => [
    {
      label: 'Activate',
      icon: <CheckCircleIcon fontSize="small" />,
      onRun: (people) => setActivation(people, true),
    },
    {
      label: 'Lock',
      icon: <LockIcon fontSize="small" />,
      onRun: (people) => setActivation(people, false),
    },
    {
      label: 'Set role',
      icon: <ManageAccountsIcon fontSize="small" />,
      onRun: setAssigningRoleTo,
    },
    {
      label: 'Delete',
      icon: <DeleteIcon fontSize="small" />,
      confirm: (people) => 'Delete ' + people.length +
        ' account(s) and everything they own? This cannot be undone.',
      onRun: (people) => runner.runAll('Deleted', people,
          (user) => UsersApi.delete(user.userId)),
    },
  ], [runner, setActivation]);

  return (
    <>
      <CollectionScreen
        title="Users"
        fields={fields}
        data={users}
        getRowId={(user) => user.userId}
        detailsTitle={(user) => user.emailAddress}
        header={<StatTiles stats={stats} />}
        rowActions={rowActions}
        bulkActions={bulkActions}
      />

      <EntityFormDialog<UserForm>
        open={!!editing}
        title={'Edit ' + (editing?.emailAddress ?? '')}
        fields={userFormFields}
        initialValues={{
          emailAddress: editing?.emailAddress ?? '',
          activated: editing?.activated ?? false,
          role: editing?.roles ?? '',
        }}
        onClose={() => setEditing(undefined)}
        onSubmit={async (values) => {
          const saved = await runner.run('Saved ' + values.emailAddress,
              () => UsersApi.update(editing!.userId, {
                emailAddress: values.emailAddress,
                activated: values.activated,
                role: values.role === '' ? null : values.role,
              }));
          if (saved) {
            setEditing(undefined);
          }
        }}
      />

      <EntityFormDialog<{role: Role | ''}>
        open={!!assigningRoleTo}
        title={'Set the role of ' + (assigningRoleTo?.length ?? 0) + ' account(s)'}
        fields={roleFormFields}
        initialValues={{role: ''}}
        submitLabel="Set role"
        onClose={() => setAssigningRoleTo(undefined)}
        onSubmit={async (values) => {
          if (await setRole(assigningRoleTo ?? [], values.role)) {
            setAssigningRoleTo(undefined);
          }
        }}
      />
    </>
  );
};
