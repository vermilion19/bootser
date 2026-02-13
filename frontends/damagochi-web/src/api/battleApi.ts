import { apiRequest } from "./http";
import type {
  BattleDetailView,
  RandomQueueResponse,
  RoomCancelResponse,
  RoomCreateResponse,
  RoomInfoResponse,
  RoomListResponse,
} from "./types";

interface CreaturePayload {
  creatureId: number;
}

export function queueRandomBattle(userId: number, creatureId: number) {
  return apiRequest<RandomQueueResponse>("/api/battles/random/queue", {
    method: "POST",
    userId,
    body: JSON.stringify({ creatureId } satisfies CreaturePayload),
  });
}

export function createBattleRoom(userId: number, creatureId: number) {
  return apiRequest<RoomCreateResponse>("/api/battles/rooms", {
    method: "POST",
    userId,
    body: JSON.stringify({ creatureId } satisfies CreaturePayload),
  });
}

export function getMyBattleRooms(userId: number) {
  return apiRequest<RoomListResponse>("/api/battles/rooms/mine", { userId });
}

export function getBattleRoom(userId: number, code: string) {
  return apiRequest<RoomInfoResponse>(`/api/battles/rooms/${code}`, { userId });
}

export function cancelBattleRoom(userId: number, code: string) {
  return apiRequest<RoomCancelResponse>(`/api/battles/rooms/${code}`, {
    method: "DELETE",
    userId,
  });
}

export function joinBattleRoom(userId: number, code: string, creatureId: number) {
  return apiRequest<RandomQueueResponse>(`/api/battles/rooms/${code}/join`, {
    method: "POST",
    userId,
    body: JSON.stringify({ creatureId } satisfies CreaturePayload),
  });
}

export function getBattle(userId: number, battleId: number) {
  return apiRequest<BattleDetailView>(`/api/battles/${battleId}`, { userId });
}
