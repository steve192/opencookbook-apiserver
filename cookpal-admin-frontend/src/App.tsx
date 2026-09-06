import {Box, CircularProgress, CssBaseline, ThemeProvider} from '@mui/material';
import {Navigate, RouterProvider, createBrowserRouter} from 'react-router-dom';
import {ToastContainer} from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import {AppContextProvider, useSession} from './AppContext';
import {MainMenu} from './navigation/MainMenu';
import {ADMIN_BASE_PATH, navigationItems, pathOf} from './navigation/navigationItems';
import {LoginScreen} from './screens/LoginScreen';
import {adminTheme} from './theme';

const router = createBrowserRouter([{
  path: ADMIN_BASE_PATH,
  element: <MainMenu />,
  children: [
    {index: true, element: <Navigate to={pathOf(navigationItems[0])} replace />},
    ...navigationItems.map((item) => ({path: item.route, element: item.element})),
  ],
}]);

const Panel = () => {
  const {signedIn, checking} = useSession();

  if (checking) {
    return (
      <Box sx={{display: 'flex', height: '100dvh', alignItems: 'center', justifyContent: 'center'}}>
        <CircularProgress />
      </Box>
    );
  }
  return signedIn ? <RouterProvider router={router} /> : <LoginScreen />;
};

const App = () => (
  <ThemeProvider theme={adminTheme}>
    <CssBaseline />
    <AppContextProvider>
      <Panel />
      <ToastContainer position="bottom-right" autoClose={4000} newestOnTop theme="colored" />
    </AppContextProvider>
  </ThemeProvider>
);

export default App;
