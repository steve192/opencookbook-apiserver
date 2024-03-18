import {DataGrid} from '@mui/x-data-grid';
import {useEffect, useState} from 'react';
import RestAPI, {BringExport} from './RestAPI';


export const BringExportScreen = () => {
  const [bringExports, setBringExports] = useState<BringExport[]>();


  useEffect(() => {
    RestAPI.getAllBringExports().then((exports) => setBringExports(exports));
  }, []);
  return (<DataGrid
    rows={bringExports ?? []}
    columns={[
      {field: 'id', headerName: 'id', width: 70},
      {field: 'owner', headerName: 'owner', width: 300},
      {field: 'createdOn', headerName: 'createdOn', width: 200},
      {field: 'lastChange', headerName: 'lastChange', width: 200},
      {field: 'baseAmount', headerName: 'baseAmount', width: 130},
    ]}
    getRowId={(row) => row.id}
    initialState={{
      pagination: {
        paginationModel: {page: 0, pageSize: 50},
      },
    }}
    pageSizeOptions={[50, 100]}
    checkboxSelection
  />);
};
