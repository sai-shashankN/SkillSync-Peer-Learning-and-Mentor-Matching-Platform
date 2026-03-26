import { lazy } from 'react';
import { useAuthStore } from '../../store/authStore';

const AdminDashboard = lazy(() => import('../admin/AdminDashboard'));
const MentorDashboard = lazy(() => import('../mentor-dashboard/MentorDashboard'));
const LearnerDashboard = lazy(() => import('./LearnerDashboard'));

export default function DashboardRouter() {
  const user = useAuthStore((state) => state.user);

  if (user?.roles.includes('ADMIN')) {
    return <AdminDashboard />;
  }

  if (user?.roles.includes('MENTOR')) {
    return <MentorDashboard />;
  }

  return <LearnerDashboard />;
}
