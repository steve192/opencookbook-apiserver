import {DataGrid} from '@mui/x-data-grid';
import {useEffect, useState} from 'react';
import RestAPI, {User} from './RestAPI';
import {Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, IconButton, MenuItem, Select, TextField, Toolbar, Tooltip, Typography} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ManageAccountsIcon from '@mui/icons-material/ManageAccounts';
import {alpha} from '@mui/material/styles';
import {toast} from 'react-toastify';


export const UsersScreen = () => {
  const [users, setUsers] = useState<User[]>();

  const [selectedItems, setSelectedItems] = useState<string[]>([]);
  const [roleSelectionOpen, setRoleSelectionOpen]= useState(false);


  const deleteSelectedItems = async () => {
    for (const userId of selectedItems) {
      try {
        await RestAPI.deleteUser(userId);
        toast('Deleted user ' + userId, {});
      } catch (e) {
        toast.error('Error deleting user ' + userId, {});
      }
    }
    RestAPI.getAllUsers().then((users) => setUsers(users));
  };

  const activateSelectedUsers = async () => {
    for (const userId of selectedItems) {
      try {
        await RestAPI.activateUser(userId);
        toast('Activated user ' + userId, {});
      } catch (e) {
        toast.error('Error activating user ' + userId, {});
      }
    }
    RestAPI.getAllUsers().then((users) => setUsers(users));
  };

  useEffect(() => {
    RestAPI.getAllUsers().then((users) => setUsers(users));
  }, []);

  const roleSelectionDialog =
    <Dialog
      open={roleSelectionOpen}
      onClose={() => setRoleSelectionOpen(false)}
      PaperProps={{
        component: 'form',
        onSubmit: async (event: React.FormEvent<HTMLFormElement>) => {
          event.preventDefault();
          const formData = new FormData(event.currentTarget);
          const formJson = Object.fromEntries((formData as any).entries());
          let role = formJson.roleToAssign;
          if (role === 'NONE') {
            role = null;
          }

          for (const userId of selectedItems) {
            try {
              await RestAPI.setUserRoles(userId, [role]);
              toast('Set roles of user ' + userId, {});
            } catch (e) {
              toast.error('Error setting roles of user ' + userId, {});
            }
          }
          RestAPI.getAllUsers().then((users) => setUsers(users));
          setRoleSelectionOpen(false);
        },
      }}
    >
      <DialogTitle>Assign roles</DialogTitle>
      <DialogContent>
        <DialogContentText>
          Select the role you want to assign to the selected users
        </DialogContentText>
        <Select
          id="roleToAssign"
          name="roleToAssign"
        >
          <MenuItem value="NONE">None</MenuItem>
          <MenuItem value="ADMIN">Admin</MenuItem>
          <MenuItem value="DEMO">Demo User (Cannot be deleted)</MenuItem>
        </Select>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => setRoleSelectionOpen(false)}>Cancel</Button>
        <Button type="submit">Set roles</Button>
      </DialogActions>
    </Dialog>;

  return (
    <>
      <TableToolbar
        selectedItems={selectedItems}
        onDeletePressed={deleteSelectedItems}
        onActivatePressed={activateSelectedUsers}
        onRoleSelectionPressed={() => setRoleSelectionOpen(true)} />
      <DataGrid
        rows={users ?? []}
        columns={[
          {field: 'userId', headerName: 'userId', width: 70},
          {field: 'emailAddress', headerName: 'emailAddress', width: 300},
          {field: 'createdOn', headerName: 'createdOn', width: 200},
          {field: 'lastChange', headerName: 'lastChange', width: 200},
          {field: 'activated', headerName: 'activated', width: 130},
          {field: 'roles', headerName: 'roles', width: 130},
        ]}
        getRowId={(row) => row.userId}
        initialState={{
          pagination: {
            paginationModel: {page: 0, pageSize: 50},
          },
        }}
        pageSizeOptions={[50, 100]}
        onRowSelectionModelChange={(selectionModel) => setSelectedItems(selectionModel as string[])}
        checkboxSelection
      />
      {roleSelectionDialog}
    </>);
};


function TableToolbar(props: {selectedItems: string[], onDeletePressed: () => void, onActivatePressed: () => void, onRoleSelectionPressed: () => void}) {
  const numSelected = props.selectedItems.length;

  return (
    <Toolbar
      sx={{
        pl: {sm: 2},
        pr: {xs: 1, sm: 1},
        ...(numSelected > 0 && {
          bgcolor: (theme) =>
            alpha(theme.palette.primary.main, theme.palette.action.activatedOpacity),
        }),
      }}
    >
      {numSelected > 0 ? (
        <Typography
          sx={{flex: '1 1 100%'}}
          color="inherit"
          variant="subtitle1"
          component="div"
        >
          {numSelected} selected
        </Typography>
      ) : (
        <Typography
          sx={{flex: '1 1 100%'}}
          variant="h6"
          id="tableTitle"
          component="div"
        >
          Users
        </Typography>
      )}
      {numSelected > 0 && (
        <>
          <Tooltip title="Delete">
            <IconButton onClick={props.onDeletePressed}>
              <DeleteIcon />
            </IconButton>
          </Tooltip>
          <Tooltip title="Activate">
            <IconButton onClick={props.onActivatePressed}>
              <CheckCircleIcon />
            </IconButton>
          </Tooltip>
          <Tooltip title="Set role">
            <IconButton onClick={props.onRoleSelectionPressed}>
              <ManageAccountsIcon />
            </IconButton>
          </Tooltip>
        </>
      ) }
    </Toolbar>
  );
}

