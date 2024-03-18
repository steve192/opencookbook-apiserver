
export default class AppPersistence {
  private static authToken: string;
  private static refreshToken: string;

  static async setAuthToken(token: string) {
    AppPersistence.authToken = token;
  }

  static async getAuthToken(): Promise<string | null> {
    return AppPersistence.authToken;
  }


  static async setRefreshToken(token: string) {
    AppPersistence.refreshToken = token;
  }

  static async getRefreshToken(): Promise<string | null> {
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
