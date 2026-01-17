import { Handle, Position } from '@xyflow/react';
import { Card, Text, Group, ThemeIcon } from '@mantine/core';
import { IconGitBranch } from '@tabler/icons-react';

export function ConditionNode({ data, selected }: { data: any, selected?: boolean }) {
    return (
        <Card
            shadow="sm"
            p="xs"
            radius="md"
            withBorder
            style={{
                minWidth: 180,
                borderColor: selected ? '#fa5252' : '#e0e0e0',
                borderWidth: selected ? '2px' : '1px',
                background: '#fff5f5' // Light red background to distinguish logic
            }}
        >
            <Handle type="target" position={Position.Top} />

            <Group mb={5} gap="xs" justify="center">
                <IconGitBranch size={16} color="#fa5252" />
                <Text fw={600} size="sm">Условие</Text>
            </Group>

            <Text size="xs" ta="center" mb={10}>
                {data.variable ? (
                    <span>
                        <b>{data.variable}</b> {data.operator} <b>{data.value}</b>
                    </span>
                ) : (
                    <span style={{ color: '#adb5bd', fontStyle: 'italic' }}>Настроить условие...</span>
                )}
            </Text>

            <Group justify="space-between" mt="xs">
                <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                    <Text size="xs" c="green" fw={700} mb={2}>Да</Text>
                    <Handle
                        type="source"
                        position={Position.Bottom}
                        id="true"
                        style={{ left: 10, background: '#40c057' }}
                    />
                </div>

                <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                    <Text size="xs" c="red" fw={700} mb={2}>Нет</Text>
                    <Handle
                        type="source"
                        position={Position.Bottom}
                        id="false"
                        style={{ right: 10, background: '#fa5252' }}
                    />
                </div>
            </Group>
        </Card>
    );
}
