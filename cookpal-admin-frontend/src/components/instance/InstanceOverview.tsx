import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import {Alert, Box, Chip, LinearProgress, Paper, Stack, Typography} from '@mui/material';
import {ReactNode} from 'react';
import {InstanceOverview as Overview} from '../../api';

const Section = (props: {title: string, children: ReactNode}) => (
  <Paper variant="outlined" sx={{p: 2}}>
    <Typography variant="overline" color="text.secondary">{props.title}</Typography>
    <Stack spacing={0.75} sx={{mt: 0.5}}>{props.children}</Stack>
  </Paper>
);

const Row = (props: {label: string, children: ReactNode}) => (
  <Box sx={{display: 'flex', flexWrap: 'wrap', columnGap: 2, alignItems: 'center'}}>
    <Typography variant="body2" color="text.secondary" sx={{width: 170, flexShrink: 0}}>
      {props.label}
    </Typography>
    <Typography variant="body2" component="div" sx={{minWidth: 0, overflowWrap: 'anywhere'}}>
      {props.children}
    </Typography>
  </Box>
);

const Flag = (props: {label: string, on: boolean, note?: string}) => (
  <Chip
    size="small"
    color={props.on ? 'success' : 'default'}
    icon={props.on ? <CheckIcon /> : <CloseIcon />}
    label={props.label + (props.note ? ' (' + props.note + ')' : '')}
  />
);

const missingMailSettings = (overview: Overview) => {
  const missing = [
    overview.mail.from ? undefined : 'MAIL_FROM',
    overview.instanceUrl.configured ? undefined : 'INSTANCE_URL',
  ].filter(Boolean);
  return missing.join(' and ') + (missing.length > 1 ? ' are' : ' is') + ' missing';
};

const orNotSet = (value: string | number | null) => value ?? 'Not set';

// What the instance runs with, as the server reports it. Only the server decides what is safe to show.
export const InstanceOverview = (props: {overview?: Overview, loading: boolean, error?: string}) => {
  const {overview} = props;
  return (
    <Stack spacing={1.5}>
      {props.error && <Alert severity="error">{props.error}</Alert>}
      {props.loading && <LinearProgress />}
      {overview && (
        <>
          <Section title="Instance">
            <Row label="Version">{orNotSet(overview.version)}</Row>
            <Row label="Address">{overview.instanceUrl.effective}</Row>
            {!overview.instanceUrl.configured && (
              <Alert severity="warning">
                INSTANCE_URL is not set; links are built from the address you opened the panel with.
                Set it so links work for everyone.
              </Alert>
            )}
          </Section>

          <Section title="Mail">
            {overview.mail.configured ? (
              <>
                <Row label="Server">
                  {overview.mail.host}:{overview.mail.port} ({overview.mail.protocol}
                  {overview.mail.startTls ? ', STARTTLS' : ''})
                </Row>
                <Row label="Sender">{orNotSet(overview.mail.from)}</Row>
              </>
            ) : (
              <Row label="Server">
                Not configured. Invitation and password reset links are handed over by hand.
              </Row>
            )}
            {overview.mail.configured && !overview.mail.enabled && (
              <Alert severity="warning">
                SMTP is set, but {missingMailSettings(overview)}, so no mail is sent.
              </Alert>
            )}
            <Row label="Open signups">
              {overview.registration.confirmation === 'MAIL' ?
                'Confirmed by the link in a mail' : 'Wait until an administrator activates them'}
            </Row>
          </Section>

          <Section title="Features">
            <Stack direction="row" useFlexGap sx={{flexWrap: 'wrap', gap: 1}}>
              <Flag label="Sharing" on={overview.features.sharing} />
              <Flag label="Households" on={overview.features.households} />
              <Flag label="API keys" on={overview.features.apiKeys} />
              <Flag
                label="Recipe scan"
                on={overview.features.recipeScan.enabled}
                note={overview.features.recipeScan.configured ? undefined : 'not configured'}
              />
            </Stack>
          </Section>

          <Section title="Sign in with Google">
            {overview.googleSignIn ? (
              <>
                <Row label="Web app">{overview.googleSignIn.clientId}</Row>
                <Row label="Android app">
                  {overview.googleSignIn.androidClientId ?? 'Not set. The Android app does not offer Google.'}
                </Row>
              </>
            ) : (
              <Row label="Status">Off. Set GOOGLE_CLIENT_ID to offer it.</Row>
            )}
          </Section>

          <Section title="Services">
            <Row label="Recipe import">{orNotSet(overview.services.recipeImportUrl)}</Row>
            <Row label="Recipe scan">{orNotSet(overview.services.recipeScanUrl)}</Row>
          </Section>

          <Section title="Legal documents">
            <Row label="Directory">{overview.legal.directory}</Row>
            <Stack direction="row" useFlexGap sx={{flexWrap: 'wrap', gap: 1}}>
              {overview.legal.documents.map((entry) => (
                <Flag
                  key={entry.document}
                  label={entry.document}
                  on={entry.published}
                  note={entry.published ? undefined : 'missing'}
                />
              ))}
            </Stack>
          </Section>

          <Section title="Limits">
            <Row label="Upload">{overview.limits.maxUploadMb} MB</Row>
            <Row label="Image">{overview.limits.maxImageMb} MB</Row>
          </Section>
        </>
      )}
    </Stack>
  );
};
