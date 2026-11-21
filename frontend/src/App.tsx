import { Navigate, Route, Routes } from "react-router-dom";
import { Navbar } from "./components/Navbar";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { useAuth } from "./hooks/useAuth";
import { AnnouncementsPage } from "./pages/AnnouncementsPage";
import { CoursesPage } from "./pages/CoursesPage";
import { LoginPage } from "./pages/LoginPage";
import { MyEnrollmentsPage } from "./pages/MyEnrollmentsPage";
import { MyGradesPage } from "./pages/MyGradesPage";
import { UploadGradesPage } from "./pages/UploadGradesPage";

function Home() {
  const { session } = useAuth();
  return <Navigate to={session?.role === "FACULTY" ? "/upload" : "/courses"} replace />;
}

export default function App() {
  return (
    <>
      <Navbar />
      <main>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<ProtectedRoute><Home /></ProtectedRoute>} />
          <Route path="/courses" element={<ProtectedRoute><CoursesPage /></ProtectedRoute>} />
          <Route path="/enrollments" element={<ProtectedRoute role="STUDENT"><MyEnrollmentsPage /></ProtectedRoute>} />
          <Route path="/grades" element={<ProtectedRoute role="STUDENT"><MyGradesPage /></ProtectedRoute>} />
          <Route path="/upload" element={<ProtectedRoute role="FACULTY"><UploadGradesPage /></ProtectedRoute>} />
          <Route path="/announcements" element={<ProtectedRoute><AnnouncementsPage /></ProtectedRoute>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </>
  );
}
