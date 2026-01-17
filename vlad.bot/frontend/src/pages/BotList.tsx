import { useEffect, useState } from 'react';
import { Title, Button, Container, Text, Stack, Group, Badge, ActionIcon, Modal, SimpleGrid, Card, ThemeIcon, TextInput } from '@mantine/core';
import { useNavigate } from 'react-router-dom';
import { useDisclosure } from '@mantine/hooks';
import { IconTrash, IconEdit, IconTemplate, IconMessage, IconQuestionMark, IconListCheck, IconUsers, IconChartPie, IconPlus, IconHierarchy2, IconRobot } from '@tabler/icons-react';
import { botsApi, scriptsApi } from '../api';
import { showSuccess, showError } from '../utils/notifications';
import { motion } from 'framer-motion';
import { fadeIn, staggerContainer } from '../utils/animations';
import { WelcomeScreen } from '../components/WelcomeScreen';
import { EmptyState } from '../components/EmptyState';
import { Skeleton } from '@mantine/core';


interface Bot {
    id: number;
    name: string;
    status: string;
}

// Шаблоны сценариев
const TEMPLATES = [
    {
        id: 'welcome',
        name: '👋 Приветствие',
        description: 'Простое приветственное сообщение',
        complexity: 'Простой',
        nodes: [
            { id: 'start-1', type: 'start', position: { x: 100, y: 100 }, data: { trigger: 'Новый чат' } },
            { id: 'message-1', type: 'message', position: { x: 100, y: 200 }, data: { text: 'Привет! 👋\n\nДобро пожаловать в нашу компанию!\n\nЕсли у вас есть вопросы - обращайтесь к HR.' } },
        ],
        edges: [
            { id: 'e1', source: 'start-1', target: 'message-1' },
        ],
    },
    {
        id: 'simple_survey',
        name: '📋 Простой опрос',
        description: '3 вопроса с текстовыми ответами',
        complexity: 'Простой',
        nodes: [
            { id: 'start-1', type: 'start', position: { x: 250, y: 50 }, data: { trigger: 'Новый чат' } },
            { id: 'message-1', type: 'message', position: { x: 250, y: 150 }, data: { text: 'Привет! Пройди короткий опрос о первых днях в компании.' } },
            { id: 'question-1', type: 'question', position: { x: 250, y: 280 }, data: { text: 'Что тебе больше всего понравилось в первую неделю?', variable: 'liked' } },
            { id: 'question-2', type: 'question', position: { x: 250, y: 410 }, data: { text: 'Что можно улучшить в процессе онбординга?', variable: 'improve' } },
            { id: 'question-3', type: 'question', position: { x: 250, y: 540 }, data: { text: 'Есть ли вопросы к HR?', variable: 'questions' } },
            { id: 'message-2', type: 'message', position: { x: 250, y: 670 }, data: { text: 'Спасибо за ответы! 🙏\n\nМы ценим твою обратную связь.' } },
        ],
        edges: [
            { id: 'e1', source: 'start-1', target: 'message-1' },
            { id: 'e2', source: 'message-1', target: 'question-1' },
            { id: 'e3', source: 'question-1', target: 'question-2' },
            { id: 'e4', source: 'question-2', target: 'question-3' },
            { id: 'e5', source: 'question-3', target: 'message-2' },
        ],
    },
    {
        id: 'nps_survey',
        name: '⭐ NPS Опрос',
        description: 'Оценка удовлетворенности с выбором',
        complexity: 'Средний',
        nodes: [
            { id: 'start-1', type: 'start', position: { x: 250, y: 50 }, data: { trigger: 'Новый чат' } },
            { id: 'message-1', type: 'message', position: { x: 250, y: 150 }, data: { text: 'Привет! 👋\n\nМы хотим узнать как у тебя дела в компании.' } },
            {
                id: 'single-1', type: 'single_choice', position: { x: 250, y: 280 }, data: {
                    text: 'Насколько ты готов(а) рекомендовать нашу компанию друзьям?',
                    variable: 'nps_score',
                    options: [
                        { label: '😍 Обязательно! (9-10)', value: 'promoter' },
                        { label: '😊 Скорее да (7-8)', value: 'passive' },
                        { label: '😐 Пока не уверен(а) (5-6)', value: 'neutral' },
                        { label: '😕 Скорее нет (0-4)', value: 'detractor' },
                    ]
                }
            },
            { id: 'question-1', type: 'question', position: { x: 250, y: 480 }, data: { text: 'Что повлияло на твою оценку?', variable: 'nps_reason' } },
            { id: 'message-2', type: 'message', position: { x: 250, y: 610 }, data: { text: 'Спасибо за честную обратную связь! 💙\n\nМы работаем над улучшениями.' } },
        ],
        edges: [
            { id: 'e1', source: 'start-1', target: 'message-1' },
            { id: 'e2', source: 'message-1', target: 'single-1' },
            { id: 'e3', source: 'single-1', target: 'question-1' },
            { id: 'e4', source: 'question-1', target: 'message-2' },
        ],
    },
    {
        id: 'training_quiz',
        name: '📚 Тренинг с тестом',
        description: 'Обучающий материал + проверка знаний',
        complexity: 'Сложный',
        nodes: [
            { id: 'start-1', type: 'start', position: { x: 300, y: 50 }, data: { trigger: 'Новый чат' } },
            { id: 'message-1', type: 'message', position: { x: 300, y: 150 }, data: { text: '📚 Тренинг: Основы безопасности\n\nЭтот тренинг займет ~5 минут.\n\nВ конце будет короткий тест.' } },
            { id: 'message-2', type: 'message', position: { x: 300, y: 280 }, data: { text: '🔐 Урок 1: Пароли\n\n✅ Используй минимум 12 символов\n✅ Сочетай буквы, цифры, символы\n✅ Не используй личные данные\n✅ Разные пароли для разных сервисов', interactive: true } },
            { id: 'message-3', type: 'message', position: { x: 300, y: 430 }, data: { text: '🎣 Урок 2: Фишинг\n\n⚠️ Проверяй отправителя письма\n⚠️ Не кликай подозрительные ссылки\n⚠️ При сомнениях - обратись в IT', interactive: true } },
            {
                id: 'single-1', type: 'single_choice', position: { x: 300, y: 580 }, data: {
                    text: '❓ Вопрос 1:\nКакой минимальный размер пароля рекомендуется?',
                    variable: 'q1',
                    options: [
                        { label: '6 символов', value: 'wrong1' },
                        { label: '8 символов', value: 'wrong2' },
                        { label: '12 символов', value: 'correct' },
                    ]
                }
            },
            { id: 'message-correct', type: 'message', position: { x: 550, y: 720 }, data: { text: '✅ Правильно! 12 символов минимум.' } },
            { id: 'message-wrong', type: 'message', position: { x: 100, y: 720 }, data: { text: '❌ Неправильно. Рекомендуется минимум 12 символов.' } },
            { id: 'message-final', type: 'message', position: { x: 300, y: 850 }, data: { text: '🎉 Тренинг пройден!\n\nСпасибо за обучение.\nЕсли есть вопросы - обращайся в IT.' } },
        ],
        edges: [
            { id: 'e1', source: 'start-1', target: 'message-1' },
            { id: 'e2', source: 'message-1', target: 'message-2' },
            { id: 'e3', source: 'message-2', target: 'message-3' },
            { id: 'e4', source: 'message-3', target: 'single-1' },
            { id: 'e5', source: 'single-1', sourceHandle: 'option-2', target: 'message-correct' },
            { id: 'e6', source: 'single-1', sourceHandle: 'option-0', target: 'message-wrong' },
            { id: 'e7', source: 'single-1', sourceHandle: 'option-1', target: 'message-wrong' },
            { id: 'e8', source: 'message-correct', target: 'message-final' },
            { id: 'e9', source: 'message-wrong', target: 'message-final' },
        ],
    },
];

export function BotList() {
    const [bots, setBots] = useState<Bot[]>([]);
    const [loading, setLoading] = useState(true);
    const navigate = useNavigate();
    const [templateModalOpened, { open: openTemplates, close: closeTemplates }] = useDisclosure(false);
    const [welcomeOpened, setWelcomeOpened] = useState(false);
    const [createModalOpened, { open: openCreateModal, close: closeCreateModal }] = useDisclosure(false);
    const [newBotName, setNewBotName] = useState('');

    useEffect(() => {
        loadBots();
        const hasVisited = localStorage.getItem('has_visited');
        if (!hasVisited) {
            setWelcomeOpened(true);
            localStorage.setItem('has_visited', 'true');
        }
    }, []);

    const loadBots = async () => {
        try {
            const data = await botsApi.list();
            setBots(data);
        } catch (error: any) {
            if (error.response?.status === 401) {
                navigate('/login');
            } else {
                console.error('Failed to load bots:', error);
            }
        } finally {
            setLoading(false);
        }
    };

    const handleCreateBot = async () => {
        if (!newBotName.trim()) return;
        try {
            await botsApi.create({ name: newBotName });
            await loadBots();
            showSuccess('Сценарий успешно создан');
            setNewBotName('');
            closeCreateModal();
        } catch (error) {
            console.error('Failed to create bot:', error);
            showError('Ошибка создания сценария');
        }
    };

    const createBotDirect = async (name: string) => {
        try {
            const newBot = await botsApi.create({ name });
            await loadBots();
            showSuccess('Сценарий успешно создан');
            return newBot;
        } catch (error) {
            console.error('Failed to create bot:', error);
            showError('Ошибка создания сценария');
            return null;
        }
    };

    const deleteBot = async (id: number, name: string) => {
        if (confirm(`Удалить сценарий "${name}"?\n\nЭто действие нельзя отменить.`)) {
            try {
                await botsApi.delete(id);
                await loadBots();
                showSuccess(`Сценарий "${name}" удалён`);
            } catch (error) {
                console.error('Failed to delete bot:', error);
                showError('Ошибка удаления сценария');
            }
        }
    };

    const createFromTemplate = async (template: typeof TEMPLATES[0]) => {
        try {
            const newBot = await createBotDirect(template.name);
            if (newBot) {
                // Save template graph
                await scriptsApi.saveDraft(newBot.id, {
                    nodes: template.nodes,
                    edges: template.edges
                });
                closeTemplates();
                navigate(`/bot/${newBot.id}`);
            }
        } catch (error) {
            console.error('Failed to create from template:', error);
            showError('Ошибка создания из шаблона');
        }
    };

    return (
        <Container>
            <Stack gap="md">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
                    <div>
                        <Title order={2} className="gradient-text">Мои сценарии</Title>
                        <Text c="dimmed" size="sm">Управляйте вашими процессами</Text>
                    </div>
                    <Group>
                        <Button variant="default" onClick={openTemplates} leftSection={<IconTemplate size={16} />}>
                            Из шаблона
                        </Button>
                        <Button onClick={openCreateModal} leftSection={<IconPlus size={16} />}>
                            Новый сценарий
                        </Button>
                    </Group>
                </div>

                {loading ? (
                    <Stack>
                        <Skeleton height={80} radius="md" />
                        <Skeleton height={80} radius="md" />
                        <Skeleton height={80} radius="md" />
                    </Stack>
                ) : bots.length === 0 ? (
                    <EmptyState
                        icon={<IconHierarchy2 />}
                        title="У вас пока нет сценариев"
                        description="Создайте свой первый сценарий с нуля или воспользуйтесь готовым шаблоном, чтобы быстро начать."
                        actionLabel="Создать сценарий"
                        onAction={openCreateModal}
                    />
                ) : (
                    <Stack gap="sm" component={motion.div} variants={staggerContainer} initial="initial" animate="animate">
                        {bots.map((bot) => (
                            <motion.div variants={fadeIn} key={bot.id}>
                                <Card
                                    withBorder
                                    shadow="sm"
                                    padding="lg"
                                    radius="md"
                                    className="card-hoverable"
                                    style={{ cursor: 'pointer' }}
                                    onClick={() => navigate(`/bot/${bot.id}`)}
                                >
                                    <Group justify="space-between">
                                        <Group gap="md">
                                            <ThemeIcon size={40} radius="md" variant="light" color={bot.name.includes('Опрос') ? 'orange' : 'blue'}>
                                                {bot.name.includes('Опрос') ? <IconListCheck size={24} /> : <IconHierarchy2 size={24} />}
                                            </ThemeIcon>
                                            <div>
                                                <Text fw={600} size="lg">{bot.name}</Text>
                                                <Group gap={6}>
                                                    <Badge
                                                        size="sm"
                                                        variant="dot"
                                                        color={bot.status === 'published' ? 'green' : 'gray'}
                                                    >
                                                        {bot.status === 'published' ? 'Опубликован' : 'Черновик'}
                                                    </Badge>
                                                    <Text size="xs" c="dimmed">• Изменен недавно</Text>
                                                </Group>
                                            </div>
                                        </Group>
                                        <Group gap="xs">
                                            <Button
                                                size="xs"
                                                variant="subtle"
                                                color="gray"
                                                leftSection={<IconChartPie size={14} />}
                                                onClick={(e) => { e.stopPropagation(); navigate(`/bot/${bot.id}/results`); }}
                                            >
                                                Результаты
                                            </Button>
                                            <Button
                                                size="xs"
                                                variant="light"
                                                color="blue"
                                                leftSection={<IconEdit size={14} />}
                                                onClick={(e) => { e.stopPropagation(); navigate(`/bot/${bot.id}`); }}
                                            >
                                                Редактор
                                            </Button>
                                            <ActionIcon
                                                size="md"
                                                variant="subtle"
                                                color="red"
                                                onClick={(e) => { e.stopPropagation(); deleteBot(bot.id, bot.name); }}
                                            >
                                                <IconTrash size={16} />
                                            </ActionIcon>
                                        </Group>
                                    </Group>
                                </Card>
                            </motion.div>
                        ))}
                    </Stack>
                )}
            </Stack>

            {/* Create Bot Modal */}
            <Modal opened={createModalOpened} onClose={closeCreateModal} title="Новый сценарий">
                <form onSubmit={(e) => { e.preventDefault(); handleCreateBot(); }}>
                    <Stack>
                        <TextInput
                            label="Название"
                            placeholder="Например: Онбординг сотрудников"
                            data-autofocus
                            required
                            value={newBotName}
                            onChange={(e) => setNewBotName(e.currentTarget.value)}
                        />
                        <Group justify="flex-end" mt="md">
                            <Button variant="default" onClick={closeCreateModal}>Отмена</Button>
                            <Button type="submit">Создать</Button>
                        </Group>
                    </Stack>
                </form>
            </Modal>

            {/* Templates Modal */}
            <Modal
                opened={templateModalOpened}
                onClose={closeTemplates}
                title="Выберите шаблон"
                size="lg"
            >
                <SimpleGrid cols={2} spacing="md">
                    {TEMPLATES.map((template) => (
                        <Card
                            key={template.id}
                            withBorder
                            padding="md"
                            style={{ cursor: 'pointer' }}
                            onClick={() => createFromTemplate(template)}
                        >
                            <Text fw={500} mb={4}>{template.name}</Text>
                            <Text size="sm" c="dimmed" mb="xs">{template.description}</Text>
                            <Badge size="sm" variant="outline" color={
                                template.complexity === 'Простой' ? 'green' :
                                    template.complexity === 'Средний' ? 'yellow' : 'orange'
                            }>
                                {template.complexity}
                            </Badge>
                        </Card>
                    ))}
                </SimpleGrid>
            </Modal>
            {/* Welcome Screen */}
            <WelcomeScreen opened={welcomeOpened} onClose={() => setWelcomeOpened(false)} />
        </Container>
    );
}

export default BotList;
