import { Group, Button, ActionIcon, Divider, Tooltip, Paper } from '@mantine/core';
import { IconPlus, IconZoomIn, IconZoomOut, IconFocus, IconArrowBackUp, IconLayoutGrid } from '@tabler/icons-react';

interface EditorToolbarProps {
    onAddNode: () => void;
    onZoomIn: () => void;
    onZoomOut: () => void;
    onFitView: () => void;
    onLayout?: () => void;
}

export function EditorToolbar({ onAddNode, onZoomIn, onZoomOut, onFitView, onLayout }: EditorToolbarProps) {
    return (
        <Paper shadow="xs" radius="md" p={4} withBorder style={{ display: 'inline-flex' }}>
            <Group gap={4}>
                <Tooltip label="Добавить узел" position="bottom" withArrow>
                    <Button
                        variant="light"
                        size="xs"
                        leftSection={<IconPlus size={14} />}
                        onClick={onAddNode}
                    >
                        Узел
                    </Button>
                </Tooltip>

                <Divider orientation="vertical" />

                <Tooltip label="Увеличить" position="bottom" withArrow>
                    <ActionIcon variant="subtle" color="gray" onClick={onZoomIn}>
                        <IconZoomIn size={18} />
                    </ActionIcon>
                </Tooltip>

                <Tooltip label="Уменьшить" position="bottom" withArrow>
                    <ActionIcon variant="subtle" color="gray" onClick={onZoomOut}>
                        <IconZoomOut size={18} />
                    </ActionIcon>
                </Tooltip>

                <Tooltip label="Показать всё" position="bottom" withArrow>
                    <ActionIcon variant="subtle" color="gray" onClick={onFitView}>
                        <IconFocus size={18} />
                    </ActionIcon>
                </Tooltip>

                {onLayout && (
                    <Tooltip label="Авто-раскладка" position="bottom" withArrow>
                        <ActionIcon variant="subtle" color="blue" onClick={onLayout}>
                            <IconLayoutGrid size={18} />
                        </ActionIcon>
                    </Tooltip>
                )}
            </Group>
        </Paper>
    );
}
