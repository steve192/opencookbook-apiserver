export default class AppPersistence {
  private static authToken: string;
  private static refreshToken: string;

  static async setAuthToken(token: string) {
    AppPersistence.authToken = token;
    localStorage.setItem('authToken', token); // Save to localStorage
  }

  static async getAuthToken(): Promise<string | null> {
    if (!AppPersistence.authToken) {
      AppPersistence.authToken = localStorage.getItem('authToken') || ''; // Retrieve from localStorage
    }
    return AppPersistence.authToken;
  }

  static async setRefreshToken(token: string) {
    AppPersistence.refreshToken = token;
    localStorage.setItem('refreshToken', token); // Save to localStorage
  }

  static async getRefreshToken(): Promise<string | null> {
    if (!AppPersistence.refreshToken) {
      AppPersistence.refreshToken = localStorage.getItem('refreshToken') || ''; // Retrieve from localStorage
    }
    return AppPersistence.refreshToken;
  }

  static async getBackendURL(): Promise<string> {
    // return 'https://beta.cookpal.io';
    return '';
  }

  static getApiRoute(): string {
    return '/api/v1';
  }
}
