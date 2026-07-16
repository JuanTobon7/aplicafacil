import { httpClient } from './httpClient';
import type { CreateProfilePayload, ProfileResponse } from '../types/profile';

export async function createProfile(payload: CreateProfilePayload): Promise<ProfileResponse> {
  const { data } = await httpClient.post<ProfileResponse>('/profiles', payload);
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
