import {FieldDefinition, FieldImportance, FieldKind} from './types';

export function valueOf<T>(field: FieldDefinition<T>, row: T): unknown {
  return field.value ? field.value(row) : (row as Record<string, unknown>)[field.key];
}

export function importanceOf<T>(field: FieldDefinition<T>): FieldImportance {
  return field.importance ?? 'primary';
}

// Dates in the reader's timezone, lists comma separated.
export function formatFieldValue(kind: FieldKind | undefined, value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  switch (kind) {
    case 'datetime':
      return new Date(String(value)).toLocaleString();
    case 'boolean':
      return value ? 'Yes' : 'No';
    case 'list':
      return Array.isArray(value) ? value.join(', ') : String(value);
    default:
      return String(value);
  }
}

export function formatValue<T>(field: FieldDefinition<T>, row: T): string {
  return formatFieldValue(field.kind, valueOf(field, row));
}

// A whole row as one lowercase string, which is what a search reads.
export function searchableText<T>(fields: FieldDefinition<T>[], row: T): string {
  return fields.map((field) => formatValue(field, row)).join(' ').toLowerCase();
}

export function compareValues(left: unknown, right: unknown): number {
  const leftMissing = left === null || left === undefined;
  const rightMissing = right === null || right === undefined;
  // Checked as a pair: null and undefined are not === each other but rank the same.
  if (leftMissing || rightMissing) {
    return Number(leftMissing) - Number(rightMissing) === 0 ? 0 : (leftMissing ? -1 : 1);
  }
  if (left === right) {
    return 0;
  }
  if (typeof left === 'number' && typeof right === 'number') {
    return left - right;
  }
  if (typeof left === 'boolean' && typeof right === 'boolean') {
    return Number(left) - Number(right);
  }
  return String(left).localeCompare(String(right));
}
