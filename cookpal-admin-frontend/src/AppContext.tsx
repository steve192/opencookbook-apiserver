import {createContext} from 'react';

type ContextType = {
    loggedIn: boolean;
    setLoggedIn: (loggedIn: boolean) => void;
}


export const AppContext = createContext<ContextType|undefined>(undefined);
