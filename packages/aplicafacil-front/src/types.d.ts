declare namespace JSX {
  interface IntrinsicElements {
    [elementName: string]: any;
  }
}

interface ImportMetaEnv {
  readonly VITE_API_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare module 'react' {
  export type ChangeEvent<T = any> = {
    target: T;
  };

  export type FormEvent<T = any> = {
    preventDefault(): void;
    target: T;
  };

  export type ReactNode = any;
  export type SetStateAction<T> = T | ((previous: T) => T);
  export type Dispatch<T> = (value: T) => void;

  export function useMemo<T>(factory: () => T, dependencies: unknown[]): T;
  export function useState<T>(initialValue: T | (() => T)): [T, Dispatch<SetStateAction<T>>];

  const React: {
    StrictMode: any;
  };

  export default React;
}

declare module 'react-dom/client' {
  export function createRoot(element: HTMLElement): {
    render(children: any): void;
  };
}

declare module 'react/jsx-runtime' {
  export const jsx: any;
  export const jsxs: any;
  export const Fragment: any;
}
