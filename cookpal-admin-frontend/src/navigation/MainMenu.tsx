import LogoutIcon from '@mui/icons-material/Logout';
import MenuIcon from '@mui/icons-material/Menu';
import {AppBar, Box, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText, Toolbar, Tooltip, Typography} from '@mui/material';
import {useState} from 'react';
import {Outlet, useLocation, useNavigate} from 'react-router-dom';
import {useSession} from '../AppContext';
import {NAVIGATION_BACKGROUND} from '../theme';
import {navigationItems, pathOf} from './navigationItems';

const DRAWER_WIDTH = 230;

export const MainMenu = () => {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const {emailAddress, signOut} = useSession();

  const current = navigationItems.find((item) => location.pathname.startsWith(pathOf(item)));

  const drawer = (
    <Box sx={{backgroundColor: NAVIGATION_BACKGROUND, height: '100%', color: 'white'}}>
      <Toolbar>
        <Typography variant="h6" noWrap>Cookpal admin</Typography>
      </Toolbar>
      <Divider sx={{borderColor: 'rgba(255,255,255,0.15)'}} />
      <List>
        {navigationItems.map((item) => (
          <ListItemButton
            key={item.route}
            selected={item === current}
            onClick={() => {
              navigate(pathOf(item));
              setDrawerOpen(false);
            }}
            sx={{
              'color': 'white',
              '&.Mui-selected': {backgroundColor: 'rgba(255,255,255,0.12)'},
              '&.Mui-selected:hover': {backgroundColor: 'rgba(255,255,255,0.18)'},
            }}
          >
            <ListItemIcon sx={{color: 'white', minWidth: 40}}>{item.icon}</ListItemIcon>
            <ListItemText primary={item.label} />
          </ListItemButton>
        ))}
      </List>
    </Box>
  );

  return (
    <Box sx={{display: 'flex', height: '100dvh', overflow: 'hidden'}}>
      <AppBar
        position="fixed"
        sx={{
          width: {md: `calc(100% - ${DRAWER_WIDTH}px)`},
          ml: {md: `${DRAWER_WIDTH}px`},
          backgroundColor: NAVIGATION_BACKGROUND,
        }}
      >
        <Toolbar>
          <IconButton
            color="inherit"
            edge="start"
            onClick={() => setDrawerOpen(true)}
            sx={{mr: 2, display: {md: 'none'}}}
            aria-label="Open the menu"
          >
            <MenuIcon />
          </IconButton>
          <Typography variant="h6" noWrap sx={{flexGrow: 1}}>
            {current?.label ?? 'Cookpal admin'}
          </Typography>
          <Typography variant="body2" sx={{mr: 1, display: {xs: 'none', sm: 'block'}}}>
            {emailAddress}
          </Typography>
          <Tooltip title="Sign out">
            <IconButton color="inherit" onClick={signOut} aria-label="Sign out">
              <LogoutIcon />
            </IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>

      <Box component="nav" sx={{width: {md: DRAWER_WIDTH}, flexShrink: {md: 0}}}>
        <Drawer
          variant="temporary"
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          ModalProps={{keepMounted: true}}
          sx={{
            'display': {xs: 'block', md: 'none'},
            '& .MuiDrawer-paper': {boxSizing: 'border-box', width: DRAWER_WIDTH},
          }}
        >
          {drawer}
        </Drawer>
        <Drawer
          variant="permanent"
          open
          sx={{
            'display': {xs: 'none', md: 'block'},
            '& .MuiDrawer-paper': {boxSizing: 'border-box', width: DRAWER_WIDTH},
          }}
        >
          {drawer}
        </Drawer>
      </Box>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          minWidth: 0,
          display: 'flex',
          flexDirection: 'column',
          height: '100dvh',
          p: {xs: 1.5, sm: 3},
          backgroundColor: 'background.default',
        }}
      >
        <Toolbar />
        <Box sx={{flexGrow: 1, minHeight: 0, display: 'flex', flexDirection: 'column'}}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
};
