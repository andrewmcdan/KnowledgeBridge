export type IconName = "home" | "spark" | "book" | "users" | "upload" | "chart" | "shield" | "search" | "menu" | "send" | "file" | "chevron" | "copy" | "bookmark" | "up" | "down";

export interface DocumentItem {
    title: string;
    type: string;
    date: string;
    color: "blue" | "green" | "purple";
    text: string;
}

export interface Detail {
    title: string;
    text: string;
}

export interface NavigationItem {
    label: string;
    path: string;
    icon: IconName;
}
