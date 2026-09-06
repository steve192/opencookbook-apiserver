import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import LinkOffIcon from '@mui/icons-material/LinkOff';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import {Chip, Link} from '@mui/material';
import {useCallback, useMemo} from 'react';
import {toast} from 'react-toastify';
import {Share, SharesApi, ShareStatistics} from '../api';
import {CollectionScreen} from '../components/collection/CollectionScreen';
import {BulkAction, FieldDefinition, RowAction} from '../components/collection/types';
import {StatTiles} from '../components/StatTiles';
import {useActionRunner} from '../hooks/useActionRunner';
import {useAsyncData, useCollection} from '../hooks/useAsyncData';

const NO_STATISTICS: ShareStatistics = {totalShares: 0, totalAccesses: 0, expiringSoon: 0};

const fields: FieldDefinition<Share>[] = [
  {key: 'recipeTitle', label: 'Recipe', width: 220},
  {key: 'ownerEmailAddress', label: 'Shared by', width: 220},
  {key: 'accessCount', label: 'Views', kind: 'number', width: 90},
  {
    key: 'expired',
    label: 'State',
    kind: 'boolean',
    width: 110,
    render: (share) => share.expired ?
      <Chip size="small" label="Lapsed" /> :
      <Chip size="small" color="success" label="Live" />,
  },
  {key: 'createdOn', label: 'Shared on', kind: 'datetime', width: 180, importance: 'secondary'},
  {key: 'expiresAt', label: 'Valid until', kind: 'datetime', width: 180, importance: 'secondary'},
  {
    key: 'shareUrl',
    label: 'Link',
    width: 300,
    importance: 'secondary',
    render: (share) => (
      <Link href={share.shareUrl} target="_blank" rel="noreferrer"
        sx={{display: 'inline-flex', alignItems: 'center', gap: 0.5}}>
        {share.shareUrl}<OpenInNewIcon fontSize="inherit" />
      </Link>
    ),
  },
  {key: 'shareId', label: 'Share id', importance: 'reference'},
  {key: 'recipeId', label: 'Recipe id', kind: 'number', importance: 'reference'},
];

export const SharesScreen = () => {
  const shares = useCollection(SharesApi.getAll);
  const statistics = useAsyncData(SharesApi.statistics, NO_STATISTICS);
  const reload = useCallback(() => {
    shares.reload();
    statistics.reload();
  }, [shares.reload, statistics.reload]);
  const runner = useActionRunner(reload);

  const copyLinks = useCallback(async (selected: Share[]) => {
    try {
      await navigator.clipboard.writeText(selected.map((share) => share.shareUrl).join('\n'));
      toast.success('Copied ' + selected.length + ' link(s)');
    } catch (cause) {
      // Clipboard access needs a secure context, which a panel reached over plain http is not.
      toast.error('The browser refused clipboard access');
    }
  }, []);

  const stats = useMemo(() => [
    {label: 'Shared recipes', value: statistics.data.totalShares},
    {label: 'Times opened', value: statistics.data.totalAccesses},
    {label: 'Lapsing within 7 days', value: statistics.data.expiringSoon},
  ], [statistics.data]);

  // Memoised: the table rebuilds every column when these change identity.
  const rowActions = useMemo<RowAction<Share>[]>(() => [
    {
      label: 'Copy link',
      icon: <ContentCopyIcon fontSize="small" />,
      onRun: (share) => copyLinks([share]),
    },
    {
      label: 'Stop sharing',
      icon: <LinkOffIcon fontSize="small" />,
      color: 'error',
      confirm: (share) => 'Stop sharing "' + share.recipeTitle +
        '"? The link stops working immediately.',
      onRun: (share) => runner.run('Stopped sharing "' + share.recipeTitle + '"',
          () => SharesApi.revoke(share.shareId)),
    },
  ], [runner, copyLinks]);

  const bulkActions = useMemo<BulkAction<Share>[]>(() => [
    {label: 'Copy links', icon: <ContentCopyIcon fontSize="small" />, onRun: copyLinks},
    {
      label: 'Stop sharing',
      icon: <LinkOffIcon fontSize="small" />,
      confirm: (selected) => 'Stop ' + selected.length +
        ' share(s)? The links stop working immediately.',
      onRun: (selected) => runner.runAll('Stopped sharing', selected,
          (share) => SharesApi.revoke(share.shareId)),
    },
  ], [runner, copyLinks]);

  return (
    <CollectionScreen
      title="Shared recipes"
      fields={fields}
      data={shares}
      getRowId={(share) => share.shareId}
      detailsTitle={(share) => share.recipeTitle}
      header={<StatTiles stats={stats} />}
      emptyMessage="Nothing is being shared publicly"
      rowActions={rowActions}
      bulkActions={bulkActions}
    />
  );
};
