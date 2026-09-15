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

export type LinkSource = 'AUTO' | 'USER' | 'ADMIN';

export interface Ingredient {
  id: number;
  name: string;
  additionalInfo: string | null;
  ownerUserId: number;
  ownerEmailAddress: string;
  catalogueFoodId: number | null;
  catalogueFoodKey: string | null;
  catalogueFoodName: string | null;
  linkSource: LinkSource | null;
  linkConfidence: number | null;
  linkMatcherVersion: number | null;
  linkedAt: string | null;
  excludedFromNutrition: boolean;
  createdOn: string;
  lastChange: string;
}

export interface NameCleanupLine {
  recipeId: number;
  recipeTitle: string;
  amount: number | null;
  unit: string | null;
  newAmount: number | null;
  newUnit: string | null;
}

export interface NameCleanupProposal {
  ingredientId: number;
  ownerEmailAddress: string;
  lastChange: string;
  name: string;
  proposedName: string;
  /** Null if the name holds none. */
  amount: number | null;
  unit: string | null;
  mergesIntoIngredientId: number | null;
  lines: NameCleanupLine[];
}

export interface NameCleanupDecision {
  ingredientId: number;
  name: string;
  lastChange: string;
}

export interface NameCleanupOutcome {
  renamed: number;
  merged: number;
  linesChanged: number;
  skipped: number;
}

export type CatalogueOrigin = 'DATASET' | 'CUSTOM';
export type CatalogueSourceType = 'BLS' | 'FDC';

/** Per 100 g; null when unknown. */
export interface Nutrients {
  energyKcal: number | null;
  energyKj: number | null;
  fat: number | null;
  saturatedFat: number | null;
  carbohydrates: number | null;
  sugar: number | null;
  fibre: number | null;
  protein: number | null;
  salt: number | null;
}

export interface CatalogueFoodSummary {
  id: number;
  catalogueKey: string;
  origin: CatalogueOrigin;
  sourceType: CatalogueSourceType | null;
  sourceName: string | null;
  displayNameDe: string | null;
  displayNameEn: string | null;
  variantOfKey: string | null;
  energyKcal: number | null;
  retired: boolean;
  nameCount: number;
  portionCount: number;
}

export interface CatalogueFoodName {
  languageIsoCode: string;
  name: string;
  display: boolean;
  origin: 'DATASET' | 'ADMIN';
}

export interface CatalogueFoodPortion {
  unitKey: string;
  grams: number;
  origin: 'SOURCE' | 'ESTIMATED' | 'ADMIN';
}

export interface CatalogueFood {
  id: number;
  catalogueKey: string;
  origin: CatalogueOrigin;
  readOnly: boolean;
  sourceType: CatalogueSourceType | null;
  sourceCode: string | null;
  sourceName: string | null;
  variantOfId: number | null;
  variantOfKey: string | null;
  retired: boolean;
  negligible: boolean;
  densityGPerMl: number | null;
  nutrients: Nutrients;
  names: CatalogueFoodName[];
  states: string[];
  portions: CatalogueFoodPortion[];
  linkedIngredients: number;
  createdOn: string;
  lastChange: string;
}

export interface CustomFoodDraft {
  names: {languageIsoCode: string, name: string, display: boolean}[];
  nutrients: Nutrients;
  densityGPerMl: number | null;
  negligible: boolean;
  portions: {unitKey: string, grams: number}[];
}

export type DatasetImportStatus = 'RUNNING' | 'DONE' | 'FAILED';

export interface NutritionDataset {
  label: string;
  checksum: string;
  foods: number;
  attributions: {source: string, text: string, license: string, licenseUrl: string}[];
  sources: {source: string, release: string, file: string, sha256: string}[];
  imports: {
    checksum: string;
    label: string;
    status: DatasetImportStatus;
    startedAt: string;
    finishedAt: string | null;
    report: string | null;
  }[];
}

export interface FoodReference {
  id: number;
  catalogueKey: string;
  displayNameDe: string;
  displayNameEn: string;
  energyKcal: number | null;
  retired: boolean;
}

export type ConfidenceBand = 'SILENT' | 'UNCERTAIN' | 'NONE';

export type RelinkScope =
  | 'NEVER_MATCHED_OR_UNLINKED'
  | 'AUTOMATIC_BELOW_CONFIDENCE'
  | 'ALL_AUTOMATIC'
  | 'RETIRED_FOODS'
  | 'OLDER_MATCHER';

export type RelinkRunStatus = 'PREVIEWED' | 'APPLIED' | 'REVERTED' | 'DISCARDED';

export interface RelinkRun {
  id: number;
  scope: RelinkScope;
  belowConfidence: number | null;
  matcherVersion: number;
  datasetLabel: string | null;
  status: RelinkRunStatus;
  proposalCount: number;
  ingredientCount: number;
  unchangedCount: number;
  appliedCount: number;
  /** Changed after the preview, so left alone by apply. */
  skippedCount: number;
  startedByEmailAddress: string | null;
  createdOn: string;
  appliedAt: string | null;
  revertedAt: string | null;
}

export type RelinkChange = 'NEW_LINK' | 'CHANGED' | 'UNLINKED' | 'CONFIDENCE';
export type RelinkDecision = 'PENDING' | 'ACCEPTED' | 'REJECTED';

export interface RelinkProposal {
  id: number;
  name: string;
  language: string | null;
  ingredientCount: number;
  oldFood: FoodReference | null;
  newFood: FoodReference | null;
  newConfidence: number | null;
  band: ConfidenceBand | null;
  change: RelinkChange;
  decision: RelinkDecision;
}

export type NameRuleKind = 'NEVER_LINK_TO' | 'NOT_A_FOOD';

export interface NameRule {
  id: number;
  name: string;
  kind: NameRuleKind;
  food: FoodReference | null;
  createdByEmailAddress: string | null;
  createdOn: string;
}

export interface UnmatchedName {
  name: string;
  language: string | null;
  userCount: number;
  useCount: number;
  ingredientIds: number[];
  candidates: {food: FoodReference, confidence: number}[];
}

export interface UserCorrection {
  name: string;
  language: string | null;
  /** Null when excluded. */
  food: FoodReference | null;
  userCount: number;
  matcherFood: FoodReference | null;
  matcherConfidence: number | null;
}

export type RecipeNutritionStatus = 'COMPLETE' | 'INCOMPLETE' | 'UNAVAILABLE';

export interface Coverage {
  recipeCount: number;
  recipesByStatus: Record<RecipeNutritionStatus, number>;
  lineCount: number;
  warningLineCount: number;
  /** A line status, or a flag of a resolved line. */
  warningCauses: {key: string, count: number}[];
  namesCausingWarnings: {key: string, count: number}[];
}

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
