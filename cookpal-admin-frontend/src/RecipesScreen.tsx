import {DataGrid} from '@mui/x-data-grid';
import {useEffect, useState} from 'react';
import RestAPI, {Recipe} from './RestAPI';


export const RecipesScreen = () => {
  const [recipes, setRecipes] = useState<Recipe[]>();


  useEffect(() => {
    RestAPI.getAllRecipes().then((recipes) => setRecipes(recipes));
  }, []);
  return (<DataGrid
    rows={recipes ?? []}
    columns={[
      {field: 'id', headerName: 'id', width: 70},
      {field: 'title', headerName: 'title', width: 300},
      {field: 'createdOn', headerName: 'createdOn', width: 200},
      {field: 'lastChange', headerName: 'lastChange', width: 200},
    ]}
    initialState={{
      pagination: {
        paginationModel: {page: 0, pageSize: 50},
      },
    }}
    pageSizeOptions={[50, 100]}
    checkboxSelection
  />);
};
