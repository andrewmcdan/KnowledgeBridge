import { DEFAULT_GRAPH_VIEW, GRAPH_VIEW_RANGES, graphViewValue, type GraphViewOptions, type GraphViewRange } from "./graphViewOptions";

export function GraphViewSettings({ settings, onChange }: { settings: GraphViewOptions; onChange: (settings: GraphViewOptions) => void }) {
    return (
        <details className="graph-view-settings">
            <summary>
                View settings <span>Spacing, nodes, links, and labels</span>
            </summary>
            <div className="graph-settings-body">
                <p id="graph-spacing-help">Lower spacing makes a denser layout; higher spacing spreads entities apart. Changing spacing resumes and refits the layout. These settings apply to the 3D view.</p>
                <div className="graph-settings-grid">
                    {(Object.keys(GRAPH_VIEW_RANGES) as GraphViewRange[]).map((key) => {
                        const { label, min, max, step } = GRAPH_VIEW_RANGES[key];
                        const id = `graph-setting-${key}`;
                        return (
                            <div className="graph-setting-range" key={key}>
                                <div>
                                    <label htmlFor={id}>{label}</label>
                                    <output htmlFor={id}>{settings[key]}%</output>
                                </div>
                                <input
                                    id={id}
                                    type="range"
                                    min={min}
                                    max={max}
                                    step={step}
                                    value={settings[key]}
                                    aria-valuetext={`${settings[key]}%`}
                                    aria-describedby={key === "spacing" ? "graph-spacing-help" : undefined}
                                    onChange={(event) => onChange({ ...settings, [key]: graphViewValue(key, Number(event.target.value)) })}
                                />
                            </div>
                        );
                    })}
                </div>
                <div className="graph-settings-toggles">
                    <label>
                        <input type="checkbox" checked={settings.showLabels} onChange={(event) => onChange({ ...settings, showLabels: event.target.checked })} />
                        Show entity labels
                    </label>
                    <label>
                        <input type="checkbox" checked={settings.sizeByDocuments} onChange={(event) => onChange({ ...settings, sizeByDocuments: event.target.checked })} />
                        Size nodes by document count
                    </label>
                    <button className="secondary-button" type="button" onClick={() => onChange({ ...DEFAULT_GRAPH_VIEW })}>
                        Reset view settings
                    </button>
                </div>
            </div>
        </details>
    );
}
