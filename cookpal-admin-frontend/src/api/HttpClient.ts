import axios, {AxiosError, AxiosInstance, AxiosResponse} from 'axios';
import {Buffer} from 'buffer';
import {Session, session} from './session';

export class ApiError extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
    this.name = 'ApiError';
  }
}

const BACKEND_URL = '';
const API_ROUTE = '/api/v1';

// Signs every request, renews an expired token once and retries, and reports the rest as an
// ApiError.
export class HttpClient {
  private readonly client: AxiosInstance;
  private renewal: Promise<void> | null = null;

  constructor(private readonly tokens: Session, baseURL: string) {
    this.client = axios.create({baseURL});
    this.client.interceptors.request.use((config) => {
      const token = this.tokens.authToken;
      if (token) {
        config.headers.Authorization = 'Bearer ' + token;
      }
      return config;
    });
    this.client.interceptors.response.use(
        (response) => response,
        (error) => this.recover(error),
    );
  }

  async get<T>(path: string, params?: Record<string, unknown>): Promise<T> {
    return this.unwrap(this.client.get<T>(path, {params}));
  }

  async post<T>(path: string, body?: unknown): Promise<T> {
    return this.unwrap(this.client.post<T>(path, body ?? {}));
  }

  async put<T>(path: string, body: unknown): Promise<T> {
    return this.unwrap(this.client.put<T>(path, body));
  }

  async delete<T>(path: string): Promise<T> {
    return this.unwrap(this.client.delete<T>(path));
  }

  async getImageAsDataUri(path: string): Promise<string> {
    const response = await this.client.get(path, {responseType: 'arraybuffer'});
    return 'data:image/jpg;base64,' + Buffer.from(response.data).toString('base64');
  }

  // The one call carrying no token, so it goes around the signing client.
  async signIn(emailAddress: string, password: string): Promise<void> {
    const response = await axios.post(BACKEND_URL + API_ROUTE + '/users/login',
        {emailAddress, password});
    this.tokens.start(response.data.token, response.data.refreshToken);
  }

  private async unwrap<T>(request: Promise<AxiosResponse<T>>): Promise<T> {
    return (await request).data;
  }

  private async recover(error: unknown) {
    const axiosError = error as AxiosError;
    const response = axiosError.response;
    const request = axiosError.config as (typeof axiosError.config & {retried?: boolean});

    if (!response) {
      throw new ApiError('The server cannot be reached');
    }

    // 403 is a denied permission, not an expired token; renewing would not help.
    const expiredToken = response.status === 401 &&
      request && !request.retried && this.tokens.refreshToken;

    if (expiredToken) {
      request.retried = true;
      await this.renewToken();
      return this.client.request(request);
    }

    throw new ApiError(describe(response), response.status);
  }

  /** Concurrent requests that all expire at once must not each renew the token. */
  private async renewToken(): Promise<void> {
    if (!this.renewal) {
      this.renewal = axios
          .post(BACKEND_URL + API_ROUTE + '/users/refreshToken',
              {refreshToken: this.tokens.refreshToken})
          .then((response) => this.tokens.renew(response.data.token))
          .catch(() => {
            this.tokens.end();
            throw new ApiError('The session has ended, please sign in again', 401);
          })
          .finally(() => {
            this.renewal = null;
          });
    }
    return this.renewal;
  }
}

function describe(response: AxiosResponse): string {
  const data = response.data as {message?: string} | string | undefined;
  if (typeof data === 'string' && data.length > 0 && data.length < 300) {
    return data;
  }
  if (data && typeof data === 'object' && data.message) {
    return data.message;
  }
  switch (response.status) {
    case 400: return 'The server refused the request as invalid';
    case 403: return 'You are not allowed to do that';
    case 404: return 'That does not exist (any more)';
    case 409: return 'That conflicts with something that already exists';
    default: return 'The server answered with http ' + response.status;
  }
}

export const http = new HttpClient(session, BACKEND_URL + API_ROUTE);
