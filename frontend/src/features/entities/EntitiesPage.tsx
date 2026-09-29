import { entities } from "../../data/demoData";
import type { Detail } from "../../types/models";
import { Icon } from "../../components/ui/Icon";

export function EntitiesPage({ onOpenDetail }: { onOpenDetail: (detail: Detail) => void }) {
    return (
        <section className="collection panel">
            <h2>Entities</h2>
            {entities.map((entity, index) => (
                <button
                    className="entity-row"
                    key={entity}
                    onClick={() =>
                        onOpenDetail({
                            title: entity,
                            text: `${entity} is mentioned in the sample purchasing workflow. Entity extraction is not connected yet.`,
                        })
                    }
                >
                    <span className={`entity-icon color-${index}`}>
                        <Icon name="users" />
                    </span>
                    <span>
                        <strong>{entity}</strong>
                        <small>{index === 3 ? "Cost Center" : "Department"}</small>
                    </span>
                    <Icon name="chevron" size={16} />
                </button>
            ))}
        </section>
    );
}
