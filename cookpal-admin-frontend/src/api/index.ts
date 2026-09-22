import {http} from './HttpClient';
import {
  BringExport,
  CatalogueFood,
  CatalogueFoodSummary,
  Coverage,
  CustomFoodDraft,
  Ingredient,
  MlJob,
  MlQuota,
  MlStatistics,
  NameCleanupDecision,
  NameCleanupOutcome,
  NameCleanupProposal,
  NameRule,
  NameRuleKind,
  NutritionDataset,
  Recipe,
  RecipeUpdate,
  ClassificationDecision,
  ClassificationKind,
  ClassificationProposal,
  ClassificationRun,
  ClassificationScope,
  RecipeDiet,
  RelinkDecision,
  RelinkProposal,
  RelinkRun,
  RelinkScope,
  SelfInfo,
  Household,
  Share,
  ShareStatistics,
  User,
  UserAccount,
  UnmatchedName,
  UserCorrection,
  UserUpdate,
} from './types';

export const UsersApi = {
  getAll: () => http.get<User[]>('/admin/users'),
  update: (userId: number, update: UserUpdate) =>
    http.put<UserAccount>(`/admin/users/${userId}`, update),
  activate: (userId: number) => http.post<UserAccount>(`/admin/users/${userId}/activate`),
  deactivate: (userId: number) => http.post<UserAccount>(`/admin/users/${userId}/deactivate`),
  sendPasswordReset: (userId: number) => http.post<void>(`/admin/users/${userId}/password-reset`),
  delete: (userId: number) => http.delete<void>(`/admin/users/${userId}`),
};

export const RecipesApi = {
  getAll: () => http.get<Recipe[]>('/admin/recipes'),
  update: (id: number, update: RecipeUpdate) => http.put<Recipe>(`/admin/recipes/${id}`, update),
  delete: (id: number) => http.delete<void>(`/admin/recipes/${id}`),
  thumbnail: (uuid: string) => http.getImageAsDataUri(`/recipes-images/thumbnail/${uuid}`),
  image: (uuid: string) => http.getImageAsDataUri(`/recipes-images/${uuid}`),
};

export const IngredientsApi = {
  getAll: () => http.get<Ingredient[]>('/admin/ingredients'),
  delete: (id: number) => http.delete<void>(`/admin/ingredients/${id}`),
  link: (id: number, catalogueFoodId: number) =>
    http.put<Ingredient>(`/admin/ingredients/${id}/link`, {catalogueFoodId}),
  exclude: (id: number) => http.put<Ingredient>(`/admin/ingredients/${id}/link`, {excluded: true}),
  nameCleanupPreview: () => http.get<NameCleanupProposal[]>('/admin/ingredients/name-cleanup'),
  applyNameCleanup: (decisions: NameCleanupDecision[]) =>
    http.post<NameCleanupOutcome>('/admin/ingredients/name-cleanup/apply', {decisions}),
};

export const CatalogueApi = {
  getFoods: () => http.get<CatalogueFoodSummary[]>('/admin/catalogue/foods'),
  getFood: (id: number) => http.get<CatalogueFood>(`/admin/catalogue/foods/${id}`),
  create: (food: CustomFoodDraft) => http.post<CatalogueFood>('/admin/catalogue/foods', food),
  update: (id: number, food: CustomFoodDraft) =>
    http.put<CatalogueFood>(`/admin/catalogue/foods/${id}`, food),
  delete: (id: number) => http.delete<void>(`/admin/catalogue/foods/${id}`),
  addName: (id: number, languageIsoCode: string, name: string) =>
    http.post<CatalogueFood>(`/admin/catalogue/foods/${id}/names`, {languageIsoCode, name}),
  removeName: (id: number, languageIsoCode: string, name: string) =>
    http.delete<CatalogueFood>(`/admin/catalogue/foods/${id}/names`, {languageIsoCode, name}),
  merge: (id: number, targetId: number) =>
    http.post<CatalogueFood>(`/admin/catalogue/foods/${id}/merge`, {targetId}),
  classify: (id: number, dietClass: RecipeDiet | null) =>
    http.put<CatalogueFood>(`/admin/catalogue/foods/${id}/diet-class`, {dietClass}),
  dataset: () => http.get<NutritionDataset>('/admin/catalogue/dataset'),
};

export const ClassificationApi = {
  getRuns: () => http.get<ClassificationRun[]>('/admin/recipes/classification-runs'),
  preview: (kind: ClassificationKind, scope: ClassificationScope) =>
    http.post<ClassificationRun>('/admin/recipes/classification-runs', {kind, scope}),
  getRun: (id: number) => http.get<ClassificationRun>(`/admin/recipes/classification-runs/${id}`),
  getProposals: (id: number) =>
    http.get<ClassificationProposal[]>(`/admin/recipes/classification-runs/${id}/proposals`),
  decide: (id: number, proposalIds: number[], decision: ClassificationDecision) =>
    http.post<ClassificationProposal[]>(`/admin/recipes/classification-runs/${id}/decisions`,
        {proposalIds, decision}),
  apply: (id: number) => http.post<ClassificationRun>(`/admin/recipes/classification-runs/${id}/apply`),
  revert: (id: number) => http.post<ClassificationRun>(`/admin/recipes/classification-runs/${id}/revert`),
  discard: (id: number) => http.post<ClassificationRun>(`/admin/recipes/classification-runs/${id}/discard`),
};

export const RelinkApi = {
  getRuns: () => http.get<RelinkRun[]>('/admin/nutrition/relink-runs'),
  preview: (scope: RelinkScope, belowConfidence: number | null) =>
    http.post<RelinkRun>('/admin/nutrition/relink-runs', {scope, belowConfidence}),
  getRun: (id: number) => http.get<RelinkRun>(`/admin/nutrition/relink-runs/${id}`),
  getProposals: (id: number) => http.get<RelinkProposal[]>(`/admin/nutrition/relink-runs/${id}/proposals`),
  decide: (id: number, proposalIds: number[], decision: RelinkDecision, remember: boolean) =>
    http.post<RelinkProposal[]>(`/admin/nutrition/relink-runs/${id}/decisions`, {proposalIds, decision, remember}),
  getSampleRecipes: (id: number, proposalId: number) =>
    http.get<Recipe[]>(`/admin/nutrition/relink-runs/${id}/proposals/${proposalId}/recipes`),
  apply: (id: number) => http.post<RelinkRun>(`/admin/nutrition/relink-runs/${id}/apply`),
  revert: (id: number) => http.post<RelinkRun>(`/admin/nutrition/relink-runs/${id}/revert`),
  discard: (id: number) => http.post<RelinkRun>(`/admin/nutrition/relink-runs/${id}/discard`),
};

export const NameRulesApi = {
  getAll: () => http.get<NameRule[]>('/admin/nutrition/name-rules'),
  add: (name: string, kind: NameRuleKind, catalogueFoodId: number | null) =>
    http.post<NameRule>('/admin/nutrition/name-rules', {name, kind, catalogueFoodId}),
  delete: (id: number) => http.delete<void>(`/admin/nutrition/name-rules/${id}`),
};

export const NutritionReportsApi = {
  unmatchedNames: () => http.get<UnmatchedName[]>('/admin/nutrition/unmatched-names'),
  userCorrections: () => http.get<UserCorrection[]>('/admin/nutrition/user-corrections'),
  namesExport: () => http.get<string>('/admin/nutrition/names-export'),
  coverage: () => http.get<Coverage>('/admin/nutrition/coverage'),
};

export const SharesApi = {
  getAll: () => http.get<Share[]>('/admin/shares'),
  statistics: () => http.get<ShareStatistics>('/admin/shares/statistics'),
  revoke: (shareId: string) => http.delete<void>(`/admin/shares/${shareId}`),
};

export const HouseholdsApi = {
  getAll: () => http.get<Household[]>('/admin/households'),
  dissolve: (householdId: string) => http.delete<void>(`/admin/households/${householdId}`),
};

export const BringExportsApi = {
  getAll: () => http.get<BringExport[]>('/admin/bringexports'),
  delete: (id: string) => http.delete<void>(`/admin/bringexports/${id}`),
};

export const MlApi = {
  statistics: () => http.get<MlStatistics>('/admin/ml'),
  quota: () => http.get<MlQuota>('/admin/ml/quota'),
  resetQuota: (userId: number) => http.post<MlQuota>(`/admin/ml/quota/${userId}/reset`),
  getJobs: () => http.get<MlJob[]>('/admin/ml/jobs'),
  resetJob: (id: string) => http.post<MlJob>(`/admin/ml/jobs/${id}/reset`),
  deleteJob: (id: string) => http.delete<void>(`/admin/ml/jobs/${id}`),
};

export const AccountApi = {
  signIn: (emailAddress: string, password: string) => http.signIn(emailAddress, password),
  self: () => http.get<SelfInfo>('/users/self'),
};

export * from './types';
export {ApiError, errorMessage} from './HttpClient';
