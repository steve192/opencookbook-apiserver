import {useCallback, useEffect, useRef, useState} from 'react';
import {ApiError} from '../api';

export interface AsyncData<T> {
  data: T;
  loading: boolean;
  error?: string;
  errorStatus?: number;
  reload: () => void;
}

export function useAsyncData<T>(load: () => Promise<T>, initial: T): AsyncData<T> {
  const [data, setData] = useState<T>(initial);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [errorStatus, setErrorStatus] = useState<number>();

  // Reloads overlap: an action reloads the list while the first load is still out. Only the
  // newest request may write, so an earlier one finishing later cannot put back what it saw.
  const newestRequest = useRef(0);

  const reload = useCallback(() => {
    const request = ++newestRequest.current;
    const isNewest = () => request === newestRequest.current;
    setLoading(true);
    load()
        .then((loaded) => {
          if (isNewest()) {
            setData(loaded);
            setError(undefined);
            setErrorStatus(undefined);
          }
        })
        .catch((cause) => {
          if (isNewest()) {
            setError(cause instanceof ApiError ? cause.message : String(cause));
            setErrorStatus(cause instanceof ApiError ? cause.status : undefined);
          }
        })
        .finally(() => {
          if (isNewest()) {
            setLoading(false);
          }
        });
  }, [load]);

  useEffect(reload, [reload]);

  return {data, loading, error, errorStatus, reload};
}

export function useCollection<T>(load: () => Promise<T[]>): AsyncData<T[]> {
  return useAsyncData(load, []);
}
