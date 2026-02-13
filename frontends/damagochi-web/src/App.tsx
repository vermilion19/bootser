import type { ReactNode } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "./components/AppShell";
import { BattleLobbyPage } from "./pages/BattleLobbyPage";
import { BattlePlayPage } from "./pages/BattlePlayPage";
import { BattleRoomPage } from "./pages/BattleRoomPage";
import { CreateCreaturePage } from "./pages/CreateCreaturePage";
import { CreaturesPage } from "./pages/CreaturesPage";
import { LoginPage } from "./pages/LoginPage";
import { MainPage } from "./pages/MainPage";
import { SettingsPage } from "./pages/SettingsPage";
import { SignupPage } from "./pages/SignupPage";
import { useAuth } from "./store/auth";

function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

function AuthRoute({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  if (user) {
    return <Navigate to="/app" replace />;
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/app" replace />} />
      <Route
        path="/login"
        element={
          <AuthRoute>
            <LoginPage />
          </AuthRoute>
        }
      />
      <Route
        path="/signup"
        element={
          <AuthRoute>
            <SignupPage />
          </AuthRoute>
        }
      />

      <Route
        path="/app"
        element={
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        }
      >
        <Route index element={<MainPage />} />
        <Route path="creatures" element={<CreaturesPage />} />
        <Route path="creatures/new" element={<CreateCreaturePage />} />
        <Route path="battle" element={<BattleLobbyPage />} />
        <Route path="battle/room" element={<BattleRoomPage />} />
        <Route path="battle/:battleId" element={<BattlePlayPage />} />
        <Route path="settings" element={<SettingsPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
