import { useState, useEffect } from 'react';
import { Container, Title, TextInput, Button, Stack, Paper, Text as MantineText, Alert } from '@mantine/core';
import { IconDeviceFloppy, IconCheck, IconKey } from '@tabler/icons-react';
import { settingsApi } from '../api';
import { showSuccess, showError } from '../utils/notifications';
import { motion } from 'framer-motion';
import { fadeIn } from '../utils/animations';

export default function Settings() {
    const [settings, setSettings] = useState<Record<string, string>>({
        telegram_bot_token: '',
        whatsapp_token: '',
        whatsapp_phone_id: '',
    });
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);

    useEffect(() => {
        loadSettings();
    }, []);

    const loadSettings = async () => {
        try {
            const data = await settingsApi.get();
            setSettings(data || {});
        } catch (err) {
            console.error('Failed to load settings:', err);
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async () => {
        setSaving(true);
        setSaved(false);
        try {
            await settingsApi.update(settings);
            setSaved(true);
            showSuccess('Настройки сохранены');
            setTimeout(() => setSaved(false), 3000);
        } catch (err) {
            console.error('Failed to save settings:', err);
            showError('Ошибка сохранения настроек');
        } finally {
            setSaving(false);
        }
    };

    return (
        <Container size="md" py="xl" component={motion.div} variants={fadeIn} initial="initial" animate="animate">
            <Title order={2} mb="lg">Настройки платформы</Title>

            <Stack gap="md">
                <Paper p="md" withBorder>
                    <Title order={4} mb="md">
                        <IconKey size={20} style={{ marginRight: 8, verticalAlign: 'middle' }} />
                        API Токены для мессенджеров
                    </Title>

                    <Alert color="blue" mb="md" variant="light">
                        <MantineText size="sm">
                            Эти токены используются ботами для отправки сообщений в Telegram и WhatsApp.
                            <br />
                            Убедитесь что токены актуальны и имеют необходимые права.
                        </MantineText>
                    </Alert>

                    <Stack gap="sm">
                        <TextInput
                            label="Telegram Bot Token"
                            placeholder="1234567890:ABCdefGHIjklMNOpqrsTUVwxyz"
                            description="Получите токен у @BotFather в Telegram"
                            value={settings.telegram_bot_token || ''}
                            onChange={(e) => setSettings({ ...settings, telegram_bot_token: e.target.value })}
                            leftSection={<IconKey size={16} />}
                        />

                        <TextInput
                            label="WhatsApp Access Token"
                            placeholder="EAAxxxxxxxxxxxxx"
                            description="Meta Business токен для WhatsApp API"
                            value={settings.whatsapp_token || ''}
                            onChange={(e) => setSettings({ ...settings, whatsapp_token: e.target.value })}
                            leftSection={<IconKey size={16} />}
                        />

                        <TextInput
                            label="WhatsApp Phone Number ID"
                            placeholder="123456789012345"
                            description="ID телефонного номера в WhatsApp Business API"
                            value={settings.whatsapp_phone_id || ''}
                            onChange={(e) => setSettings({ ...settings, whatsapp_phone_id: e.target.value })}
                        />
                    </Stack>

                    <Button
                        mt="xl"
                        leftSection={saved ? <IconCheck size={16} /> : <IconDeviceFloppy size={16} />}
                        onClick={handleSave}
                        loading={saving}
                        color={saved ? 'green' : 'blue'}
                        fullWidth
                    >
                        {saved ? 'Сохранено!' : 'Сохранить настройки'}
                    </Button>
                </Paper>

                <Paper p="md" withBorder>
                    <Title order={4} mb="md">Информация</Title>
                    <MantineText size="sm" c="dimmed">
                        <strong>Telegram Bot:</strong> После добавления токена, убедитесь что webhook настроен корректно.
                        <br />
                        <strong>WhatsApp:</strong> Используется WhatsApp Business API (не WhatsApp Business App).
                    </MantineText>
                </Paper>
            </Stack>
        </Container>
    );
}
