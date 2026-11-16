/** Preserve row identity across ranking changes; identical rows use a per-identity occurrence. */
export function resultKeys<T>(rows: readonly T[], identity: (row: T) => readonly string[]): string[] {
    const occurrences = new Map<string, number>();
    return rows.map((row) => {
        const id = JSON.stringify(identity(row));
        const occurrence = occurrences.get(id) ?? 0;
        occurrences.set(id, occurrence + 1);
        return JSON.stringify([id, occurrence]);
    });
}
