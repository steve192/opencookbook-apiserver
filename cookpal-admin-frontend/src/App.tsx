import {Alert, Box, Button, CircularProgress, CssBaseline, ThemeProvider} from '@mui/material';
import {Navigate, RouterProvider, createBrowserRouter} from 'react-router-dom';
import {ToastContainer} from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import {AppContextProvider, useInstance, useSession} from './AppContext';
import {MainMenu} from './navigation/MainMenu';
import {ADMIN_BASE_PATH, navigationItems, pathOf} from './navigation/navigationItems';
import {LoginScreen} from './screens/LoginScreen';
import {SetupScreen} from './screens/SetupScreen';
import {adminTheme} from './theme';

const invitationsItem = navigationItems.find((item) => item.route === 'invitations')!;

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
  const instance = useInstance();

  // The next step of the use case: invite the first people.
  const finishSetup = () => {
    router.navigate(pathOf(invitationsItem));
    instance.reload();
  };

  if (instance.error && !instance.data) {
    return (
      <Alert severity="error" sx={{m: 2}} action={<Button color="inherit" onClick={instance.reload}>Retry</Button>}>
        {instance.error}
      </Alert>
    );
  }
  if (checking || !instance.data) {
    return (
      <Box sx={{display: 'flex', height: '100dvh', alignItems: 'center', justifyContent: 'center'}}>
        <CircularProgress />
      </Box>
    );
  }
  // The setup stays up after the account is created: its second step needs the signed in session.
  if (instance.data.setupRequired) {
    return <SetupScreen onFinish={finishSetup} reloadInstance={instance.reload} />;
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
