import type { IconName } from "../../types/models";

const paths: Record<IconName, string> = {
    home: "M3 10 12 3l9 7v11h-6v-7H9v7H3Z",
    spark: "m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5ZM20 2v4m-2-2h4",
    book: "M5 3h15v18H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm2 0v18M3 17h17",
    users: "M16 21v-3a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v3m20 0v-3a4 4 0 0 0-3-4M9 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8Zm9 1a3 3 0 0 1 0 6",
    upload: "M12 16V3m-5 5 5-5 5 5M3 14v6a1 1 0 0 0 1 1h16a1 1 0 0 0 1-1v-6",
    chart: "M3 13h4v8H3Zm7-6h4v14h-4Zm7-5h4v19h-4Z",
    shield: "m12 3 9 4v5c0 5-9 10-9 10S3 17 3 12V7Zm-4 9 3 3 5-6",
    search: "M10 3a7 7 0 1 0 0 14 7 7 0 0 0 0-14Zm5 12 6 6",
    menu: "M4 6h16M4 12h16M4 18h16",
    send: "m3 10 18-7-7 18-3-8Zm8 3L21 3",
    file: "M5 3h9l5 5v13H5Zm9 0v6h5M8 13h8m-8 4h8",
    chevron: "m9 5 7 7-7 7",
    copy: "M9 9h12v12H9ZM15 9V3H3v12h6",
    bookmark: "M6 3h12v18l-6-4-6 4Z",
    up: "M7 21H3V10h4Zm0-11 5-7h2v7h6l1 2-3 9H7",
    down: "M7 3H3v11h4Zm0 11 5 7h2v-7h6l1-2-3-9H7",
};

export function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
    return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d={paths[name]} />
        </svg>
    );
}
