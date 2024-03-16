import axios, {AxiosError, AxiosRequestConfig, AxiosRequestHeaders} from 'axios';
import {Buffer} from 'buffer';
import AppPersistence from './services/AppPersistence';


export interface InstanceInfo {
  termsOfService: string;
}

export interface UserInfo {
  email: string;
}

export interface User {
  createdOn: string;
  lastChange: string;
  userId: string;
  emailAddress: string;
  activated:boolean;
  roles: string;
}

export interface Recipe {
  id: string;
  createdOn: string;
  lastChange: string;
  title: string;
  neededIngredients: IngredientNeed[];
  preparationSteps: string[];
  images: string[];
  servings: number;
  recipeGroups: string; // TODO
  preparationTime: number | null;
  totalTime: number | null;
  recipeType: string | null;
}

export interface IngredientNeed {
  createdOn: string;
  lastChange: string;
  id: string;
  amount: string;
  unit: string;
  ingredient: Ingredient
}

export interface Ingredient {
  createdOn: string;
  lastChange: string;
  id: number;
  name: string;
  owner: User;
  publicIngredient: boolean;
}

class RestAPI {
  static async getAllRecipes(): Promise<Recipe[]> {
    const response = await this.get('/admin/recipes');
    return response?.data;
  }
  static async getAllUsers(): Promise<User[]> {
    const response = await this.get('/admin/users');
    return response?.data;
  }
  static async getUserInfo(): Promise<UserInfo> {
    const response = await this.get('/users/self');
    return response?.data;
  }
  static async refreshToken() {
    const response = await axios.post(await this.url('/users/refreshToken'), {refreshToken: await AppPersistence.getRefreshToken()});
    await AppPersistence.setAuthToken(response.data.token);
  }

  static async getAvailableImportHosts(): Promise<string[]> {
    const response = await this.get('/recipes/import/available-hosts');
    return response?.data;
  }


  private static dataURItoBlob(dataURI: string) {
    // convert base64/URLEncoded data component to raw binary data held in a string
    let byteString;
    if (dataURI.split(',')[0].indexOf('base64') >= 0) {
      byteString = atob(dataURI.split(',')[1]);
    } else {
      byteString = unescape(dataURI.split(',')[1]);
    }

    // separate out the mime component
    const mimeString = dataURI.split(',')[0].split(':')[1].split(';')[0];

    // write the bytes of the string to a typed array
    const ia = new Uint8Array(byteString.length);
    for (let i = 0; i < byteString.length; i++) {
      ia[i] = byteString.charCodeAt(i);
    }

    return new Blob([ia], {type: mimeString});
  }


  static async getThumbnailImageAsDataURI(uuid: string): Promise<string> {
    try {
      const response = await axios.get(await this.url('/recipes-images/thumbnail/' + uuid), {
        headers: {
          'Authorization': 'Bearer ' + await AppPersistence.getAuthToken(),
        },
        responseType: 'arraybuffer',
      });

      const base64String = Buffer.from(response.data).toString('base64');
      return 'data:image/jpg;base64,' + base64String;
    } catch (e) {
      await this.handleAxiosError(e);
      return '';
    }
  }

  static async getImageAsDataURI(uuid: string): Promise<string> {
    try {
      const response = await axios.get(await this.url('/recipes-images/' + uuid), {
        headers: {
          'Authorization': 'Bearer ' + await AppPersistence.getAuthToken(),
        },
        responseType: 'arraybuffer',
      });

      const base64String = Buffer.from(response.data).toString('base64');
      return 'data:image/jpg;base64,' + base64String;
    } catch (e) {
      await this.handleAxiosError(e);
      return '';
    }
  }

  static async axiosConfig(headers?: {[headerName: string]: string}): Promise<AxiosRequestConfig> {
    const mergedHeaders = {...await this.getAuthHeader(), ...headers};
    return {
      headers: mergedHeaders,
    };
  }

  static async getAuthHeader(): Promise<AxiosRequestHeaders> {
    const token = await AppPersistence.getAuthToken();
    return {'Authorization': 'Bearer ' + token};
  }


  static async authenticate(emailAddress: string, password: string): Promise<void> {
    const response = await axios.post(await this.url('/users/login'), {
      emailAddress: emailAddress,
      password: password,
    });

    AppPersistence.setAuthToken(response.data.token);
    AppPersistence.setRefreshToken(response.data.refreshToken);
  }


  private static async url(path: string) {
    return await AppPersistence.getBackendURL() + AppPersistence.getApiRoute() + path;
  }

  private static async post(apiPath: string, data: any, headers?: {[headerName: string]: string}) {
    try {
      return await axios.post(await this.url(apiPath), data, await this.axiosConfig(headers));
    } catch (e) {
      await RestAPI.handleAxiosError(e);
      // Retry after error handling
      return axios.post(await this.url(apiPath), data, await this.axiosConfig());
    }
  }
  private static async delete(apiPath: string) {
    try {
      return await axios.delete(await this.url(apiPath), await this.axiosConfig());
    } catch (e) {
      await RestAPI.handleAxiosError(e);
      // Retry after error handling
      return axios.delete(await this.url(apiPath), await this.axiosConfig());
    }
  }
  private static async put(apiPath: string, data: any) {
    try {
      return await axios.put(await this.url(apiPath), data, await this.axiosConfig());
    } catch (e) {
      await RestAPI.handleAxiosError(e);
      // Retry after error handling
      return axios.put(await this.url(apiPath), data, await this.axiosConfig());
    }
  }
  private static async get(apiPath: string) {
    try {
      const response = await axios.get(await this.url(apiPath), await this.axiosConfig());
      return response;
    } catch (e) {
      await RestAPI.handleAxiosError(e);
      // Retry after error handling
      const response = await axios.get(await this.url(apiPath), await this.axiosConfig());
      return response;
    }
  }

  private static async handleAxiosError(axiosError: unknown) {
    const errResponse = (axiosError as AxiosError).response;
    if (!errResponse) {
      console.error('Axios error: No response from server');
      throw axiosError;
    }

    if (errResponse.status === 401 || errResponse.status === 403) {
      // Maybe token expired?
      console.warn('Axios warning: Auth fail, trying to refresh token');
      try {
        await this.refreshToken();
      } catch (refreshError) {
        console.error('Failed to refesh token');
        throw refreshError;
      }
    } else {
      console.error('Axios error: Server responded with http '+ errResponse.status);
      throw axiosError;
    }
  }
}

export default RestAPI;


