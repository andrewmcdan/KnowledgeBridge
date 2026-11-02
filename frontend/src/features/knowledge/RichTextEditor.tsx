import { Markdown } from "@tiptap/markdown";
import { EditorContent, useEditor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { useEffect } from "react";

type RichTextEditorProps = {
    value: string;
    disabled: boolean;
    onChange: (value: string) => void;
};

type ToolbarButtonProps = {
    active?: boolean;
    disabled: boolean;
    label: string;
    onClick: () => void;
    children: string;
};

function ToolbarButton({ active = false, disabled, label, onClick, children }: ToolbarButtonProps) {
    return (
        <button className={active ? "active" : ""} type="button" aria-label={label} aria-pressed={active} disabled={disabled} onMouseDown={(event) => event.preventDefault()} onClick={onClick}>
            {children}
        </button>
    );
}

export function RichTextEditor({ value, disabled, onChange }: RichTextEditorProps) {
    const editor = useEditor({
        extensions: [StarterKit, Markdown],
        content: value,
        contentType: "markdown",
        editable: !disabled,
        immediatelyRender: false,
        editorProps: {
            attributes: {
                "aria-label": "Note content",
                "data-placeholder": "Start writing your note…",
                id: "note-content",
            },
        },
        onUpdate: ({ editor: currentEditor }) => onChange(currentEditor.getMarkdown()),
    });

    useEffect(() => {
        if (!editor) return;
        editor.setEditable(!disabled);
    }, [disabled, editor]);

    useEffect(() => {
        if (!editor || value === editor.getMarkdown()) return;
        editor.commands.setContent(value, { contentType: "markdown", emitUpdate: false });
    }, [editor, value]);

    if (!editor) return null;

    return (
        <div className="rich-text-editor">
            <div className="rich-text-toolbar" role="toolbar" aria-label="Note formatting">
                <ToolbarButton active={editor.isActive("bold")} disabled={disabled} label="Bold" onClick={() => editor.chain().focus().toggleBold().run()}>
                    B
                </ToolbarButton>
                <ToolbarButton active={editor.isActive("italic")} disabled={disabled} label="Italic" onClick={() => editor.chain().focus().toggleItalic().run()}>
                    I
                </ToolbarButton>
                <ToolbarButton active={editor.isActive("heading", { level: 2 })} disabled={disabled} label="Heading" onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}>
                    H
                </ToolbarButton>
                <ToolbarButton active={editor.isActive("bulletList")} disabled={disabled} label="Bulleted list" onClick={() => editor.chain().focus().toggleBulletList().run()}>
                    List
                </ToolbarButton>
                <ToolbarButton active={editor.isActive("orderedList")} disabled={disabled} label="Numbered list" onClick={() => editor.chain().focus().toggleOrderedList().run()}>
                    1.
                </ToolbarButton>
                <ToolbarButton active={editor.isActive("blockquote")} disabled={disabled} label="Quote" onClick={() => editor.chain().focus().toggleBlockquote().run()}>
                    Quote
                </ToolbarButton>
                <ToolbarButton active={editor.isActive("codeBlock")} disabled={disabled} label="Code block" onClick={() => editor.chain().focus().toggleCodeBlock().run()}>
                    {"</>"}
                </ToolbarButton>
            </div>
            <EditorContent editor={editor} />
        </div>
    );
}
