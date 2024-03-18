import {DataGrid} from '@mui/x-data-grid';
import {useEffect, useState} from 'react';
import RestAPI, {User} from './RestAPI';


export const UsersScreen = () => {
  const [users, setUsers] = useState<User[]>();


  useEffect(() => {
    RestAPI.getAllUsers().then((users) => setUsers(users));
  }, []);
  return (<DataGrid
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
    checkboxSelection
  />);
};
