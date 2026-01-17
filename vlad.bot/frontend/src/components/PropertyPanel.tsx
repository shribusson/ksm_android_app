import { Paper, Title, TextInput, Textarea, Stack, Text, Button, Group, Select, ActionIcon, Switch, FileButton, Alert, Badge } from '@mantine/core';
import type { Node } from '@xyflow/react';
import { IconX, IconTrash, IconPlus, IconUpload, IconPlayerPlay, IconInfoCircle, IconArrowRight } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import { MediaAttachmentsSection } from './MediaAttachmentsSection';

interface PropertyPanelProps {
    selectedNode: Node | null;
    onChange: (id: string, data: any) => void;
    onClose: () => void;
}

export function PropertyPanel({ selectedNode, onChange, onClose }: PropertyPanelProps) {
    const [formData, setFormData] = useState(selectedNode?.data || {});
    const [collapsed, setCollapsed] = useState(false);
    const [width, setWidth] = useState(320);

    useEffect(() => {
        setFormData(selectedNode?.data || {});
    }, [selectedNode]);

    const handleChange = (field: string, value: any) => {
        const updated = { ...formData, [field]: value };
        setFormData(updated);
        if (selectedNode) {
            onChange(selectedNode.id, updated);
        }
    };

    if (!selectedNode) return null;

    // Special case: Start Node
    if (selectedNode.type === 'start') {
        return (
            <Paper p="md" style={{ height: '100%', overflow: 'auto' }}>
                <Group justify="space-between" mb="md">
                    <Group gap="xs">
                        <IconPlayerPlay size={20} color="#40c057" />
                        <Title order={5}>Старт</Title>
                    </Group>
                    <ActionIcon variant="subtle" onClick={onClose}>
                        <IconX size={16} />
                    </ActionIcon>
                </Group>

                <Alert icon={<IconInfoCircle size={16} />} title="Точка входа" color="blue" variant="light">
                    <Text size="sm">
                        Start Node - это маркер начала сценария.
                        <br /><br />
                        Когда вы назначаете опрос сотруднику, выполнение начнется отсюда.
                        <br /><br />
                        <strong>Настройки не требуются</strong> - просто соедините Start с первым блоком вашего сценария.
                    </Text>
                </Alert>
            </Paper>
        );
    }

    const handleUpload = async (file: File | null) => {
        if (!file) return;
        const formData = new FormData();
        formData.append('file', file);

        try {
            const res = await fetch('/upload', {
                method: 'POST',
                body: formData,
            });
            if (!res.ok) throw new Error('Upload failed');
            const data = await res.json();
            handleChange('file_url', data.url);
        } catch (err) {
            console.error(err);
            alert('Ошибка загрузки файла');
        }
    };

    // Helper functions for file display
    const getFileName = (url: string): string => {
        if (!url) return '';
        const parts = url.split('/');
        const filename = parts[parts.length - 1];
        // Remove UUID prefix if present (e.g., "feb9c7b9-3abc-file.pdf" -> "file.pdf")
        const withoutUuid = filename.replace(/^[a-f0-9-]+-/, '');
        return decodeURIComponent(withoutUuid);
    };

    const getFileExtension = (url: string): string => {
        const filename = getFileName(url);
        const parts = filename.split('.');
        return parts.length > 1 ? parts[parts.length - 1].toLowerCase() : '';
    };

    const getFileIcon = (url: string): string => {
        const ext = getFileExtension(url);
        const iconMap: Record<string, string> = {
            pdf: '📕',
            doc: '📘',
            docx: '📘',
            xls: '📗',
            xlsx: '📗',
            ppt: '📙',
            pptx: '📙',
            txt: '📄',
            png: '🖼️',
            jpg: '🖼️',
            jpeg: '🖼️',
            gif: '🖼️',
            svg: '🖼️',
            zip: '📦',
            rar: '📦',
        };
        return iconMap[ext] || '📎';
    };

    const getFileColor = (url: string): string => {
        const ext = getFileExtension(url);
        const colorMap: Record<string, string> = {
            pdf: 'red',
            doc: 'blue',
            docx: 'blue',
            xls: 'green',
            xlsx: 'green',
            ppt: 'orange',
            pptx: 'orange',
            png: 'cyan',
            jpg: 'cyan',
            jpeg: 'cyan',
            gif: 'pink',
        };
        return colorMap[ext] || 'gray';
    };

    const isYouTubeUrl = (url: string): boolean => {
        if (!url) return false;
        return url.includes('youtube.com') || url.includes('youtu.be');
    };

    const getYouTubeEmbedUrl = (url: string): string => {
        if (!url) return '';
        let videoId = '';

        if (url.includes('youtube.com/watch')) {
            const urlParams = new URLSearchParams(new URL(url).search);
            videoId = urlParams.get('v') || '';
        } else if (url.includes('youtu.be/')) {
            videoId = url.split('youtu.be/')[1]?.split('?')[0] || '';
        }

        return videoId ? `https://www.youtube.com/embed/${videoId}` : '';
    };

    const isImageUrl = (url: string): boolean => {
        if (!url) return false;
        return /\.(jpg|jpeg|png|gif|webp|svg)$/i.test(url);
    };

    return (
        <Paper
            shadow="lg"
            radius={0}
            style={{
                width: collapsed ? 50 : width,
                background: '#fff',
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                borderLeft: '1px solid #e9ecef',
                transition: 'width 0.2s',
                position: 'relative'
            }}
        >
            {/* Toggle Button */}
            <ActionIcon
                variant="subtle"
                color="gray"
                size="sm"
                onClick={() => setCollapsed(!collapsed)}
                style={{ position: 'absolute', top: 12, left: collapsed ? 12 : -12, zIndex: 11 }}
            >
                {collapsed ? <IconX size={14} style={{ transform: 'rotate(45deg)' }} /> : <IconArrowRight size={14} />}
            </ActionIcon>

            {!collapsed && (
                <>
                    <Group justify="space-between" p="md" style={{ borderBottom: '1px solid #eee' }} pl={30}>
                        <Title order={5}>Настройки</Title>
                        <Button variant="subtle" size="xs" p={0} onClick={onClose}><IconX size={18} /></Button>
                    </Group>

                    <Stack p="md" gap="md" style={{ flex: 1, overflowY: 'auto' }}>
                        <Text size="xs" c="dimmed" tt="uppercase" fw={700} style={{ letterSpacing: '0.5px' }}>
                            {selectedNode.type === 'start' ? 'Стартовый блок' :
                                selectedNode.type === 'message' ? 'Сообщение' :
                                    selectedNode.type === 'question' ? 'Вопрос' :
                                        selectedNode.type === 'single_choice' ? 'Кнопки (Выбор)' :
                                            'Условие'}
                        </Text>

                        {/* Form Content - reused from before */}
                        {selectedNode.type === 'start' && (
                            <>
                                <Select
                                    label="Тип запуска"
                                    placeholder="Выберите триггер"
                                    data={['Команда', 'Новый чат', 'Webhook']}
                                    value={String(selectedNode.data.trigger || 'Новый чат')}
                                    onChange={(val) => handleChange('trigger', val || '')}
                                />
                                <Text size="xs" c="dimmed">
                                    Как запускается этот сценарий.
                                </Text>
                            </>
                        )}

                        {selectedNode.type === 'message' && (
                            <>
                                <TextInput
                                    label="Текст сообщения"
                                    value={formData.text || ''}
                                    onChange={(e) => handleChange('text', e.target.value)}
                                    placeholder="Напишите текст..."
                                />

                                <MediaAttachmentsSection
                                    fileUrls={formData.file_urls || []}
                                    mediaUrl={formData.media_url || ''}
                                    linkUrl={formData.link_url || ''}
                                    onFileUrlsChange={(urls) => handleChange('file_urls', urls)}
                                    onMediaUrlChange={(url) => handleChange('media_url', url)}
                                    onLinkUrlChange={(url) => handleChange('link_url', url)}
                                />
                            </>
                        )}

                        {selectedNode.type === 'question' && (
                            <>
                                <TextInput
                                    label="Текст вопроса"
                                    placeholder="Что спросить?"
                                    value={String(selectedNode.data.text || '')}
                                    onChange={(e) => handleChange('text', e.target.value)}
                                />
                                <TextInput
                                    label="Сохранить в переменную"
                                    description="Сохраняется как {имя_переменной}"
                                    placeholder="имя_пользователя"
                                    value={String(selectedNode.data.variable || '')}
                                    onChange={(e) => handleChange('variable', e.target.value)}
                                />
                                <MediaAttachmentsSection
                                    fileUrls={formData.file_urls || []}
                                    mediaUrl={formData.media_url || ''}
                                    linkUrl={formData.link_url || ''}
                                    onFileUrlsChange={(urls) => handleChange('file_urls', urls)}
                                    onMediaUrlChange={(url) => handleChange('media_url', url)}
                                    onLinkUrlChange={(url) => handleChange('link_url', url)}
                                />
                            </>
                        )}

                        {selectedNode.type === 'single_choice' && (
                            <>
                                <TextInput
                                    label="Текст вопроса"
                                    placeholder="Выберите вариант..."
                                    value={String(selectedNode.data.text || '')}
                                    onChange={(e) => handleChange('text', e.target.value)}
                                />
                                <Text size="sm" fw={500} mt="md" mb={5}>Варианты ответа</Text>
                                <Stack gap="xs">
                                    {(selectedNode.data.options || []).map((opt: any, index: number) => (
                                        <Group key={index} gap="xs">
                                            <TextInput
                                                placeholder={`Вариант ${index + 1}`}
                                                value={opt.label}
                                                style={{ flex: 1 }}
                                                onChange={(e) => {
                                                    const newOptions = [...(selectedNode.data.options || [])];
                                                    newOptions[index] = { ...newOptions[index], label: e.target.value, value: e.target.value };
                                                    onChange(selectedNode.id, { ...selectedNode.data, options: newOptions });
                                                }}
                                            />
                                            <ActionIcon
                                                color="red"
                                                variant="light"
                                                onClick={() => {
                                                    const newOptions = selectedNode.data.options.filter((_: any, i: number) => i !== index);
                                                    onChange(selectedNode.id, { ...selectedNode.data, options: newOptions });
                                                }}
                                            >
                                                <IconTrash size={16} />
                                            </ActionIcon>
                                        </Group>
                                    ))}
                                    <Button
                                        variant="default"
                                        size="xs"
                                        leftSection={<IconPlus size={14} />}
                                        onClick={() => {
                                            const newOptions = [...(selectedNode.data.options || []), { label: `Вариант ${(selectedNode.data.options?.length || 0) + 1}`, value: `opt_${Date.now()}` }];
                                            onChange(selectedNode.id, { ...selectedNode.data, options: newOptions });
                                        }}
                                    >
                                        Добавить вариант
                                    </Button>
                                </Stack>
                                <TextInput
                                    label="Сохранить в переменную"
                                    placeholder="choice_result"
                                    value={String(selectedNode.data.variable || '')}
                                    onChange={(e) => handleChange('variable', e.target.value)}
                                />
                                <MediaAttachmentsSection
                                    fileUrls={formData.file_urls || []}
                                    mediaUrl={formData.media_url || ''}
                                    linkUrl={formData.link_url || ''}
                                    onFileUrlsChange={(urls) => handleChange('file_urls', urls)}
                                    onMediaUrlChange={(url) => handleChange('media_url', url)}
                                    onLinkUrlChange={(url) => handleChange('link_url', url)}
                                />
                            </>
                        )}

                        {selectedNode.type === 'condition' && (
                            <>
                                <Text size="sm" fw={500}>Логика проверки</Text>
                                <Group grow>
                                    <TextInput
                                        label="Переменная"
                                        placeholder="score"
                                        value={String(selectedNode.data.variable || '')}
                                        onChange={(e) => handleChange('variable', e.target.value)}
                                    />
                                    <Select
                                        label="Оператор"
                                        data={[
                                            { value: 'equals', label: 'Равно' },
                                            { value: 'not_equals', label: 'Не равно' },
                                            { value: 'contains', label: 'Содержит' },
                                            { value: 'gt', label: '>' },
                                            { value: 'lt', label: '<' }
                                        ]}
                                        value={String(selectedNode.data.operator || 'equals')}
                                        onChange={(val) => handleChange('operator', val || 'equals')}
                                    />
                                </Group>
                                <TextInput
                                    label="Значение"
                                    placeholder="10"
                                    value={String(selectedNode.data.value || '')}
                                    onChange={(e) => handleChange('value', e.target.value)}
                                />
                            </>
                        )}
                    </Stack>
                </>
            )}
        </Paper>
    );
}
