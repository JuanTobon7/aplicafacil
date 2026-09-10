import { httpClient } from './httpClient';
import type { CreatePeoplePayload, LoginPayload, PeopleResponse, RegisterPayload } from '../types/account';

export async function createPerson(payload: CreatePeoplePayload): Promise<PeopleResponse> {
  const { data } = await httpClient.post<PeopleResponse>('/people', payload);
  return data;
}

export async function registerUser(payload: RegisterPayload): Promise<void> {
  await httpClient.post('/auth/register', payload);
}

export async function loginUser(payload: LoginPayload): Promise<void> {
  console.log('loginUser payload', payload);
  const response = await httpClient.post('/auth/login', payload);
  const { access_token } = response.data;
  console.log('loginUser access_token', access_token);
  window.localStorage.setItem('access_token', access_token);
  console.log('loginUser access_token stored in localStorage');
}
