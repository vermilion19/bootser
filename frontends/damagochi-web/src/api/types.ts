export interface ApiErrorPayload {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

export interface AuthResponse {
  userId: number;
  loginId: string;
  nickname: string;
}

export interface SignUpRequest {
  loginId: string;
  password: string;
  nickname: string;
}

export interface LoginRequest {
  loginId: string;
  password: string;
}

export interface CreatureStateView {
  age: number;
  health: number;
  hunger: number;
  conditionScore: number;
  winRate: number;
  sleeping: boolean;
  sleepingSince: string | null;
  lastSleepToggleAt: string | null;
  lastFedAt: string | null;
  lastTreatedAt: string | null;
  updatedAt: string;
}

export interface CreatureSummary {
  id: number;
  userId: number;
  name: string;
  species: string;
  stage: string;
  active: boolean;
  state: CreatureStateView;
}

export interface CreateCreatureRequest {
  name: string;
  species: string;
}

export interface SleepRequest {
  sleeping: boolean;
}

export interface RandomQueueResponse {
  status: "WAITING" | "MATCHED";
  ticket: number;
  battleId: number | null;
}

export interface RoomCreateResponse {
  code: string;
  ticket: number;
}

export interface RoomInfoResponse {
  code: string;
  ownerUserId: number;
  ownerCreatureId: number;
  createdAt: string;
  expiresAt: string;
  remainingSeconds: number;
  status: "OPEN_OWNER" | "OPEN";
}

export interface RoomCancelResponse {
  code: string;
  canceled: boolean;
}

export interface RoomSummary {
  code: string;
  ownerCreatureId: number;
  createdAt: string;
  expiresAt: string;
  remainingSeconds: number;
}

export interface RoomListResponse {
  rooms: RoomSummary[];
}

export interface BattleParticipantView {
  userId: number;
  creatureId: number;
  outcome: "WIN" | "LOSE" | "DRAW";
  snapshotJson: string;
}

export interface BattleDetailView {
  battleId: number;
  type: "RANDOM" | "ROOM";
  result: "PENDING" | "COMPLETED";
  seed: number | null;
  startedAt: string;
  endedAt: string | null;
  myOutcome: "WIN" | "LOSE" | "DRAW";
  participants: BattleParticipantView[];
}
