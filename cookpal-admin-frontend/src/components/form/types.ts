/** A pairList row: the two edited properties, plus any id the entity carries along. */
export type PairListEntry = {[key: string]: string | number | undefined};

export type FormValue =
  | string
  | number
  | boolean
  | null
  | undefined
  | string[]
  | PairListEntry[];

// A mapped type rather than an index signature, so an interface can satisfy it. The base
// no-unused-vars rule does not see a mapped type's parameter as used.
// eslint-disable-next-line no-unused-vars
export type FormValues<V> = {[Field in keyof V]: FormValue};

interface CommonFieldDefinition<V> {
  name: keyof V & string;
  label: string;
  required?: boolean;
  helperText?: string;
  section?: string;
}

/** Everything the plain inputs cover: one value, edited as itself. */
export interface SimpleFieldDefinition<V> extends CommonFieldDefinition<V> {
  type: 'text' | 'multiline' | 'number' | 'switch' | 'stringList';
}

export interface SelectFieldDefinition<V> extends CommonFieldDefinition<V> {
  type: 'select';
  options: {value: string, label: string}[];
}

export interface PairListFieldDefinition<V> extends CommonFieldDefinition<V> {
  type: 'pairList';
  /** The two properties of each entry, and what to call them. */
  pairKeys: [string, string];
  pairLabels: [string, string];
}

// A union rather than one interface of optional extras, so a select without options or a
// pairList without keys does not typecheck in the first place.
export type FormFieldDefinition<V> =
  | SimpleFieldDefinition<V>
  | SelectFieldDefinition<V>
  | PairListFieldDefinition<V>;
