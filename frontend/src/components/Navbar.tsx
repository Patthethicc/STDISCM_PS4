import { NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";

export function Navbar() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();
  if (!session) return null;

  const links =
    session.role === "STUDENT"
      ? [
          ["/courses", "Courses"],
          ["/enrollments", "My Enrollments"],
          ["/grades", "My Grades"],
          ["/announcements", "Announcements"],
        ]
      : [
          ["/courses", "Courses"],
          ["/upload", "Upload Grades"],
          ["/announcements", "Announcements"],
        ];

  return (
    <nav className="nav">
      <span className="brand">Enrollment</span>
      <div className="links">
        {links.map(([to, label]) => (
          <NavLink key={to} to={to}>
            {label}
          </NavLink>
        ))}
      </div>
      <div className="user">
        <span>
          {session.name} <span className="badge">{session.role.toLowerCase()}</span>
        </span>
        <button
          className="btn btn-small btn-ghost"
          onClick={async () => {
            await logout();
            navigate("/login");
          }}
        >
          Log out
        </button>
      </div>
    </nav>
  );
}
