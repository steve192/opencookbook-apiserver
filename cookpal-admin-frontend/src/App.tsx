import {useState} from 'react';
import {RouterProvider, createBrowserRouter} from 'react-router-dom';
import './App.css';
import {AppContext} from './AppContext';
import {LoginScreen} from './LoginScreen';
import {MainMenu} from './MainMenu';
import {RecipesScreen} from './RecipesScreen';
import {UsersScreen} from './UsersScreen';

function App() {
  const [loggedIn, setLoggedIn] = useState(false);

  const router = createBrowserRouter([{
    path: '/',
    element: <MainMenu/>,
    children: [
      {
        path: 'users',
        element: <UsersScreen/>,
      },
      {
        path: 'recipes',
        element: <RecipesScreen/>,
      },
    ],
  }]);

  return (<AppContext.Provider value={{
    loggedIn: loggedIn,
    setLoggedIn: setLoggedIn,
  }}>
    {loggedIn ? <RouterProvider router={router}/> : <LoginScreen/>}
  </AppContext.Provider>
  );
}

export default App;
