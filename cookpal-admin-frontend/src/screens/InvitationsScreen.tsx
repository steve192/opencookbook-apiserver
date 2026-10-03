import DeleteIcon from '@mui/icons-material/Delete';
import LinkIcon from '@mui/icons-material/Link';
import {useMemo, useState} from 'react';
import {Invitation, InvitationsApi} from '../api';
import {useInstance} from '../AppContext';
import {CollectionScreen} from '../components/collection/CollectionScreen';
import {FieldDefinition, RowAction} from '../components/collection/types';
import {EntityFormDialog} from '../components/form/EntityFormDialog';
import {FormFieldDefinition} from '../components/form/types';
import {LinkContent, LinkDialog} from '../components/LinkDialog';
import {useActionRunner} from '../hooks/useActionRunner';
import {useCollection} from '../hooks/useAsyncData';

interface InvitationForm {
  validForDays: string;
  sendTo: string;
}

const VALIDITY_OPTIONS = [
  {value: '1', label: '1 day'},
  {value: '7', label: '7 days'},
  {value: '30', label: '30 days'},
];

const DELETED_ADMINISTRATOR = 'a deleted administrator';
const invitationLink = (link: string, mailedTo: string | null = null): LinkContent => ({
  title: 'Invitation link',
  link,
  note: mailedTo ?
    'The link was mailed to ' + mailedTo + '. It works once.' :
    'Hand this link to the person you invite. It works once.',
});

const fields: FieldDefinition<Invitation>[] = [
  {key: 'link', label: 'Link', width: 420},
  {
    key: 'createdBy',
    label: 'Created by',
    width: 220,
    value: (invitation) => invitation.createdBy ?? DELETED_ADMINISTRATOR,
  },
  {key: 'expiresAt', label: 'Expires', kind: 'datetime', width: 180},
  {key: 'createdOn', label: 'Created', kind: 'datetime', width: 180, importance: 'secondary'},
];

export const InvitationsScreen = () => {
  const invitations = useCollection(InvitationsApi.getAll);
  const mailEnabled = useInstance().data?.mailEnabled;
  const runner = useActionRunner(invitations.reload);
  const [creating, setCreating] = useState(false);
  const [shownLink, setShownLink] = useState<LinkContent>();

  const formFields = useMemo<FormFieldDefinition<InvitationForm>[]>(() => [
    {name: 'validForDays', label: 'Valid for', type: 'select', options: VALIDITY_OPTIONS},
    ...(mailEnabled ? [{
      name: 'sendTo',
      label: 'Send by mail to',
      type: 'text',
      helperText: 'Optional. Leave empty to hand over the link yourself.',
    } satisfies FormFieldDefinition<InvitationForm>] : []),
  ], [mailEnabled]);

  const rowActions = useMemo<RowAction<Invitation>[]>(() => [
    {
      label: 'Show link',
      icon: <LinkIcon fontSize="small" />,
      onRun: (invitation) => setShownLink(invitationLink(invitation.link)),
    },
    {
      label: 'Revoke',
      icon: <DeleteIcon fontSize="small" />,
      color: 'error',
      confirm: () => 'Revoke this invitation? The link stops working.',
      onRun: (invitation) => runner.run('Revoked the invitation',
          () => InvitationsApi.revoke(invitation.id)),
    },
  ], [runner]);

  return (
    <>
      <CollectionScreen
        title="Invitations"
        fields={fields}
        data={invitations}
        getRowId={(invitation) => invitation.id}
        detailsTitle={(invitation) => 'Invitation by ' + (invitation.createdBy ?? DELETED_ADMINISTRATOR)}
        rowActions={rowActions}
        onCreate={() => setCreating(true)}
        createLabel="Create invitation"
        emptyMessage="No open invitations"
      />

      <EntityFormDialog<InvitationForm>
        open={creating}
        title="Create invitation"
        fields={formFields}
        initialValues={{validForDays: '7', sendTo: ''}}
        submitLabel="Create"
        onClose={() => setCreating(false)}
        onSubmit={async (values) => {
          const sendTo = values.sendTo.trim() || null;
          let invitation: Invitation | undefined;
          const saved = await runner.run('Created the invitation', async () => {
            invitation = await InvitationsApi.create({validForDays: Number(values.validForDays), sendTo});
          });
          if (saved && invitation) {
            setCreating(false);
            setShownLink(invitationLink(invitation.link, sendTo));
          }
        }}
      />

      <LinkDialog content={shownLink} onClose={() => setShownLink(undefined)} />
    </>
  );
};
