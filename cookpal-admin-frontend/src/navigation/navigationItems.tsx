import BlockIcon from '@mui/icons-material/Block';
import CleaningServicesIcon from '@mui/icons-material/CleaningServices';
import DocumentScannerIcon from '@mui/icons-material/DocumentScanner';
import EggIcon from '@mui/icons-material/Egg';
import FileUploadIcon from '@mui/icons-material/FileUpload';
import LocalDiningIcon from '@mui/icons-material/LocalDining';
import LocalPizzaIcon from '@mui/icons-material/LocalPizza';
import PersonIcon from '@mui/icons-material/Person';
import PlaylistAddCheckIcon from '@mui/icons-material/PlaylistAddCheck';
import QuestionMarkIcon from '@mui/icons-material/QuestionMark';
import RuleIcon from '@mui/icons-material/Rule';
import ShareIcon from '@mui/icons-material/Share';
import {ReactNode} from 'react';
import {BringExportsScreen} from '../screens/BringExportsScreen';
import {CatalogueScreen} from '../screens/CatalogueScreen';
import {IngredientCleanupScreen} from '../screens/IngredientCleanupScreen';
import {IngredientsScreen} from '../screens/IngredientsScreen';
import {NameRulesScreen} from '../screens/NameRulesScreen';
import {OcrJobsScreen} from '../screens/OcrJobsScreen';
import {RecipesScreen} from '../screens/RecipesScreen';
import {RelinkScreen} from '../screens/RelinkScreen';
import {SharesScreen} from '../screens/SharesScreen';
import {UnmatchedNamesScreen} from '../screens/UnmatchedNamesScreen';
import {UserCorrectionsScreen} from '../screens/UserCorrectionsScreen';
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
  {route: 'ingredient-cleanup', label: 'Ingredient cleanup', icon: <CleaningServicesIcon />,
    element: <IngredientCleanupScreen />},
  {route: 'catalogue', label: 'Nutrition catalogue', icon: <EggIcon />, element: <CatalogueScreen />},
  {route: 'relinking', label: 'Relinking', icon: <PlaylistAddCheckIcon />, element: <RelinkScreen />},
  {route: 'unmatched-names', label: 'Unmatched names', icon: <QuestionMarkIcon />,
    element: <UnmatchedNamesScreen />},
  {route: 'user-corrections', label: 'User corrections', icon: <RuleIcon />,
    element: <UserCorrectionsScreen />},
  {route: 'name-rules', label: 'Name rules', icon: <BlockIcon />, element: <NameRulesScreen />},
  {route: 'shares', label: 'Shared recipes', icon: <ShareIcon />, element: <SharesScreen />},
  {route: 'ocr-jobs', label: 'Recipe scans', icon: <DocumentScannerIcon />,
    element: <OcrJobsScreen />},
  {route: 'bringexports', label: 'Bring exports', icon: <FileUploadIcon />,
    element: <BringExportsScreen />},
];

export const pathOf = (item: NavigationItem) => ADMIN_BASE_PATH + '/' + item.route;
