import { Handle, Position } from '@xyflow/react';
import { Card, Text, Group, Badge, Stack, Button } from '@mantine/core';
import { IconListCheck } from '@tabler/icons-react';

// Helper functions
const isImageFile = (url: string): boolean => {
    if (!url) return false;
    return /\.(jpg|jpeg|png|gif|webp|svg)$/i.test(url);
};

const getFileName = (url: string): string => {
    if (!url) return '';
    const parts = url.split('/');
    const filename = parts[parts.length - 1];
    return decodeURIComponent(filename.replace(/^[a-f0-9-]+-/, ''));
};

const getFileIcon = (url: string): string => {
    const ext = url.split('.').pop()?.toLowerCase() || '';
    const iconMap: Record<string, string> = {
        pdf: '📕', doc: '📘', docx: '📘', xls: '📗', xlsx: '📗',
        ppt: '📙', pptx: '📙', txt: '📄', zip: '📦', rar: '📦',
    };
    return iconMap[ext] || '📎';
};

export function QuestionSingleChoiceNode({ data, selected }: { data: any, selected?: boolean }) {
    const options = data.options || [];

    return (
        <Card
            shadow="sm"
            p="xs"
            radius="md"
            withBorder
            style={{
                minWidth: 220,
                maxWidth: 300,
                borderColor: selected ? '#228be6' : '#e0e0e0',
                borderWidth: selected ? '2px' : '1px'
            }}
        >
            <Group mb={5} gap="xs">
                <IconListCheck size={16} color="#228be6" />
                <Text fw={500} size="sm">Кнопки (Выбор)</Text>
            </Group>

            <Text size="sm" mb={8} lineClamp={2}>
                {data.text || <span style={{ color: '#adb5bd', fontStyle: 'italic' }}>Текст вопроса...</span>}
            </Text>

            {/* Multiple Files Display */}
            {data.file_urls && data.file_urls.length > 0 && (
                <Stack gap={4} mb={8}>
                    {data.file_urls.slice(0, 2).map((url: string, idx: number) => (
                        <div key={idx}>
                            {isImageFile(url) ? (
                                <img
                                    src={url}
                                    alt="Attached"
                                    style={{
                                        width: '100%',
                                        maxHeight: 80,
                                        objectFit: 'cover',
                                        borderRadius: 4,
                                        border: '1px solid #dee2e6'
                                    }}
                                />
                            ) : (
                                <Badge
                                    size="xs"
                                    variant="outline"
                                    leftSection={getFileIcon(url)}
                                    fullWidth
                                    style={{ justifyContent: 'flex-start' }}
                                >
                                    Файл
                                </Badge>
                            )}
                        </div>
                    ))}
                    {data.file_urls.length > 2 && (
                        <Badge variant="outline" size="xs" fullWidth>
                            +{data.file_urls.length - 2} ещё
                        </Badge>
                    )}
                </Stack>
            )}

            {/* Media Badge */}
            {data.media_url && (
                <Badge
                    size="xs"
                    variant="outline"
                    leftSection="📺"
                    fullWidth
                    style={{ justifyContent: 'flex-start', marginTop: 4, marginBottom: 8 }}
                >
                    Медиа
                </Badge>
            )}

            {/* Link Badge */}
            {data.link_url && (
                <Badge
                    size="xs"
                    variant="outline"
                    leftSection="🔗"
                    fullWidth
                    color="grape"
                    style={{ justifyContent: 'flex-start', marginTop: 4, marginBottom: 8 }}
                >
                    Ссылка
                </Badge>
            )}

            {/* Options with individual source handles */}
            <Stack gap={4} mt="xs">
                {options.map((opt: any, idx: number) => (
                    <div key={idx} style={{ position: 'relative' }}>
                        {/* Left Handle */}
                        <Handle
                            type="source"
                            position={Position.Left}
                            id={`option-${idx}-left`}
                            className="custom-handle"
                            style={{ left: -10, top: '50%', transform: 'translateY(-50%)' }}
                        >
                            <div className="handle-dot" style={{ backgroundColor: '#228be6' }} />
                        </Handle>

                        <Button
                            size="xs"
                            variant="light"
                            fullWidth
                            style={{ cursor: 'default', justifyContent: 'center', paddingLeft: '20px', paddingRight: '20px' }}
                        >
                            {opt.label}
                        </Button>

                        {/* Right Handle */}
                        <Handle
                            type="source"
                            position={Position.Right}
                            id={`option-${idx}`}
                            className="custom-handle"
                            style={{ right: -10, top: '50%', transform: 'translateY(-50%)' }}
                        >
                            <div className="handle-dot" style={{ backgroundColor: '#228be6' }} />
                        </Handle>
                    </div>
                ))}
            </Stack>

            {/* Base handles on 4 sides - positioned to not overlap with option handles */}
            {/* Top - Input */}
            <Handle type="target" position={Position.Top} id="top" className="custom-handle" style={{ top: -10, left: '50%', transform: 'translateX(-50%)' }}>
                <div className="handle-dot" style={{ backgroundColor: '#228be6' }} />
            </Handle>

            {/* Right - Output (below options) */}
            <Handle type="source" position={Position.Right} id="right" className="custom-handle" style={{ right: -10, bottom: 15, transform: 'translateY(0)' }}>
                <div className="handle-dot" style={{ backgroundColor: '#228be6' }} />
            </Handle>

            {/* Bottom - Output */}
            <Handle type="source" position={Position.Bottom} id="bottom" className="custom-handle" style={{ bottom: -10, left: '50%', transform: 'translateX(-50%)' }}>
                <div className="handle-dot" style={{ backgroundColor: '#228be6' }} />
            </Handle>

            {/* Left - Input */}
            <Handle type="target" position={Position.Left} id="left" className="custom-handle" style={{ left: -10, top: '50%', transform: 'translateY(-50%)' }}>
                <div className="handle-dot" style={{ backgroundColor: '#228be6' }} />
            </Handle>
        </Card>
    );
}
