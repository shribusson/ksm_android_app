import { Stack, Paper, Text, UnstyledButton, Group, ThemeIcon, ScrollArea } from '@mantine/core';
import { IconMessage, IconQuestionMark, IconListCheck, IconFlag, IconPlayerPlay, IconArrowRight, IconGitBranch } from '@tabler/icons-react';
import { DragEvent } from 'react';

const nodeTypes = [
    { type: 'message', label: 'Сообщение', icon: IconMessage, color: 'blue', desc: 'Отправить текст или медиа' },
    { type: 'question', label: 'Вопрос', icon: IconQuestionMark, color: 'orange', desc: 'Запросить ввод текста' },
    { type: 'single_choice', label: 'Выбор', icon: IconListCheck, color: 'cyan', desc: 'Кнопки с вариантами' },
    { type: 'condition', label: 'Условие', icon: IconGitBranch, color: 'violet', desc: 'Ветвление логики' },
    { type: 'end', label: 'Конец', icon: IconFlag, color: 'red', desc: 'Завершить сценарий' },
];

export function NodePalette({ collapsed }: { collapsed?: boolean }) {
    const onDragStart = (event: DragEvent, nodeType: string) => {
        event.dataTransfer.setData('application/reactflow', nodeType);
        event.dataTransfer.effectAllowed = 'move';
    };

    return (
        <Paper p={collapsed ? "xs" : "md"} withBorder style={{ height: '100%', background: '#fafafa', transition: 'padding 0.3s ease' }}>
            <Text size="xs" fw={700} c="dimmed" tt="uppercase" mb="md" style={{ opacity: collapsed ? 0 : 1, transition: 'opacity 0.2s', whiteSpace: 'nowrap', overflow: 'hidden' }}>
                Компоненты
            </Text>

            <Stack gap="sm">
                {nodeTypes.map((node) => {
                    const Icon = node.icon;
                    return (
                        <UnstyledButton
                            key={node.type}
                            aria-label={`Добавить узел ${node.label}`}
                            draggable
                            onDragStart={(e) => onDragStart(e, node.type)}
                            style={{
                                padding: collapsed ? '8px' : '10px',
                                borderRadius: '8px',
                                border: collapsed ? '1px solid transparent' : '1px solid #e0e0e0',
                                backgroundColor: collapsed ? 'transparent' : 'white',
                                cursor: 'grab',
                                transition: 'all 0.2s',
                                display: 'flex',
                                justifyContent: collapsed ? 'center' : 'flex-start',
                                width: '100%'
                            }}
                            onMouseEnter={(e) => {
                                if (!collapsed) {
                                    e.currentTarget.style.borderColor = 'var(--mantine-color-blue-5)';
                                    e.currentTarget.style.transform = 'translateY(-2px)';
                                    e.currentTarget.style.boxShadow = '0 4px 8px rgba(0,0,0,0.05)';
                                } else {
                                    e.currentTarget.style.transform = 'scale(1.1)';
                                }
                            }}
                            onMouseLeave={(e) => {
                                if (!collapsed) {
                                    e.currentTarget.style.borderColor = '#e0e0e0';
                                    e.currentTarget.style.transform = 'none';
                                    e.currentTarget.style.boxShadow = 'none';
                                } else {
                                    e.currentTarget.style.transform = 'none';
                                }
                            }}
                        >
                            <Group wrap="nowrap" justify={collapsed ? 'center' : 'flex-start'}>
                                <ThemeIcon
                                    color={node.color}
                                    variant="light"
                                    size="lg"
                                    radius="md"
                                >
                                    <Icon size={20} />
                                </ThemeIcon>

                                {!collapsed && (
                                    <div style={{ flex: 1, minWidth: 0, opacity: 1, transition: 'opacity 0.2s ease-in' }}>
                                        <Text size="sm" fw={500}>{node.label}</Text>
                                        <Text size="xs" c="dimmed" lineClamp={1}>
                                            {node.desc}
                                        </Text>
                                    </div>
                                )}
                            </Group>
                        </UnstyledButton>
                    );
                })}
            </Stack>

            {!collapsed && (
                <Text size="xs" c="dimmed" mt="xl" ta="center" style={{ animation: 'fadeIn 0.5s' }}>
                    Перетащите блок на поле
                </Text>
            )}
        </Paper>
    );
}
