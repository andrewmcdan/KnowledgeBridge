import type { NavigationItem } from "../types/models";

export const navigation: NavigationItem[] = [
    { label: "Dashboard", path: "/dashboard", icon: "home" },
    { label: "Ask AI", path: "/ask-ai", icon: "spark" },
    { label: "Knowledge Base", path: "/knowledge", icon: "book" },
    { label: "Entities", path: "/entities", icon: "users" },
    { label: "Uploads", path: "/uploads", icon: "upload", requiresAdmin: true },
    { label: "Usage", path: "/usage", icon: "chart" },
    { label: "Admin", path: "/admin", icon: "shield", requiresAdmin: true },
];
