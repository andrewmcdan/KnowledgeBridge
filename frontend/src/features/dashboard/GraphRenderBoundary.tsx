import { Component, type ReactNode } from "react";

export class GraphRenderBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
    state = { failed: false };

    static getDerivedStateFromError() {
        return { failed: true };
    }

    render() {
        return this.state.failed ? (
            <div className="graph-render-message" role="status">
                The 3D view could not start. Use List view to explore the same entities, connections, and documents.
            </div>
        ) : (
            this.props.children
        );
    }
}
