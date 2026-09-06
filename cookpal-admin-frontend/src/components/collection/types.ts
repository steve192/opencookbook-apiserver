import {ReactNode} from 'react';

export type FieldKind = 'text' | 'number' | 'boolean' | 'datetime' | 'list';

/**
 * How much room a field earns. The surfaces are nested: the details panel shows every field,
 * the table shows primary and secondary ones, the phone cards only the primary ones. A
 * secondary column also starts hidden when the table is too narrow to hold everything.
 */
export type FieldImportance = 'primary' | 'secondary' | 'reference';

/** Drives the table column, the card line and the details panel alike. */
export interface FieldDefinition<T> {
  key: string;
  label: string;
  kind?: FieldKind;
  width?: number;
  value?: (row: T) => unknown;
  render?: (row: T) => ReactNode;
  importance?: FieldImportance;
}

export interface RowAction<T> {
  label: string;
  icon: ReactNode;
  onRun: (row: T) => unknown;
  hidden?: (row: T) => boolean;
  confirm?: (row: T) => string;
  color?: 'inherit' | 'primary' | 'error';
}

export interface BulkAction<T> {
  label: string;
  icon: ReactNode;
  onRun: (rows: T[]) => unknown;
  confirm?: (rows: T[]) => string;
  destructive?: boolean;
}

export type RowId = string | number;
