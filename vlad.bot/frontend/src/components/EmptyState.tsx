import { Stack, Title, Text, Button, ThemeIcon } from '@mantine/core';
import { ReactNode } from 'react';

interface EmptyStateProps {
    icon: ReactNode;
    title: string;
    description: string;
    actionLabel?: string;
    onAction?: () => void;
}

export function EmptyState({ icon, title, description, actionLabel, onAction }: EmptyStateProps) {
    return (
        <Stack align="center" gap="md" py={60}>
            <ThemeIcon size={80} radius={100} variant="light" color="gray" style={{ opacity: 0.5 }}>
                <div style={{ fontSize: 40, lineHeight: 1 }}>{icon}</div>
            </ThemeIcon>
            <Title order={3} ta="center">{title}</Title>
            <Text c="dimmed" ta="center" maw={400} mx="auto">
                {description}
            </Text>
            {actionLabel && onAction && (
                <Button onClick={onAction} size="md" mt="sm" variant="light">
                    {actionLabel}
                </Button>
            )}
        </Stack>
    );
}
