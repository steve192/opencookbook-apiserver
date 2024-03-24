import {useState} from 'react';
import {RouterProvider, createBrowserRouter} from 'react-router-dom';
import './App.css';
import {AppContext} from './AppContext';
import {LoginScreen} from './LoginScreen';
import {MainMenu} from './MainMenu';
import {RecipesScreen} from './RecipesScreen';
import {UsersScreen} from './UsersScreen';
import {BringExportScreen} from './BringExportScreen';
import {ToastContainer} from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';

function App() {
  const [loggedIn, setLoggedIn] = useState(false);

  const router = createBrowserRouter([{
    path: '/admin/',
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
      {
        path: 'ingredients',
      },
      {
        path: 'bringexports',
        element: <BringExportScreen/>,
      },
    ],
  }]);

  return (<AppContext.Provider value={{
    loggedIn: loggedIn,
    setLoggedIn: setLoggedIn,
  }}>
    {loggedIn ? <RouterProvider router={router}/> : <LoginScreen/>}
    <ToastContainer
      position="top-right"
      autoClose={5000}
      hideProgressBar={false}
      newestOnTop={false}
      closeOnClick
      rtl={false}
      pauseOnFocusLoss
      draggable
      pauseOnHover
      theme="dark"
    />
  </AppContext.Provider>
  );
}

export default App;
