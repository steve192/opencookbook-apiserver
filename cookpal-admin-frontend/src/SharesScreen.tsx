import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import LinkOffIcon from '@mui/icons-material/LinkOff';
import {Box, Chip, Paper, Typography} from '@mui/material';
import {DataGrid, GridColDef} from '@mui/x-data-grid';
import {useCallback, useEffect, useState} from 'react';
import {toast} from 'react-toastify';
import RestAPI, {Share, ShareStatistics} from './RestAPI';
import {TableToolbar} from './TableToolbar';

/**
 * What this instance is currently publishing to anyone holding a link.
 *
 * Public shares are the only way user content leaves the instance to visitors who never signed
 * up, so an operator needs to be able to see all of it, follow any of it, and take any of it
 * down without reaching for SQL.
 *
 * @return {JSX.Element} the shared recipes screen
 */
export const SharesScreen = () => {
  const [shares, setShares] = useState<Share[]>();
  const [statistics, setStatistics] = useState<ShareStatistics>();
  const [selectedShareIds, setSelectedShareIds] = useState<string[]>([]);

  const reload = useCallback(() => {
    RestAPI.getAllShares().then(setShares);
    RestAPI.getShareStatistics().then(setStatistics);
  }, []);

  useEffect(reload, [reload]);

  const revokeSelectedShares = async () => {
    for (const shareId of selectedShareIds) {
      try {
        await RestAPI.revokeShare(shareId);
        toast('Revoked share ' + shareId, {});
      } catch (e) {
        toast.error('Error revoking share ' + shareId, {});
      }
    }
    reload();
  };

  const copySelectedLinks = async () => {
    const links = (shares ?? [])
        .filter((share) => selectedShareIds.includes(share.shareId))
        .map((share) => share.shareUrl);

    try {
      await navigator.clipboard.writeText(links.join('\n'));
      toast('Copied ' + links.length + ' link(s)', {});
    } catch (e) {
      // Clipboard access is refused outside a secure context, which a self hosted panel
      // reached over plain http is. Saying so beats a button that silently does nothing.
      toast.error('The browser refused clipboard access', {});
    }
  };

  return (
    <>
      <ShareTotals statistics={statistics} />
      <TableToolbar
        title="Shared recipes"
        selectedCount={selectedShareIds.length}
        actions={[
          {label: 'Stop sharing', icon: <LinkOffIcon />, onPress: revokeSelectedShares},
          {label: 'Copy links', icon: <ContentCopyIcon />, onPress: copySelectedLinks},
        ]} />
      <DataGrid
        rows={shares ?? []}
        columns={shareColumns}
        getRowId={(row) => row.shareId}
        initialState={{
          pagination: {
            paginationModel: {page: 0, pageSize: 50},
          },
          sorting: {
            // What is being looked at most is what an operator is most likely looking for.
            sortModel: [{field: 'accessCount', sort: 'desc'}],
          },
        }}
        pageSizeOptions={[50, 100]}
        onRowSelectionModelChange={(selectionModel) => setSelectedShareIds(selectionModel as string[])}
        checkboxSelection
      />
    </>);
};

const shareColumns: GridColDef<Share>[] = [
  {
    field: 'recipeTitle',
    headerName: 'Recipe',
    width: 240,
  },
  {
    field: 'ownerEmailAddress',
    headerName: 'Shared by',
    width: 240,
  },
  {
    field: 'accessCount',
    headerName: 'Views',
    width: 90,
    type: 'number',
  },
  {
    field: 'createdOn',
    headerName: 'Shared on',
    width: 180,
    valueFormatter: (params) => formatTimestamp(params.value),
  },
  {
    field: 'expiresAt',
    headerName: 'Valid until',
    width: 180,
    valueFormatter: (params) => formatTimestamp(params.value),
  },
  {
    field: 'expired',
    headerName: 'State',
    width: 120,
    renderCell: (params) => params.value ?
      <Chip label="Lapsed" size="small" color="default" /> :
      <Chip label="Live" size="small" color="success" />,
  },
  {
    field: 'shareUrl',
    headerName: 'Link',
    width: 420,
    // Following the link is how an operator sees what is actually being published.
    renderCell: (params) => <a href={params.value} target="_blank" rel="noreferrer">{params.value}</a>,
  },
  {
    field: 'shareId',
    headerName: 'Share id',
    width: 300,
  },
];

/**
 * Renders a timestamp from the api the way a person reads one.
 *
 * @param {string} value an ISO instant
 * @return {string} the same moment in the operator's own locale and timezone
 */
function formatTimestamp(value: string): string {
  if (!value) {
    return '';
  }
  return new Date(value).toLocaleString();
}

const ShareTotals = (props: {statistics?: ShareStatistics}) => (
  <Box sx={{display: 'flex', gap: 2, mb: 1, flexWrap: 'wrap'}}>
    <Total label="Shared recipes" value={props.statistics?.totalShares} />
    <Total label="Times opened" value={props.statistics?.totalAccesses} />
    <Total label="Lapsing within 30 days" value={props.statistics?.expiringSoon} />
  </Box>
);

const Total = (props: {label: string, value?: number}) => (
  <Paper sx={{px: 3, py: 2, minWidth: 180}} elevation={1}>
    <Typography variant="h4">{props.value ?? '-'}</Typography>
    <Typography variant="body2" color="text.secondary">{props.label}</Typography>
  </Paper>
);
