import {DataGrid} from '@mui/x-data-grid';
import {useEffect, useState} from 'react';
import RestAPI, {User} from './RestAPI';
import {Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, MenuItem, Select} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ManageAccountsIcon from '@mui/icons-material/ManageAccounts';
import {toast} from 'react-toastify';
import {TableToolbar} from './TableToolbar';


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
        title="Users"
        selectedCount={selectedItems.length}
        actions={[
          {label: 'Delete', icon: <DeleteIcon />, onPress: deleteSelectedItems},
          {label: 'Activate', icon: <CheckCircleIcon />, onPress: activateSelectedUsers},
          {label: 'Set role', icon: <ManageAccountsIcon />, onPress: () => setRoleSelectionOpen(true)},
        ]} />
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
