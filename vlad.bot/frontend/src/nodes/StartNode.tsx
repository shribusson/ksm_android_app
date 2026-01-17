import { Handle, Position } from '@xyflow/react';
import { Card, Text, Group } from '@mantine/core';
import { IconPlayerPlay } from '@tabler/icons-react';

export function StartNode({ selected }: { selected?: boolean }) {
    return (
        <Card
            shadow="sm"
            p="xs"
            radius="md"
            withBorder
            style={{
                minWidth: 150,
                borderColor: selected ? '#40c057' : '#e0e0e0',
                borderWidth: selected ? '2px' : '1px',
                backgroundColor: '#f8f9fa'
            }}
        >
            <Group mb={5} gap="xs" justify="center">
                <IconPlayerPlay size={16} color="#40c057" style={{ fill: "#40c057" }} />
                <Text fw={600} size="sm">Старт</Text>
            </Group>

            <Text size="xs" c="dimmed" ta="center">
                Точка входа
            </Text>

            <Handle
                type="source"
                position={Position.Bottom}
                className="custom-handle"
                style={{ bottom: -10, left: '50%', transform: 'translateX(-50%)' }}
            >
                <div className="handle-dot" style={{ backgroundColor: '#40c057' }} />
            </Handle>
        </Card>
    );
}
