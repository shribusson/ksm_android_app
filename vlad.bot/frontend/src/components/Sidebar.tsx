import React, { useState } from 'react';
import { Card, Text, Group, Tooltip, UnstyledButton, Stack } from '@mantine/core';
import { IconMessage, IconQuestionMark, IconMenu2, IconListCheck, IconGitBranch, IconHandStop } from '@tabler/icons-react';

export function Sidebar() {
    const [expanded, setExpanded] = useState(false);

    const onDragStart = (event: React.DragEvent, nodeType: string) => {
        event.dataTransfer.setData('application/reactflow', nodeType);
        event.dataTransfer.effectAllowed = 'move';
    };

    return (
        <div
            style={{
                width: expanded ? '200px' : '60px',
                borderRight: '1px solid #e0e0e0',
                background: '#fff',
                transition: 'width 0.3s ease',
                display: 'flex',
                flexDirection: 'column',
                alignItems: expanded ? 'stretch' : 'center',
                padding: '10px 0',
                zIndex: 10
            }}
            onMouseEnter={() => setExpanded(true)}
            onMouseLeave={() => setExpanded(false)}
        >
            <Stack gap="md" align="center" style={{ width: '100%' }}>
                <IconMenu2 size={24} color="gray" style={{ marginBottom: 10 }} />

                <Tooltip label="Сообщение" position="right" disabled={expanded}>
                    <UnstyledButton
                        draggable
                        onDragStart={(e) => onDragStart(e, 'message')}
                        style={{
                            width: expanded ? '90%' : '40px',
                            padding: expanded ? '10px' : '5px',
                            borderRadius: '8px',
                            border: '1px solid #eee',
                            display: 'flex',
                            justifyContent: expanded ? 'flex-start' : 'center',
                            alignItems: 'center',
                            gap: '10px',
                            cursor: 'grab'
                        }}
                    >
                        <IconMessage size={24} />
                        {expanded && (
                            <div>
                                <Text size="sm" fw={500}>Сообщение</Text>
                                <Text size="xs" c="dimmed">Отправить текст</Text>
                            </div>
                        )}
                    </UnstyledButton>
                </Tooltip>

                <Tooltip label="Вопрос" position="right" disabled={expanded}>
                    <UnstyledButton
                        draggable
                        onDragStart={(e) => onDragStart(e, 'question')}
                        style={{
                            width: expanded ? '90%' : '40px',
                            padding: expanded ? '10px' : '5px',
                            borderRadius: '8px',
                            border: '1px solid #eee',
                            display: 'flex',
                            justifyContent: expanded ? 'flex-start' : 'center',
                            alignItems: 'center',
                            gap: '10px',
                            cursor: 'grab'
                        }}
                    >
                        <IconQuestionMark size={24} />
                        {expanded && (
                            <div>
                                <Text size="sm" fw={500}>Вопрос</Text>
                                <Text size="xs" c="dimmed">Задать вопрос</Text>
                            </div>
                        )}
                    </UnstyledButton>
                </Tooltip>

                <Tooltip label="Кнопки" position="right" disabled={expanded}>
                    <UnstyledButton
                        draggable
                        onDragStart={(e) => onDragStart(e, 'single_choice')}
                        style={{
                            width: expanded ? '90%' : '40px',
                            padding: expanded ? '10px' : '5px',
                            borderRadius: '8px',
                            border: '1px solid #eee',
                            display: 'flex',
                            justifyContent: expanded ? 'flex-start' : 'center',
                            alignItems: 'center',
                            gap: '10px',
                            cursor: 'grab'
                        }}
                    >
                        <IconListCheck size={24} color="#228be6" />
                        {expanded && (
                            <div>
                                <Text size="sm" fw={500}>Кнопки</Text>
                                <Text size="xs" c="dimmed">Выбор из списка</Text>
                            </div>
                        )}
                    </UnstyledButton>
                </Tooltip>

                <Tooltip label="Условие" position="right" disabled={expanded}>
                    <UnstyledButton
                        draggable
                        onDragStart={(e) => onDragStart(e, 'condition')}
                        style={{
                            width: expanded ? '90%' : '40px',
                            padding: expanded ? '10px' : '5px',
                            borderRadius: '8px',
                            border: '1px solid #eee',
                            display: 'flex',
                            justifyContent: expanded ? 'flex-start' : 'center',
                            alignItems: 'center',
                            gap: '10px',
                            cursor: 'grab'
                        }}
                    >
                        <IconGitBranch size={24} color="#fa5252" />
                        {expanded && (
                            <div>
                                <Text size="sm" fw={500}>Условие</Text>
                                <Text size="xs" c="dimmed">Ветвление</Text>
                            </div>
                        )}
                    </UnstyledButton>
                </Tooltip>
            </Stack>
        </div>
    );
}
