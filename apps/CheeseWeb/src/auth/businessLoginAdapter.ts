export interface BusinessLoginAdapter {
  login(): Promise<{ userID: string; platformID: number; token: string }>;
}
