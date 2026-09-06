const AUTH_TOKEN_KEY = 'authToken';
const REFRESH_TOKEN_KEY = 'refreshToken';

export class Session {
  private listeners = new Set<(signedIn: boolean) => void>();

  get authToken(): string | null {
    return localStorage.getItem(AUTH_TOKEN_KEY);
  }

  get refreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  get hasCredentials(): boolean {
    return !!this.authToken && !!this.refreshToken;
  }

  start(authToken: string, refreshToken: string) {
    localStorage.setItem(AUTH_TOKEN_KEY, authToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    this.notify(true);
  }

  renew(authToken: string) {
    localStorage.setItem(AUTH_TOKEN_KEY, authToken);
  }

  end() {
    localStorage.removeItem(AUTH_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    this.notify(false);
  }

  onChange(listener: (signedIn: boolean) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify(signedIn: boolean) {
    this.listeners.forEach((listener) => listener(signedIn));
  }
}

export const session = new Session();
