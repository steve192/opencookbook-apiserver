export type Role = 'ADMIN' | 'DEMO';

export interface UserAccount {
  userId: number;
  emailAddress: string;
  activated: boolean;
  roles: Role | null;
  createdOn: string;
  lastChange: string;
}

/** A row of the account list, which is the only place the counts are reported. */
export interface User extends UserAccount {
  recipeCount: number;
  ingredientCount: number;
}

export interface UserUpdate {
  emailAddress: string;
  activated: boolean;
  role: Role | null;
}

export type RecipeType = 'VEGAN' | 'VEGETARIAN' | 'MEAT';

export interface Recipe {
  id: number;
  title: string;
  ownerUserId: number | null;
  ownerEmailAddress: string | null;
  servings: number;
  preparationTime: number | null;
  totalTime: number | null;
  recipeType: RecipeType | null;
  recipeSource: string | null;
  ingredientCount: number;
  stepCount: number;
  ingredients: string[];
  preparationSteps: string[];
  imageUuids: string[];
  recipeGroups: string[];
  createdOn: string;
  lastChange: string;
}

export interface RecipeUpdate {
  title: string;
  servings: number | null;
  preparationTime: number | null;
  totalTime: number | null;
  recipeType: RecipeType | null;
  preparationSteps: string[];
}

/** A type rather than an interface so it can be edited as a pairList row. */
export type AlternativeName = {
  id?: number;
  languageIsoCode: string;
  alternativeName: string;
};

export interface Ingredient {
  id: number;
  name: string;
  additionalInfo: string | null;
  publicIngredient: boolean;
  ownerUserId: number | null;
  ownerEmailAddress: string | null;
  aliasForId: number | null;
  aliasForName: string | null;
  alternativeNames: AlternativeName[];
  nutrientsEnergy: number | null;
  nutrientsFat: number | null;
  nutrientsSaturatedFat: number | null;
  nutrientsCarbohydrates: number | null;
  nutrientsSugar: number | null;
  nutrientsProtein: number | null;
  nutrientsSalt: number | null;
  createdOn: string;
  lastChange: string;
}

/** No id: the path says which ingredient. */
export type IngredientDraft = Omit<Ingredient,
  'id' | 'createdOn' | 'lastChange' | 'publicIngredient' | 'ownerUserId' | 'ownerEmailAddress' |
  'aliasForId' | 'aliasForName'>;

export interface Share {
  shareId: string;
  shareUrl: string;
  recipeId: number;
  recipeTitle: string;
  ownerUserId: number;
  ownerEmailAddress: string;
  createdOn: string;
  expiresAt: string;
  expired: boolean;
  accessCount: number;
}

export interface ShareStatistics {
  totalShares: number;
  totalAccesses: number;
  expiringSoon: number;
}

export interface BringExport {
  id: string;
  ownerUserId: number | null;
  ownerEmailAddress: string | null;
  baseAmount: number;
  ingredients: string[];
  ingredientCount: number;
  createdOn: string;
  expiresAt: string | null;
  expired: boolean;
}

export type MlJobStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface MlJob {
  id: string;
  ownerUserId: number | null;
  ownerEmailAddress: string | null;
  jobType: string;
  status: MlJobStatus;
  remoteJobId: string | null;
  queuePosition: number | null;
  errorCode: string | null;
  errorMessage: string | null;
  errorRetryable: boolean;
  countsTowardsQuota: boolean;
  finished: boolean;
  hasResult: boolean;
  createdOn: string;
  finishedAt: string | null;
}

export interface MlStatistics {
  available: boolean;
  totalJobs: number;
  jobsByStatus: Record<MlJobStatus, number>;
  recentFailures: {
    id: string;
    jobType: string;
    code: string;
    message: string;
    failedAt: string;
  }[];
}

export interface MlQuota {
  dailyLimit: number;
  users: {
    userId: number;
    emailAddress: string;
    used: number;
    exhausted: boolean;
  }[];
}

export interface SelfInfo {
  email: string;
  roles: string[];
}
