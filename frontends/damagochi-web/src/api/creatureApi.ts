import { apiRequest } from "./http";
import type {
  CreateCreatureRequest,
  CreatureSummary,
  SleepRequest,
} from "./types";

export function getCreatures(userId: number) {
  return apiRequest<CreatureSummary[]>("/api/creatures", { userId });
}

export function createCreature(userId: number, request: CreateCreatureRequest) {
  return apiRequest<CreatureSummary>("/api/creatures", {
    method: "POST",
    userId,
    body: JSON.stringify(request),
  });
}

export function activateCreature(userId: number, creatureId: number) {
  return apiRequest<CreatureSummary>(`/api/creatures/${creatureId}/activate`, {
    method: "POST",
    userId,
  });
}

export function feedCreature(userId: number, creatureId: number) {
  return apiRequest<CreatureSummary>(`/api/creatures/${creatureId}/actions/feed`, {
    method: "POST",
    userId,
  });
}

export function treatCreature(userId: number, creatureId: number) {
  return apiRequest<CreatureSummary>(`/api/creatures/${creatureId}/actions/treat`, {
    method: "POST",
    userId,
  });
}

export function sleepCreature(
  userId: number,
  creatureId: number,
  request: SleepRequest
) {
  return apiRequest<CreatureSummary>(`/api/creatures/${creatureId}/actions/sleep`, {
    method: "POST",
    userId,
    body: JSON.stringify(request),
  });
}
