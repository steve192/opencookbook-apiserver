import {RecipeDiet} from '../api';

export const DIETS: RecipeDiet[] = ['VEGAN', 'VEGETARIAN', 'MEAT'];

export const DIET_LABELS: Record<RecipeDiet, string> = {
  VEGAN: 'Vegan',
  VEGETARIAN: 'Vegetarian',
  MEAT: 'Meat',
};

export const DIET_COLORS: Record<RecipeDiet, 'success' | 'warning' | 'error'> = {
  VEGAN: 'success',
  VEGETARIAN: 'warning',
  MEAT: 'error',
};

export const isDiet = (value: string): value is RecipeDiet => value in DIET_COLORS;
