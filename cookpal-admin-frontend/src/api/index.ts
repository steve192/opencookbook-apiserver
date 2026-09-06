import {http} from './HttpClient';
import {
  BringExport,
  Ingredient,
  IngredientDraft,
  MlJob,
  MlQuota,
  MlStatistics,
  Recipe,
  RecipeUpdate,
  SelfInfo,
  Share,
  ShareStatistics,
  User,
  UserAccount,
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
  create: (ingredient: IngredientDraft) => http.post<Ingredient>('/admin/ingredients', ingredient),
  update: (id: number, ingredient: IngredientDraft) =>
    http.put<Ingredient>(`/admin/ingredients/${id}`, ingredient),
  delete: (id: number) => http.delete<void>(`/admin/ingredients/${id}`),
};

export const SharesApi = {
  getAll: () => http.get<Share[]>('/admin/shares'),
  statistics: () => http.get<ShareStatistics>('/admin/shares/statistics'),
  revoke: (shareId: string) => http.delete<void>(`/admin/shares/${shareId}`),
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
export {ApiError} from './HttpClient';
