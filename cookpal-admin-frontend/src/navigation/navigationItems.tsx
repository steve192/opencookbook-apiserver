import DocumentScannerIcon from '@mui/icons-material/DocumentScanner';
import FileUploadIcon from '@mui/icons-material/FileUpload';
import LocalDiningIcon from '@mui/icons-material/LocalDining';
import LocalPizzaIcon from '@mui/icons-material/LocalPizza';
import PersonIcon from '@mui/icons-material/Person';
import ShareIcon from '@mui/icons-material/Share';
import {ReactNode} from 'react';
import {BringExportsScreen} from '../screens/BringExportsScreen';
import {IngredientsScreen} from '../screens/IngredientsScreen';
import {OcrJobsScreen} from '../screens/OcrJobsScreen';
import {RecipesScreen} from '../screens/RecipesScreen';
import {SharesScreen} from '../screens/SharesScreen';
import {UsersScreen} from '../screens/UsersScreen';

/** Where the api server mounts the panel. Kept in step with `base` in vite.config.ts. */
export const ADMIN_BASE_PATH = '/admin';

export interface NavigationItem {
  /** The route under ADMIN_BASE_PATH. */
  route: string;
  label: string;
  icon: ReactNode;
  element: ReactNode;
}

// The one list: the router builds its pages from it and the drawer builds its entries.
export const navigationItems: NavigationItem[] = [
  {route: 'users', label: 'Users', icon: <PersonIcon />, element: <UsersScreen />},
  {route: 'recipes', label: 'Recipes', icon: <LocalDiningIcon />, element: <RecipesScreen />},
  {route: 'ingredients', label: 'Ingredients', icon: <LocalPizzaIcon />,
    element: <IngredientsScreen />},
  {route: 'shares', label: 'Shared recipes', icon: <ShareIcon />, element: <SharesScreen />},
  {route: 'ocr-jobs', label: 'Recipe scans', icon: <DocumentScannerIcon />,
    element: <OcrJobsScreen />},
  {route: 'bringexports', label: 'Bring exports', icon: <FileUploadIcon />,
    element: <BringExportsScreen />},
];

export const pathOf = (item: NavigationItem) => ADMIN_BASE_PATH + '/' + item.route;
