export type IconName = "home" | "spark" | "book" | "users" | "upload" | "chart" | "shield" | "search" | "menu" | "send" | "file" | "chevron" | "copy" | "up" | "down";

export interface DocumentItem {
    lifecycleState?: "ACTIVE" | "INACTIVE" | "TRASH";
    id?: number;
    title: string;
    type: string;
    date: string;
    color: "blue" | "green" | "purple";
    text: string;
    ingestionStatus?: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";
}

export interface Detail {
    lifecycleState?: "ACTIVE" | "INACTIVE" | "TRASH";
    id?: number;
    title: string;
    text: string;
    focus?: DocumentFocus;
}

export interface DocumentFocus {
    kind: "search" | "synthesis";
    passages: string[];
    question?: string;
}

export interface NavigationItem {
    label: string;
    path: string;
    icon: IconName;
    requiresAdmin?: boolean;
}
