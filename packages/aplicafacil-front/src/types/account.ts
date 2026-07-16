export type AccountMode = 'register' | 'login';

export type AccountForm = {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  linkedinUrl: string;
  username: string;
  password: string;
};

export type PeopleResponse = {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  linkedinUrl?: string;
};

export type CreatePeoplePayload = {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  linkedinUrl?: string;
};

export type RegisterPayload = {
  username: string;
  password: string;
  personId: string;
};

export type LoginPayload = {
  username: string;
  password: string;
};
