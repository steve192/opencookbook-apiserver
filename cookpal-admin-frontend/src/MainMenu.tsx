import {Edit} from '@mui/icons-material';
import {AppBar, Box, CssBaseline, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText, Toolbar, Typography} from '@mui/material';
import {useState} from 'react';
import {Link, Outlet, useNavigate} from 'react-router-dom';
import PersonIcon from '@mui/icons-material/Person';
import LocalDiningIcon from '@mui/icons-material/LocalDining';
import LocalPizzaIcon from '@mui/icons-material/LocalPizza';
import MenuIcon from '@mui/icons-material/Menu';

export const MainMenu = () => {
  const drawWidth = 220;
  const [mobileViewOpen, setMobileViewOpen] = useState(false);
  const navigate = useNavigate();

  const handleToggle = () => {
    setMobileViewOpen(!mobileViewOpen);
  };


  const responsiveDrawer = (
    <div style={{backgroundColor: '#09212E',
      height: '100%'}}>
      <Toolbar />
      <Divider />
      <Typography
        sx={{textAlign: 'center', pt: 4,
          color: 'white', fontSize: 20}}
      >
                  Cookpal Admin Panel
      </Typography>
      <List sx={{backgroundColor: '#09212E'}}>
        <ListItemButton sx={{color: 'white'}}>
          <ListItemIcon sx={{color: 'white'}}>
            {<PersonIcon />}
          </ListItemIcon>
          <Link to="/users"><ListItemText primary={'Users'} /> </Link>
        </ListItemButton>
        <ListItemButton onClick={() => navigate('/recipes')} sx={{color: 'white'}}>
          <ListItemIcon sx={{color: 'white'}}>
            {<LocalDiningIcon />}
          </ListItemIcon>
          <ListItemText primary={'Recipes'} />
        </ListItemButton>
        <ListItemButton sx={{color: 'white'}}>
          <ListItemIcon sx={{color: 'white'}}>
            {<LocalPizzaIcon />}
          </ListItemIcon>
          <ListItemText primary={'Ingredients'} />
        </ListItemButton>
      </List>
      <Divider />
      <List>
        <ListItemButton sx={{color: 'white'}}>
          <ListItemIcon sx={{color: 'white'}}>
            {<Edit />}
          </ListItemIcon>
          <ListItemText primary={'System Settings'} />
        </ListItemButton>
      </List>
    </div>
  );

  return (<div>
    <div>
      <Box sx={{display: 'flex'}}>
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
            <Typography variant="h6">
                          Cookpal Admin Panel
            </Typography>
          </Toolbar>
        </AppBar>
        <Box
          component="nav"
          sx={{width: {sm: drawWidth},
            flexShrink: {sm: 0}}}
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
          }}
        >
          <Toolbar />
          <Outlet/>
        </Box>
      </Box>
    </div>
  </div>);
};
