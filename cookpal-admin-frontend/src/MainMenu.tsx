import {Edit} from '@mui/icons-material';
import FileUploadIcon from '@mui/icons-material/FileUpload';
import LocalDiningIcon from '@mui/icons-material/LocalDining';
import LocalPizzaIcon from '@mui/icons-material/LocalPizza';
import MenuIcon from '@mui/icons-material/Menu';
import PersonIcon from '@mui/icons-material/Person';
import {AppBar, Box, CssBaseline, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText, Toolbar, Typography} from '@mui/material';
import {useState} from 'react';
import {Outlet, useNavigate} from 'react-router-dom';

export const MainMenu = () => {
  const drawWidth = 220;
  const [mobileViewOpen, setMobileViewOpen] = useState(false);
  const navigate = useNavigate();

  const handleToggle = () => {
    setMobileViewOpen(!mobileViewOpen);
  };

  const responsiveDrawer = (
    <div
      style={{
        backgroundColor: '#09212E',
        height: '100%',
      }}
    >
      <Toolbar />
      <Divider />
      <Typography
        sx={{
          textAlign: 'center',
          pt: 4,
          color: 'white',
          fontSize: 20,
        }}
      >
        Cookpal Admin Panel
      </Typography>
      <List sx={{backgroundColor: '#09212E'}}>
        <ListItemButton onClick={() => navigate('/admin/users')} sx={{color: 'white'}}>
          <ListItemIcon sx={{color: 'white'}}>
            <PersonIcon />
          </ListItemIcon>
          <ListItemText primary={'Users'} />
        </ListItemButton>
        <ListItemButton onClick={() => navigate('/admin/recipes')} sx={{color: 'white'}}>
          <ListItemIcon sx={{color: 'white'}}>
            <LocalDiningIcon />
          </ListItemIcon>
          <ListItemText primary={'Recipes'} />
        </ListItemButton>
        <ListItemButton onClick={() => navigate('/admin/ingredients')} sx={{color: 'white'}}>
          <ListItemIcon sx={{color: 'white'}}>
            <LocalPizzaIcon />
          </ListItemIcon>
          <ListItemText primary={'Ingredients'} />
        </ListItemButton>
        <ListItemButton onClick={() => navigate('/admin/bringexports')} sx={{color: 'white'}}>
          <ListItemIcon sx={{color: 'white'}}>
            <FileUploadIcon />
          </ListItemIcon>
          <ListItemText primary={'Bring Exports'} />
        </ListItemButton>
      </List>
      <Divider />
      <List>
        <ListItemButton sx={{color: 'white'}}>
          <ListItemIcon sx={{color: 'white'}}>
            <Edit />
          </ListItemIcon>
          <ListItemText primary={'System Settings'} />
        </ListItemButton>
      </List>
    </div>
  );

  return (
    <Box sx={{display: 'flex', height: '100vh', overflow: 'hidden'}}>
      <CssBaseline />
      <AppBar
        position="fixed"
        sx={{
          width: {sm: `calc(100% - ${drawWidth}px)`},
          ml: {sm: `${drawWidth}px`},
          backgroundColor: 'rgb(9, 33, 46)',
        }}
      >
        <Toolbar>
          <IconButton
            color="inherit"
            edge="start"
            onClick={handleToggle}
            sx={{mr: 2, display: {sm: 'none'}}}
          >
            <MenuIcon />
          </IconButton>
          <Typography variant="h6">Cookpal Admin Panel</Typography>
        </Toolbar>
      </AppBar>
      <Box
        component="nav"
        sx={{
          width: {sm: drawWidth},
          flexShrink: {sm: 0},
        }}
      >
        <Drawer
          variant="temporary"
          open={mobileViewOpen}
          onClose={handleToggle}
          ModalProps={{
            keepMounted: true,
          }}
          sx={{
            'display': {xs: 'block', sm: 'none'},
            '& .MuiDrawer-paper': {
              boxSizing: 'border-box',
              width: drawWidth,
            },
          }}
        >
          {responsiveDrawer}
        </Drawer>
        <Drawer
          variant="permanent"
          sx={{
            'display': {xs: 'none', sm: 'block'},
            '& .MuiDrawer-paper': {
              boxSizing: 'border-box',
              width: drawWidth,
            },
          }}
          open
        >
          {responsiveDrawer}
        </Drawer>
      </Box>
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: 3,
          width: {sm: `calc(100% - ${drawWidth}px)`},
          height: '100vh',
          overflow: 'auto',
          backgroundColor: 'white',
        }}
      >
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
};
