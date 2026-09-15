import {FoodReference} from '../api';

// "Zucker (400 kcal)"
export const describeFood = (food: FoodReference | null): string => {
  if (!food) {
    return '-';
  }
  const energy = food.energyKcal === null ? '' : ' (' + Math.round(food.energyKcal) + ' kcal)';
  return (food.displayNameDe ?? food.catalogueKey) + energy + (food.retired ? ', retired' : '');
};

export const formatConfidence = (confidence: number | null): string =>
  confidence === null ? '-' : Math.round(confidence * 100) + ' %';
