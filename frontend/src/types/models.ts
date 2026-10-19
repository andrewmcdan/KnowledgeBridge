export type IconName = "home" | "spark" | "book" | "users" | "upload" | "chart" | "shield" | "search" | "menu" | "send" | "file" | "chevron" | "copy" | "bookmark" | "up" | "down";

export interface DocumentItem {
    id?: number;
    title: string;
    type: string;
    date: string;
    color: "blue" | "green" | "purple";
    text: string;
    ingestionStatus?: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";
}

export interface Detail {
    id?: number;
    title: string;
    text: string;
}

export interface NavigationItem {
    label: string;
    path: string;
    icon: IconName;
    requiresAdmin?: boolean;
}
