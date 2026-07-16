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
  await httpClient.post('/auth/login', payload);
}
