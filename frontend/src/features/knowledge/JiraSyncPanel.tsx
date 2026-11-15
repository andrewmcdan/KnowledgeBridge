import { useEffect, useRef, useState } from "react";
import { describeJiraSyncRun, getJiraProjects, getJiraStatus, getJiraSyncRun, getJiraSyncRuns, startJiraSync, type JiraProject, type JiraSyncRun } from "./jiraSyncApi";

const POLL_INTERVAL_MS = 1500;

interface JiraSyncPanelProps {
    onSynced: () => void;
}

export function JiraSyncPanel({ onSynced }: JiraSyncPanelProps) {
    const [projects, setProjects] = useState<JiraProject[]>([]);
    const [projectKey, setProjectKey] = useState("");
    const [run, setRun] = useState<JiraSyncRun | null>(null);
    const [starting, setStarting] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const mounted = useRef(true);
    const pollTimer = useRef<number | undefined>(undefined);

    const poll = (current: JiraSyncRun) => {
        pollTimer.current = window.setTimeout(() => {
            getJiraSyncRun(current.id)
                .then((next) => {
                    if (!mounted.current) return;
                    setRun(next);
                    if (next.status === "RUNNING") poll(next);
                    else onSynced();
                })
                .catch((reason: unknown) => {
                    if (mounted.current) setError(reason instanceof Error ? reason.message : "We could not check the Jira sync.");
                });
        }, POLL_INTERVAL_MS);
    };

    useEffect(() => {
        mounted.current = true;
        getJiraStatus()
            .then(async (status) => {
                if (!status.configured || !status.reachable) return;
                const [available, history] = await Promise.all([getJiraProjects(), getJiraSyncRuns()]);
                if (!mounted.current) return;
                setProjects(available);
                setProjectKey(history[0]?.projectKey ?? available[0]?.key ?? "");
                if (history[0]) {
                    setRun(history[0]);
                    if (history[0].status === "RUNNING") poll(history[0]);
                }
            })
            .catch((reason: unknown) => {
                if (mounted.current) setError(reason instanceof Error ? reason.message : "We could not load the Jira connection.");
            });
        return () => {
            mounted.current = false;
            window.clearTimeout(pollTimer.current);
        };
        // The panel loads once; poll and onSynced change on every render and must not restart it.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const start = async () => {
        setStarting(true);
        setError(null);
        try {
            const started = await startJiraSync(projectKey);
            if (!mounted.current) return;
            setRun(started);
            poll(started);
        } catch (reason: unknown) {
            if (mounted.current) setError(reason instanceof Error ? reason.message : "We could not start the Jira sync.");
        } finally {
            if (mounted.current) setStarting(false);
        }
    };

    if (!projects.length && !error) return null;
    const syncing = starting || run?.status === "RUNNING";
    return (
        <div className="knowledge-management-toolbar" aria-label="Jira sync">
            {projects.length > 0 && (
                <>
                    <label>
                        Jira project
                        <select value={projectKey} onChange={(event) => setProjectKey(event.target.value)} disabled={syncing}>
                            {projects.map((project) => (
                                <option key={project.key} value={project.key}>
                                    {project.name} ({project.key})
                                </option>
                            ))}
                        </select>
                    </label>
                    <button className="secondary-button" type="button" disabled={syncing || !projectKey} onClick={() => void start()}>
                        {syncing ? "Syncing…" : "Sync Jira"}
                    </button>
                </>
            )}
            {run && <span role="status">{describeJiraSyncRun(run)}</span>}
            {error && (
                <span className="upload-error" role="alert">
                    {error}
                </span>
            )}
        </div>
    );
}
