import { createContext, useContext, useState, ReactNode } from "react";
import { api } from "../api/client";

type AuthUser = { userId: number; role: string } | null;

type AuthContextValue = {
  user: AuthUser;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, fullName: string, role: string) => Promise<void>;
  logout: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

function loadUser(): AuthUser {
  const userId = localStorage.getItem("userId");
  const role = localStorage.getItem("role");
  if (!userId || !role) return null;
  return { userId: Number(userId), role };
}

function persist(res: { accessToken: string; refreshToken: string; userId: number; role: string }) {
  localStorage.setItem("accessToken", res.accessToken);
  localStorage.setItem("refreshToken", res.refreshToken);
  localStorage.setItem("userId", String(res.userId));
  localStorage.setItem("role", res.role);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser>(loadUser());

  async function login(email: string, password: string) {
    const res = await api.post<any>("/api/auth/login", { email, password });
    persist(res);
    setUser({ userId: res.userId, role: res.role });
  }

  async function register(email: string, password: string, fullName: string, role: string) {
    const res = await api.post<any>("/api/auth/register", { email, password, fullName, role });
    persist(res);
    setUser({ userId: res.userId, role: res.role });
  }

  function logout() {
    localStorage.clear();
    setUser(null);
  }

  return <AuthContext.Provider value={{ user, login, register, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
