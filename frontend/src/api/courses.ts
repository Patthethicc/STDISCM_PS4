import { api } from "./client";
import type { Course, Roster } from "../types";

export const listCourses = () => api<Course[]>("/courses");
export const enroll = (code: string) => api<Course>(`/courses/${encodeURIComponent(code)}/enroll`, { method: "POST" });
export const myEnrollments = () => api<Course[]>("/enrollments");
export const roster = (code: string) => api<Roster>(`/courses/${encodeURIComponent(code)}/roster`);
