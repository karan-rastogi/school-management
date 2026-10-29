import { Navigate, createBrowserRouter } from "react-router-dom";
import { AppLayout } from "./components/AppLayout";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { AcademicYearsPage } from "./pages/admin/AcademicYearsPage";
import { AdminLayout } from "./pages/admin/AdminLayout";
import { CalendarPage } from "./pages/admin/CalendarPage";
import { ClassesPage } from "./pages/admin/ClassesPage";
import { IdCardsPage } from "./pages/admin/IdCardsPage";
import { ParentLinksPage } from "./pages/admin/ParentLinksPage";
import { StudentDetailPage } from "./pages/admin/StudentDetailPage";
import { StudentImportPage } from "./pages/admin/StudentImportPage";
import { StudentsPage } from "./pages/admin/StudentsPage";
import { SubstitutesPage } from "./pages/admin/SubstitutesPage";
import { TeacherAssignmentsPage } from "./pages/admin/TeacherAssignmentsPage";
import { UsersPage } from "./pages/admin/UsersPage";
import { AuthCallbackPage } from "./pages/AuthCallbackPage";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./pages/LoginPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { NoticesPage } from "./pages/NoticesPage";
import { ParentDashboardPage } from "./pages/ParentDashboardPage";
import { ParentNoticesPage } from "./pages/ParentNoticesPage";
import { TeacherAttendancePage } from "./pages/TeacherAttendancePage";
import { TeacherQueriesPage } from "./pages/TeacherQueriesPage";

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
        path: "attendance",
        element: (
          <ProtectedRoute requiredRole={["ADMIN", "TEACHER"]}>
            <TeacherAttendancePage />
          </ProtectedRoute>
        ),
      },
      {
        path: "queries",
        element: (
          <ProtectedRoute requiredRole={["ADMIN", "TEACHER"]}>
            <TeacherQueriesPage />
          </ProtectedRoute>
        ),
      },
      {
        path: "parent",
        element: (
          <ProtectedRoute requiredRole="PARENT">
            <ParentDashboardPage />
          </ProtectedRoute>
        ),
      },
      {
        path: "parent/notices",
        element: (
          <ProtectedRoute requiredRole="PARENT">
            <ParentNoticesPage />
          </ProtectedRoute>
        ),
      },
      {
        path: "notices",
        element: (
          <ProtectedRoute requiredRole={["ADMIN", "TEACHER"]}>
            <NoticesPage />
          </ProtectedRoute>
        ),
      },
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
          { path: "id-cards", element: <IdCardsPage /> },
          { path: "academic-years", element: <AcademicYearsPage /> },
          { path: "classes", element: <ClassesPage /> },
          { path: "calendar", element: <CalendarPage /> },
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
          { path: "substitutes", element: <SubstitutesPage /> },
          { path: "parent-links", element: <ParentLinksPage /> },
        ],
      },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]);
