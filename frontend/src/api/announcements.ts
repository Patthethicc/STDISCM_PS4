import { api } from "./client";
import type { Announcement } from "../types";

export const listAnnouncements = () => api<Announcement[]>("/announcements");
export const postAnnouncement = (title: string, body: string) =>
  api<Announcement>("/announcements", { method: "POST", body: { title, body } });
