import { Recommendation } from "../../api/recommendations";

export interface IWriter {
  available(): boolean;
  writeRecommendations(recommendations: Recommendation[]): void;
}