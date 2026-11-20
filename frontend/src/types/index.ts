export type Role = "STUDENT" | "FACULTY";

export interface Session {
  userId: string;
  role: Role;
  name: string;
  expiresAt: number;
}

export interface Course {
  code: string;
  title: string;
  capacity: number;
  enrolled: number;
  open: boolean;
}

export interface RosterStudent {
  studentId: string;
  studentName: string;
}

export interface Roster {
  courseCode: string;
  students: RosterStudent[];
}

export interface Grade {
  courseCode: string;
  courseTitle: string;
  term: string;
  grade: number;
}

export interface TeachingCourse {
  courseCode: string;
  courseTitle: string;
}

export interface Announcement {
  id: string;
  title: string;
  body: string;
  authorName: string;
  createdAt: number;
}

/** Error body from the gateway. */
export interface ApiErrorBody {
  error: string;
  message: string;
  service?: string | null;
}
