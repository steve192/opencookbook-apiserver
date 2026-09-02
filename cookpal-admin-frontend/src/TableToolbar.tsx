import {IconButton, Toolbar, Tooltip, Typography} from '@mui/material';
import {alpha} from '@mui/material/styles';
import {ReactNode} from 'react';

/** One thing that can be done to whatever is selected. */
export interface TableToolbarAction {
  label: string;
  icon: ReactNode;
  onPress: () => void;
}

/**
 * The bar above a data grid: the name of what is listed, or what is selected and what can be
 * done with it.
 *
 * Every screen in this panel shows the same bar over the same kind of grid, so the only thing
 * that varies is which actions the selection supports.
 *
 * @param {object} props what is listed, how much of it is selected, and what can be done with it
 * @return {JSX.Element} the toolbar
 */
export const TableToolbar = (props: {
  title: string,
  selectedCount: number,
  actions: TableToolbarAction[],
}) => {
  const somethingIsSelected = props.selectedCount > 0;

  return (
    <Toolbar
      sx={{
        'pl': {sm: 2},
        'pr': {xs: 1, sm: 1},
        ...(somethingIsSelected && {
          bgcolor: (theme) =>
            alpha(theme.palette.primary.main, theme.palette.action.activatedOpacity),
        }),
      }}
    >
      <Typography
        sx={{flex: '1 1 100%'}}
        color={somethingIsSelected ? 'inherit' : undefined}
        variant={somethingIsSelected ? 'subtitle1' : 'h6'}
        component="div"
      >
        {somethingIsSelected ? `${props.selectedCount} selected` : props.title}
      </Typography>
      {somethingIsSelected && props.actions.map((action) => (
        <Tooltip title={action.label} key={action.label}>
          <IconButton onClick={action.onPress} aria-label={action.label}>
            {action.icon}
          </IconButton>
        </Tooltip>
      ))}
    </Toolbar>
  );
};
