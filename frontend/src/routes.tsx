import { Navigate, createBrowserRouter } from "react-router-dom";
import { AppLayout } from "./components/AppLayout";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { AcademicYearsPage } from "./pages/admin/AcademicYearsPage";
import { AdminLayout } from "./pages/admin/AdminLayout";
import { ClassesPage } from "./pages/admin/ClassesPage";
import { ParentLinksPage } from "./pages/admin/ParentLinksPage";
import { StudentDetailPage } from "./pages/admin/StudentDetailPage";
import { StudentImportPage } from "./pages/admin/StudentImportPage";
import { StudentsPage } from "./pages/admin/StudentsPage";
import { TeacherAssignmentsPage } from "./pages/admin/TeacherAssignmentsPage";
import { UsersPage } from "./pages/admin/UsersPage";
import { AuthCallbackPage } from "./pages/AuthCallbackPage";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./pages/LoginPage";
import { NotFoundPage } from "./pages/NotFoundPage";

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/auth/callback", element: <AuthCallbackPage /> },
  {
    path: "/",
    element: (
      <ProtectedRoute>
        <AppLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <HomePage /> },
      {
        path: "admin",
        element: (
          <ProtectedRoute requiredRole="ADMIN">
            <AdminLayout />
          </ProtectedRoute>
        ),
        children: [
          { index: true, element: <Navigate to="/admin/students" replace /> },
          { path: "students", element: <StudentsPage /> },
          { path: "students/:studentId", element: <StudentDetailPage /> },
          { path: "import", element: <StudentImportPage /> },
          { path: "academic-years", element: <AcademicYearsPage /> },
          { path: "classes", element: <ClassesPage /> },
          {
            path: "teachers",
            element: (
              <UsersPage
                role="TEACHER"
                title="Teachers"
                description="Teacher accounts are users holding the TEACHER role. Roles can be changed at any time."
              />
            ),
          },
          {
            path: "parents",
            element: (
              <UsersPage
                role="PARENT"
                title="Parents"
                description="Parent accounts sign in and see only their linked children."
              />
            ),
          },
          { path: "teacher-assignments", element: <TeacherAssignmentsPage /> },
          { path: "parent-links", element: <ParentLinksPage /> },
        ],
      },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]);
