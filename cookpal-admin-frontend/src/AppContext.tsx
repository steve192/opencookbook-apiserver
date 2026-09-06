import {ReactNode, createContext, useCallback, useContext, useEffect, useState} from 'react';
import {AccountApi, ApiError, SelfInfo} from './api';
import {session} from './api/session';

interface AppSession {
  signedIn: boolean;
  checking: boolean;
  emailAddress?: string;
  signIn: (emailAddress: string, password: string) => Promise<void>;
  signOut: () => void;
}

const AppContext = createContext<AppSession | undefined>(undefined);

export const useSession = (): AppSession => {
  const context = useContext(AppContext);
  if (!context) {
    throw new Error('There is no session outside of AppContextProvider');
  }
  return context;
};

export const AppContextProvider = (props: {children: ReactNode}) => {
  const [signedIn, setSignedIn] = useState(false);
  const [checking, setChecking] = useState(session.hasCredentials);
  const [emailAddress, setEmailAddress] = useState<string>();

  // A reload should not ask for the password again while the tokens are still good.
  useEffect(() => {
    if (!session.hasCredentials) {
      return;
    }
    AccountApi.self()
        .then((self) => {
          requireAdministrator(self);
          setEmailAddress(self.email);
          setSignedIn(true);
        })
        .catch(() => session.end())
        .finally(() => setChecking(false));
  }, []);

  // The http layer ends the session when a token cannot be renewed.
  useEffect(() => session.onChange((stillSignedIn) => {
    if (!stillSignedIn) {
      setSignedIn(false);
      setEmailAddress(undefined);
    }
  }), []);

  const signIn = useCallback(async (address: string, password: string) => {
    await AccountApi.signIn(address, password);
    try {
      const self = await AccountApi.self();
      requireAdministrator(self);
      setEmailAddress(self.email);
      setSignedIn(true);
    } catch (cause) {
      session.end();
      throw cause;
    }
  }, []);

  // Nothing else to do: ending the session notifies the listener above, which clears the state.
  const signOut = useCallback(() => session.end(), []);

  return (
    <AppContext.Provider value={{signedIn, checking, emailAddress, signIn, signOut}}>
      {props.children}
    </AppContext.Provider>
  );
};

// Refused at the door rather than by every screen in turn.
function requireAdministrator(self: SelfInfo) {
  if (!self.roles?.includes('ADMIN')) {
    throw new ApiError('That account is not an administrator', 403);
  }
}
