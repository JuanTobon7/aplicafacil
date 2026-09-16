export interface AuthMiddlewareContract  {
    use(req: any, res: any, next: () => void);
    verifyToken(token: string): any;
}
