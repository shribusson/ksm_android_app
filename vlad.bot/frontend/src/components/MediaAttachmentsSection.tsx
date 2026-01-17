import { Stack, Text, TextInput, FileButton, Button, Group, Badge, ActionIcon } from '@mantine/core';
import { IconUpload, IconTrash } from '@tabler/icons-react';

interface MediaAttachmentsSectionProps {
    fileUrls: string[];
    mediaUrl: string;
    linkUrl: string;
    onFileUrlsChange: (urls: string[]) => void;
    onMediaUrlChange: (url: string) => void;
    onLinkUrlChange: (url: string) => void;
}

// Helper functions
const isImageUrl = (url: string): boolean => {
    if (!url) return false;
    return /\.(jpg|jpeg|png|gif|webp|svg)$/i.test(url);
};

const getFileName = (url: string): string => {
    if (!url) return '';
    const parts = url.split('/');
    const filename = parts[parts.length - 1];
    return decodeURIComponent(filename.replace(/^[a-f0-9-]+-/, ''));
};

const getFileExtension = (url: string): string => {
    const filename = getFileName(url);
    const parts = filename.split('.');
    return parts.length > 1 ? parts[parts.length - 1].toLowerCase() : '';
};

const getFileIcon = (url: string): string => {
    const ext = getFileExtension(url);
    const iconMap: Record<string, string> = {
        pdf: '📕', doc: '📘', docx: '📘', xls: '📗', xlsx: '📗',
        ppt: '📙', pptx: '📙', txt: '📄', png: '🖼️', jpg: '🖼️',
        jpeg: '🖼️', gif: '🖼️', svg: '🖼️', zip: '📦', rar: '📦',
    };
    return iconMap[ext] || '📎';
};

const getFileColor = (url: string): string => {
    const ext = getFileExtension(url);
    const colorMap: Record<string, string> = {
        pdf: 'red', doc: 'blue', docx: 'blue', xls: 'green', xlsx: 'green',
        ppt: 'orange', pptx: 'orange', png: 'cyan', jpg: 'cyan',
        jpeg: 'cyan', gif: 'pink',
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

export function MediaAttachmentsSection({
    fileUrls,
    mediaUrl,
    linkUrl,
    onFileUrlsChange,
    onMediaUrlChange,
    onLinkUrlChange
}: MediaAttachmentsSectionProps) {
    const handleUpload = async (files: File[] | null) => {
        if (!files || files.length === 0) return;

        const uploadPromises = Array.from(files).map(async (file) => {
            const formData = new FormData();
            formData.append('file', file);
            try {
                const res = await fetch('/upload', { method: 'POST', body: formData });
                if (!res.ok) throw new Error('Upload failed');
                const data = await res.json();
                return data.url;
            } catch (err) {
                console.error(err);
                return null;
            }
        });

        const uploadedUrls = (await Promise.all(uploadPromises)).filter(Boolean) as string[];
        onFileUrlsChange([...fileUrls, ...uploadedUrls]);
    };

    const handleRemoveFile = (index: number) => {
        const newUrls = fileUrls.filter((_, i) => i !== index);
        onFileUrlsChange(newUrls);
    };

    return (
        <Stack gap="md">
            {/* File Upload Section */}
            <div>
                <Text size="sm" fw={500} mb={5}>Прикрепить файлы</Text>
                <FileButton
                    onChange={handleUpload}
                    accept="image/*,application/pdf,.doc,.docx,.xls,.xlsx"
                    multiple
                >
                    {(props) => (
                        <Button {...props} size="xs" variant="light" leftSection={<IconUpload size={14} />}>
                            Выбрать файлы
                        </Button>
                    )}
                </FileButton>

                {/* File List */}
                {fileUrls && fileUrls.length > 0 && (
                    <Stack gap={6} mt="xs">
                        {fileUrls.map((url, idx) => (
                            <div key={idx}>
                                {/* Image Preview */}
                                {isImageUrl(url) ? (
                                    <div style={{ position: 'relative' }}>
                                        <img
                                            src={url}
                                            alt="Preview"
                                            style={{
                                                width: '100%',
                                                maxHeight: 150,
                                                objectFit: 'contain',
                                                borderRadius: 4,
                                                border: '1px solid #dee2e6'
                                            }}
                                        />
                                        <ActionIcon
                                            size="sm"
                                            color="red"
                                            variant="filled"
                                            onClick={() => handleRemoveFile(idx)}
                                            style={{ position: 'absolute', top: 4, right: 4 }}
                                        >
                                            <IconTrash size={12} />
                                        </ActionIcon>
                                    </div>
                                ) : (
                                    /* File Badge */
                                    <Group gap="xs">
                                        <Badge
                                            size="lg"
                                            variant="light"
                                            color={getFileColor(url)}
                                            leftSection={getFileIcon(url)}
                                            styles={{ root: { textTransform: 'none', paddingLeft: 8, flex: 1 } }}
                                        >
                                            {getFileName(url)}
                                        </Badge>
                                        <ActionIcon
                                            size="sm"
                                            color="red"
                                            variant="light"
                                            onClick={() => handleRemoveFile(idx)}
                                        >
                                            <IconTrash size={14} />
                                        </ActionIcon>
                                    </Group>
                                )}
                            </div>
                        ))}
                    </Stack>
                )}
            </div>

            {/* Media URL Section */}
            <div>
                <TextInput
                    label="Ссылка на медиа"
                    placeholder="https://youtube.com/watch?v=... или https://..."
                    value={mediaUrl}
                    onChange={(e) => onMediaUrlChange(e.target.value)}
                    description="YouTube видео или прямая ссылка на изображение"
                />

                {/* YouTube Preview */}
                {mediaUrl && isYouTubeUrl(mediaUrl) && (
                    <div style={{ marginTop: 8, border: '1px solid #dee2e6', borderRadius: 4, overflow: 'hidden' }}>
                        <iframe
                            width="100%"
                            height="150"
                            src={getYouTubeEmbedUrl(mediaUrl)}
                            frameBorder="0"
                            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                            allowFullScreen
                            style={{ display: 'block' }}
                        />
                    </div>
                )}

                {/* Image Preview */}
                {mediaUrl && !isYouTubeUrl(mediaUrl) && isImageUrl(mediaUrl) && (
                    <div style={{ marginTop: 8 }}>
                        <img
                            src={mediaUrl}
                            alt="Preview"
                            style={{ maxWidth: '100%', maxHeight: 150, borderRadius: 4, border: '1px solid #dee2e6' }}
                            onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
                        />
                    </div>
                )}
            </div>

            {/* Link URL Section */}
            <TextInput
                label="Ссылка"
                placeholder="https://example.com/article"
                value={linkUrl}
                onChange={(e) => onLinkUrlChange(e.target.value)}
                description="Обычная ссылка на статью, сайт, документацию"
                leftSection={<span>🔗</span>}
            />
        </Stack>
    );
}
