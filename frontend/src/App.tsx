import { Suspense, lazy, useEffect, useState } from 'react';
import axios from 'axios';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import type { AuthResponse } from './services';
import type { ApiResponse } from './types';
import AppErrorBoundary from './components/errors/AppErrorBoundary';
import AppShell from './components/layout/AppShell';
import ProtectedRoute from './components/guards/ProtectedRoute';
import { useAuthStore } from './store/authStore';
import { useThemeStore } from './store/themeStore';
import './i18n';

const LoginPage = lazy(() => import('./features/auth/LoginPage'));
const RegisterPage = lazy(() => import('./features/auth/RegisterPage'));
const GithubCallbackPage = lazy(() => import('./features/auth/GithubCallbackPage'));
const DashboardRouter = lazy(() => import('./features/dashboard/DashboardRouter'));
const UserManagementPage = lazy(() => import('./features/admin/UserManagementPage'));
const MentorApprovalPage = lazy(() => import('./features/admin/MentorApprovalPage'));
const PaymentManagementPage = lazy(() => import('./features/admin/PaymentManagementPage'));
const AuditLogPage = lazy(() => import('./features/admin/AuditLogPage'));
const AnalyticsPage = lazy(() => import('./features/admin/AnalyticsPage'));
const GroupBrowsePage = lazy(() => import('./features/groups/GroupBrowsePage'));
const GroupDetailPage = lazy(() => import('./features/groups/GroupDetailPage'));
const MentorDiscoveryPage = lazy(() => import('./features/mentors/MentorDiscoveryPage'));
const MentorProfilePage = lazy(() => import('./features/mentors/MentorProfilePage'));
const AvailabilityManager = lazy(() => import('./features/mentor-dashboard/AvailabilityManager'));
const EarningsPage = lazy(() => import('./features/mentor-dashboard/EarningsPage'));
const MentorSessionsPage = lazy(() => import('./features/mentor-dashboard/MentorSessionsPage'));
const ProfilePage = lazy(() => import('./features/profile/ProfilePage'));
const SessionBookingFlow = lazy(() => import('./features/sessions/SessionBookingFlow'));
const SessionDetailPage = lazy(() => import('./features/sessions/SessionDetailPage'));
const SessionHistoryPage = lazy(() => import('./features/sessions/SessionHistoryPage'));
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'));
const UnauthorizedPage = lazy(() => import('./pages/UnauthorizedPage'));

function App() {
  const isDark = useThemeStore((state) => state.isDark);
  const accessToken = useAuthStore((state) => state.accessToken);
  const user = useAuthStore((state) => state.user);
  const loginSuccess = useAuthStore((state) => state.loginSuccess);
  const logout = useAuthStore((state) => state.logout);
  const [isBootstrapping, setIsBootstrapping] = useState(true);

  useEffect(() => {
    document.documentElement.classList.toggle('dark', isDark);
  }, [isDark]);

  useEffect(() => {
    let isMounted = true;

    async function bootstrapSession() {
      if (accessToken) {
        setIsBootstrapping(false);
        return;
      }

      try {
        const response = await axios.post<ApiResponse<AuthResponse>>('/api/auth/refresh', null, {
          withCredentials: true,
        });

        if (isMounted) {
          loginSuccess(response.data.data.accessToken, response.data.data.user);
        }
      } catch {
        if (isMounted) {
          logout();
        }
      } finally {
        if (isMounted) {
          setIsBootstrapping(false);
        }
      }
    }

    void bootstrapSession();

    return () => {
      isMounted = false;
    };
  }, [accessToken, loginSuccess, logout]);

  if (isBootstrapping) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-white dark:bg-gray-950">
        <div className="rounded-full border border-slate-200 bg-white px-6 py-3 text-sm font-medium text-slate-600 shadow-sm dark:border-slate-800 dark:bg-slate-900 dark:text-slate-200">
          Loading...
        </div>
      </div>
    );
  }

  return (
    <BrowserRouter>
      <Toaster position="top-right" />
      <Suspense
        fallback={
          <div className="flex h-screen items-center justify-center">Loading...</div>
        }
      >
        <AppErrorBoundary>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/auth/github/callback" element={<GithubCallbackPage />} />

            <Route element={<AppShell />}>
              <Route
                path="/"
                element={
                  <ProtectedRoute>
                    <DashboardRouter />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/dashboard"
                element={
                  <ProtectedRoute>
                    <DashboardRouter />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/mentors"
                element={
                  <ProtectedRoute>
                    {user?.roles.includes('ADMIN') ? <MentorApprovalPage /> : <MentorDiscoveryPage />}
                  </ProtectedRoute>
                }
              />
              <Route
                path="/mentors/:id"
                element={
                  <ProtectedRoute>
                    <MentorProfilePage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/mentors/:id/book"
                element={
                  <ProtectedRoute>
                    <SessionBookingFlow />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/sessions"
                element={
                  <ProtectedRoute>
                    <SessionHistoryPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/sessions/:id"
                element={
                  <ProtectedRoute>
                    <SessionDetailPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/groups"
                element={
                  <ProtectedRoute>
                    <GroupBrowsePage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/groups/:id"
                element={
                  <ProtectedRoute>
                    <GroupDetailPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/profile"
                element={
                  <ProtectedRoute>
                    <ProfilePage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/earnings"
                element={
                  <ProtectedRoute requiredRoles={['MENTOR']}>
                    <EarningsPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/availability"
                element={
                  <ProtectedRoute requiredRoles={['MENTOR']}>
                    <AvailabilityManager />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/mentor/sessions"
                element={
                  <ProtectedRoute requiredRoles={['MENTOR']}>
                    <MentorSessionsPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin"
                element={
                  <ProtectedRoute requiredRoles={['ADMIN']}>
                    <Navigate to="/dashboard" replace />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/users"
                element={
                  <ProtectedRoute requiredRoles={['ADMIN']}>
                    <UserManagementPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/pending-approvals"
                element={
                  <ProtectedRoute requiredRoles={['ADMIN']}>
                    <MentorApprovalPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/payments"
                element={
                  <ProtectedRoute requiredRoles={['ADMIN']}>
                    <PaymentManagementPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/audit-logs"
                element={
                  <ProtectedRoute requiredRoles={['ADMIN']}>
                    <AuditLogPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/analytics"
                element={
                  <ProtectedRoute requiredRoles={['ADMIN']}>
                    <AnalyticsPage />
                  </ProtectedRoute>
                }
              />
            </Route>

            <Route path="/unauthorized" element={<UnauthorizedPage />} />
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </AppErrorBoundary>
      </Suspense>
    </BrowserRouter>
  );
}

export default App;
