import { Navigate, Route, Routes } from 'react-router-dom';
import RequireAuth from './components/RequireAuth';
import DashboardPage from './pages/DashboardPage';
import InterviewQuestionsPage from './pages/InterviewQuestionsPage';
import JobsPage from './pages/JobsPage';
import LoginPage from './pages/LoginPage';
import MatchJobPage from './pages/MatchJobPage';
import ProfilePage from './pages/ProfilePage';
import ResumePage from './pages/ResumePage';
import SignupPage from './pages/SignupPage';
import TailoredResumePage from './pages/TailoredResumePage';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route
        path="/dashboard"
        element={
          <RequireAuth>
            <DashboardPage />
          </RequireAuth>
        }
      />
      <Route
        path="/profile"
        element={
          <RequireAuth>
            <ProfilePage />
          </RequireAuth>
        }
      />
      <Route
        path="/resume"
        element={
          <RequireAuth>
            <ResumePage />
          </RequireAuth>
        }
      />
      <Route
        path="/resume/:id"
        element={
          <RequireAuth>
            <TailoredResumePage />
          </RequireAuth>
        }
      />
      <Route
        path="/jobs"
        element={
          <RequireAuth>
            <JobsPage />
          </RequireAuth>
        }
      />
      <Route
        path="/jobs/:id/interview"
        element={
          <RequireAuth>
            <InterviewQuestionsPage />
          </RequireAuth>
        }
      />
      <Route
        path="/match"
        element={
          <RequireAuth>
            <MatchJobPage />
          </RequireAuth>
        }
      />
    </Routes>
  );
}
