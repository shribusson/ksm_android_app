import { Handle, Position } from '@xyflow/react';
import { Card, Text, Center } from '@mantine/core';
import { IconHandStop } from '@tabler/icons-react';

export function EndNode({ selected }: { selected?: boolean }) {
    return (
        <Card
            shadow="sm"
            p="xs"
            radius="md"
            withBorder
            style={{
                minWidth: 100,
                borderColor: selected ? '#fa5252' : '#e0e0e0',
                borderWidth: selected ? '2px' : '1px',
                background: '#fff0f0'
            }}
        >
            <Handle type="target" position={Position.Top} />
            <Handle type="target" position={Position.Left} />

            <Center>
                <IconHandStop size={24} color="#fa5252" />
            </Center>
            <Text size="xs" fw={700} ta="center" mt={4} c="red">
                Завершение
            </Text>
        </Card>
    );
}
