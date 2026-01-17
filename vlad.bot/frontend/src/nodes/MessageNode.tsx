import { Handle, Position } from '@xyflow/react';
import { Card, Text, Group, Stack, Badge, Button } from '@mantine/core';
import { IconMessage, IconFile } from '@tabler/icons-react';

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

export function MessageNode({ data, selected }: { data: any, selected?: boolean }) {
    return (
        <Card
            shadow="sm"
            p="xs"
            radius="md"
            withBorder
            style={{
                minWidth: 200,
                maxWidth: 300,
                borderColor: selected ? '#228be6' : '#e0e0e0',
                borderWidth: selected ? '2px' : '1px'
            }}
        >
            <Group mb={5} gap="xs">
                <IconMessage size={16} color="#228be6" />
                <Text fw={500} size="sm">Сообщение</Text>
            </Group>

            <Text size="sm" lineClamp={3} mb="xs">
                {data.text || <span style={{ color: '#adb5bd', fontStyle: 'italic' }}>Пустое сообщение...</span>}
            </Text>

            {/* Multiple Files Display */}
            {data.file_urls && data.file_urls.length > 0 && (
                <Stack gap={4} mb="xs">
                    {data.file_urls.slice(0, 2).map((url: string, idx: number) => (
                        <div key={idx}>
                            {/* Image Preview */}
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
                                /* File Badge (for non-images) */
                                <Badge
                                    variant="light"
                                    color="blue"
                                    size="xs"
                                    fullWidth
                                    leftSection={getFileIcon(url)}
                                    style={{ textTransform: 'none', justifyContent: 'flex-start' }}
                                >
                                    {getFileName(url)}
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

            {/* Media URL Badge */}
            {data.media_url && (
                <Badge
                    variant="light"
                    color="red"
                    size="sm"
                    fullWidth
                    style={{ textTransform: 'none', justifyContent: 'flex-start', marginBottom: 4 }}
                >
                    {data.media_url.includes('youtube') ? '📺 YouTube' : '🔗 Медиа'}
                </Badge>
            )}

            {/* Link URL Badge */}
            {data.link_url && (
                <Badge
                    variant="light"
                    color="grape"
                    size="sm"
                    fullWidth
                    style={{ textTransform: 'none', justifyContent: 'flex-start', marginBottom: 4 }}
                >
                    🔗 Ссылка
                </Badge>
            )}

            {data.interactive && (
                <Button fullWidth size="xs" variant="light" mt="xs" color="gray" style={{ cursor: 'default' }}>
                    Далее
                </Button>
            )}

            {/* Handles - one per side */}
            <Handle type="target" position={Position.Top} id="top" className="custom-handle" style={{ top: -10, left: '50%', transform: 'translateX(-50%)' }}>
                <div className="handle-dot" style={{ backgroundColor: '#74c0fc' }} />
            </Handle>

            <Handle type="source" position={Position.Right} id="right" className="custom-handle" style={{ right: -10, top: '50%', transform: 'translateY(-50%)' }}>
                <div className="handle-dot" style={{ backgroundColor: '#74c0fc' }} />
            </Handle>

            <Handle type="source" position={Position.Bottom} id="bottom" className="custom-handle" style={{ bottom: -10, left: '50%', transform: 'translateX(-50%)' }}>
                <div className="handle-dot" style={{ backgroundColor: '#74c0fc' }} />
            </Handle>

            <Handle type="target" position={Position.Left} id="left" className="custom-handle" style={{ left: -10, top: '50%', transform: 'translateY(-50%)' }}>
                <div className="handle-dot" style={{ backgroundColor: '#74c0fc' }} />
            </Handle>
        </Card>
    );
}
