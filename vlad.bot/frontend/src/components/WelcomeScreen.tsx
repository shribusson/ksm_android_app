import { Modal, Title, Text, Button, Stack, List, ThemeIcon, Group } from '@mantine/core';
import { IconRocket, IconChartBar, IconUsers, IconTemplate } from '@tabler/icons-react';

interface WelcomeScreenProps {
    opened: boolean;
    onClose: () => void;
}

export function WelcomeScreen({ opened, onClose }: WelcomeScreenProps) {
    return (
        <Modal
            opened={opened}
            onClose={onClose}
            withCloseButton={false}
            size="lg"
            radius="xl"
            centered
            overlayProps={{
                backgroundOpacity: 0.55,
                blur: 3,
            }}
        >
            <Stack gap="xl" py="md">
                <div style={{ textAlign: 'center' }}>
                    <ThemeIcon size={64} radius="xl" color="blue" variant="light" mb="md">
                        <IconRocket size={36} />
                    </ThemeIcon>
                    <Title order={2} mb="xs">Добро пожаловать в HR Bot Platform! 👋</Title>
                    <Text c="dimmed" size="lg">
                        Ваш инструмент для автоматизации HR-процессов, онбординга и опросов.
                    </Text>
                </div>

                <List
                    spacing="md"
                    size="md"
                    center
                    icon={
                        <ThemeIcon color="teal" size={24} radius="xl">
                            <IconRocket size={14} />
                        </ThemeIcon>
                    }
                >
                    <List.Item
                        icon={
                            <ThemeIcon color="blue" size={28} radius="md" variant="light">
                                <IconTemplate size={16} />
                            </ThemeIcon>
                        }
                    >
                        <Text span fw={500}>Создавайте сценарии</Text> — используйте визуальный редактор или готовые шаблоны
                    </List.Item>

                    <List.Item
                        icon={
                            <ThemeIcon color="grape" size={28} radius="md" variant="light">
                                <IconUsers size={16} />
                            </ThemeIcon>
                        }
                    >
                        <Text span fw={500}>Добавляйте сотрудников</Text> — управляйте базой и назначайте опросы
                    </List.Item>

                    <List.Item
                        icon={
                            <ThemeIcon color="orange" size={28} radius="md" variant="light">
                                <IconChartBar size={16} />
                            </ThemeIcon>
                        }
                    >
                        <Text span fw={500}>Анализируйте результаты</Text> — получайте детальную статистику ответов
                    </List.Item>
                </List>

                <Group justify="center" mt="md">
                    <Button size="lg" onClick={onClose} fullWidth>
                        Начать работу
                    </Button>
                </Group>
            </Stack>
        </Modal>
    );
}
