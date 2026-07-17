import { httpClient } from './httpClient';
import type { CreateProfilePayload, ProfileForm, ProfileResponse } from '../types/profile';

export async function createProfile(payload: CreateProfilePayload): Promise<ProfileResponse> {
  const { data } = await httpClient.post<ProfileResponse>('/profiles', payload);
  return data;
}

export async function updateProfile(profileId: string, payload: CreateProfilePayload): Promise<ProfileForm> {
  const { data } = await httpClient.put<ProfileForm>(`/profiles/${profileId}`, payload);
  return data;
}

export async function getProfiles(): Promise<ProfileResponse[]> {
  const { data } = await httpClient.get<ProfileResponse[]>('/profiles');
  return data;
}

export async function getProfileById(profileId: string): Promise<ProfileForm> {
  const { data } = await httpClient.get<ProfileForm>(`/profiles/${profileId}`);
  return data;
}

export async function uploadProfileCv(profileId: string, file: File): Promise<void> {
  const formData = new FormData();
  formData.append('file', file);

  await httpClient.post(`/profiles/${profileId}/cv`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
}
