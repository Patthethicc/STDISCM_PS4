import { api } from "./client";
import type { Grade, TeachingCourse } from "../types";

export const myGrades = () => api<Grade[]>("/grades");
export const teachingCourses = () => api<TeachingCourse[]>("/grades/teaching");

export interface GradeUpload {
  courseCode: string;
  term: string;
  grades: { studentId: string; grade: number }[];
}
export const uploadGrades = (body: GradeUpload) =>
  api<{ saved: number }>("/grades", { method: "POST", body });
