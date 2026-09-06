import {useCallback, useMemo} from 'react';
import {toast} from 'react-toastify';
import {ApiError} from '../api';

export interface ActionRunner {
  /** False when it failed, so a form can stay open with what was typed still in it. */
  run: (what: string, action: () => Promise<unknown>) => Promise<boolean>;
  /** False unless every item went through. */
  runAll: <T>(what: string, items: T[], action: (item: T) => Promise<unknown>) => Promise<boolean>;
}

// Reloads the list after every action, whether it worked or not.
export function useActionRunner(reload: () => void): ActionRunner {
  const run = useCallback(async (what: string, action: () => Promise<unknown>) => {
    try {
      await action();
      toast.success(what);
      return true;
    } catch (cause) {
      toast.error(what + ' failed: ' + (cause instanceof ApiError ? cause.message : String(cause)));
      return false;
    } finally {
      reload();
    }
  }, [reload]);

  const runAll = useCallback(async <T, >(
    what: string, items: T[], action: (item: T) => Promise<unknown>) => {
    const failures: string[] = [];
    for (const item of items) {
      try {
        await action(item);
      } catch (cause) {
        failures.push(cause instanceof ApiError ? cause.message : String(cause));
      }
    }

    const succeeded = items.length - failures.length;
    if (succeeded > 0) {
      toast.success(what + ': ' + succeeded + ' of ' + items.length);
    }
    if (failures.length > 0) {
      toast.error(what + ' failed for ' + failures.length + ': ' + failures[0]);
    }
    reload();
    return failures.length === 0;
  }, [reload]);

  return useMemo(() => ({run, runAll}), [run, runAll]);
}
