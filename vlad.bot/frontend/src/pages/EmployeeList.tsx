import { useEffect, useState } from 'react';
import { Title, Table, Button, Group, Container, Paper, Avatar, Badge, ActionIcon, Modal, TextInput, Stack, Select, Text } from '@mantine/core';
import { IconPlus, IconBriefcase, IconTrash, IconPencil, IconBrandTelegram, IconBrandWhatsapp, IconRobot, IconUsers } from '@tabler/icons-react';
import { useDisclosure } from '@mantine/hooks';
import api, { botsApi } from '../api';
import { showSuccess, showError } from '../utils/notifications';
import { motion } from 'framer-motion';
import { fadeIn, staggerContainer } from '../utils/animations';
import { EmptyState } from '../components/EmptyState';
import { Skeleton } from '@mantine/core';

interface Employee {
    id: number;
    full_name: string;
    position: string;
    phone: string;
    telegram_id: string;
    department: string;
    tags: string;
}

export function EmployeeList() {
    const [employees, setEmployees] = useState<Employee[]>([]);
    const [bots, setBots] = useState<any[]>([]);
    const [loading, setLoading] = useState(true);
    const [opened, { open, close }] = useDisclosure(false);

    // Assignment Modal State
    const [assignOpened, { open: openAssignModal, close: closeAssignModal }] = useDisclosure(false);
    const [selectedEmployee, setSelectedEmployee] = useState<Employee | null>(null);
    const [selectedBotId, setSelectedBotId] = useState<string | null>(null);

    // Bulk Logic
    const [bulkAssignOpened, { open: openBulkAssign, close: closeBulkAssign }] = useDisclosure(false);
    const [bulkFilters, setBulkFilters] = useState({ department: '', position: '', tags: '' });

    // Derive unique options
    const departments = Array.from(new Set(employees.map(e => e.department).filter(Boolean)));
    const positions = Array.from(new Set(employees.map(e => e.position).filter(Boolean)));
    const allTags = employees
        .flatMap(e => (e.tags || '').split(','))
        .map(t => t.trim())
        .filter(Boolean);
    const uniqueTags = Array.from(new Set(allTags));


    // Form state (simple)
    const [formData, setFormData] = useState({
        full_name: '',
        position: '',
        phone: '',
        telegram_id: '',
        department: '',
        tags: ''
    });

    const fetchData = async () => {
        try {
            const [empRes, botRes] = await Promise.all([
                api.get('/employees/'),
                botsApi.list()
            ]);
            setEmployees(empRes.data);
            setBots(botRes);
        } catch (err) {
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const fetchEmployees = fetchData; // Alias for compatibility with existing code calling fetchEmployees

    useEffect(() => {
        fetchData();
    }, []);

    const openAssign = (emp: Employee) => {
        setSelectedEmployee(emp);
        setSelectedBotId(null);
        openAssignModal();
    };

    const closeAssign = () => {
        setSelectedEmployee(null);
        closeAssignModal();
    };

    const handleAssign = async () => {
        if (!selectedEmployee || !selectedBotId) return;
        try {
            await api.post(`/employees/${selectedEmployee.id}/assign`, {
                bot_id: Number(selectedBotId)
            });
            showSuccess('Бот назначен. Сообщение будет отправлено.');
            closeAssign();
        } catch (err: any) {
            showError(err.response?.data?.detail || 'Ошибка назначения');
        }
    };

    const handleBulkAssign = async () => {
        if (!selectedBotId) return;
        try {
            const res = await api.post('/employees/assign-bulk', {
                bot_id: Number(selectedBotId),
                department: bulkFilters.department || undefined,
                position: bulkFilters.position || undefined,
                tags: bulkFilters.tags || undefined
            });
            showSuccess(`Бот назначен успешно! Охвачено сотрудников: ${res.data.count}`);
            closeBulkAssign();
        } catch (err: any) {
            showError('Ошибка массового назначения');
        }
    };

    const handleCreate = async () => {
        try {
            await api.post('/employees/', formData);
            close();
            setFormData({ full_name: '', position: '', phone: '', telegram_id: '', department: '', tags: '' });
            fetchEmployees();
            showSuccess('Сотрудник добавлен');
        } catch (err) {
            showError('Ошибка создания');
        }
    };

    const handleDelete = async (id: number) => {
        if (!confirm('Удалить сотрудника?')) return;
        try {
            await api.delete(`/employees/${id}`);
            setEmployees(employees.filter(e => e.id !== id));
            showSuccess('Сотрудник удалён');
        } catch (err) {
            showError('Ошибка удаления');
        }
    };

    return (
        <Container size="xl" py="xl">
            <Group justify="space-between" mb="lg">
                <Title order={2}>Сотрудники</Title>
                <Group>
                    <Button variant="default" leftSection={<IconUsers size={18} />} onClick={openBulkAssign}>
                        Массовое назначение
                    </Button>
                    <Button leftSection={<IconPlus size={18} />} onClick={open}>
                        Добавить сотрудника
                    </Button>
                </Group>
            </Group>

            <Paper withBorder p="md" radius="md">
                <Table>
                    <Table.Thead>
                        <Table.Tr>
                            <Table.Th>ФИО</Table.Th>
                            <Table.Th>Должность / Отдел</Table.Th>
                            <Table.Th>Контакты</Table.Th>
                            <Table.Th>Теги</Table.Th>
                            <Table.Th>Действия</Table.Th>
                        </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody component={motion.tbody} variants={staggerContainer} initial="initial" animate="animate">
                        {employees.map((employee) => (
                            <Table.Tr
                                key={employee.id}
                                component={motion.tr}
                                variants={fadeIn}
                            >
                                <Table.Td>
                                    <Group gap="sm">
                                        <Avatar color="blue" radius="xl">
                                            {employee.full_name?.substring(0, 2).toUpperCase()}
                                        </Avatar>
                                        <div style={{ fontWeight: 500 }}>{employee.full_name}</div>
                                    </Group>
                                </Table.Td>
                                <Table.Td>
                                    <div style={{ fontSize: 14, fontWeight: 500 }}>{employee.position || '-'}</div>
                                    <div style={{ fontSize: 12, color: 'gray' }}>{employee.department}</div>
                                </Table.Td>
                                <Table.Td>
                                    <Stack gap={4}>
                                        {employee.phone && (
                                            <Group gap={6}>
                                                <IconBrandWhatsapp size={14} color="green" />
                                                <div style={{ fontSize: 12 }}>{employee.phone}</div>
                                            </Group>
                                        )}
                                        {employee.telegram_id && (
                                            <Group gap={6}>
                                                <IconBrandTelegram size={14} color="#228be6" />
                                                <div style={{ fontSize: 12 }}>{employee.telegram_id}</div>
                                            </Group>
                                        )}
                                    </Stack>
                                </Table.Td>
                                <Table.Td>
                                    <Group gap={4}>
                                        {employee.tags?.split(',').map((tag, i) => (
                                            <Badge key={i} size="xs" variant="dot" color="gray">
                                                {tag.trim()}
                                            </Badge>
                                        ))}
                                    </Group>
                                </Table.Td>
                                <Table.Td>
                                    <Group gap="xs">
                                        <ActionIcon variant="light" color="blue" title="Назначить бота" onClick={() => openAssign(employee)}>
                                            <IconRobot size={16} />
                                        </ActionIcon>
                                        <ActionIcon variant="subtle" color="red" onClick={() => handleDelete(employee.id)}>
                                            <IconTrash size={16} />
                                        </ActionIcon>
                                    </Group>
                                </Table.Td>
                            </Table.Tr>
                        ))}
                    </Table.Tbody>
                </Table>
                {loading && (
                    <Stack gap="sm" p="md">
                        <Skeleton height={40} radius="sm" />
                        <Skeleton height={40} radius="sm" />
                        <Skeleton height={40} radius="sm" />
                    </Stack>
                )}
                {employees.length === 0 && !loading && (
                    <EmptyState
                        icon={<IconUsers />}
                        title="Список сотрудников пуст"
                        description="Добавьте сотрудников вручную или загрузите файлом, чтобы назначать им опросы."
                        actionLabel="Добавить сотрудника"
                        onAction={open}
                    />
                )}
            </Paper>

            <Modal opened={opened} onClose={close} title="Новый сотрудник">
                {/* ... existing form ... */}
                <Stack>
                    <TextInput
                        label="ФИО"
                        required
                        value={formData.full_name}
                        onChange={(e) => setFormData({ ...formData, full_name: e.target.value })}
                    />
                    <TextInput
                        label="Должность"
                        value={formData.position}
                        onChange={(e) => setFormData({ ...formData, position: e.target.value })}
                    />
                    <TextInput
                        label="Телефон (WhatsApp)"
                        placeholder="7999..."
                        value={formData.phone}
                        onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                    />
                    <TextInput
                        label="Telegram ID"
                        placeholder="123456789"
                        value={formData.telegram_id}
                        onChange={(e) => setFormData({ ...formData, telegram_id: e.target.value })}
                    />
                    <TextInput
                        label="Отдел"
                        value={formData.department}
                        onChange={(e) => setFormData({ ...formData, department: e.target.value })}
                    />
                    <TextInput
                        label="Теги"
                        placeholder="sales, onboarding"
                        value={formData.tags}
                        onChange={(e) => setFormData({ ...formData, tags: e.target.value })}
                    />
                    <Button mt="md" fullWidth onClick={handleCreate}>Создать</Button>
                </Stack>
            </Modal>

            {/* Assignment Modal */}
            <Modal opened={assignOpened} onClose={closeAssign} title={`Назначить бота: ${selectedEmployee?.full_name}`}>
                <Stack>
                    <Select
                        label="Выберите бота"
                        placeholder="Опрос / Онбординг..."
                        data={bots.map(b => ({ value: String(b.id), label: b.name }))}
                        value={selectedBotId}
                        onChange={setSelectedBotId}
                    />
                    <Button mt="md" fullWidth onClick={handleAssign} disabled={!selectedBotId}>
                        Назначить и отправить
                    </Button>
                </Stack>
            </Modal>

            {/* Bulk Assignment Modal */}
            <Modal opened={bulkAssignOpened} onClose={closeBulkAssign} title="Назначение">
                <Stack>
                    <Text size="sm" c="dimmed">
                        Выберите критерии для назначения бота сотрудникам.
                    </Text>

                    <Select
                        label="Отдел"
                        placeholder="Выберите отдел"
                        data={departments}
                        searchable
                        clearable
                        value={bulkFilters.department}
                        onChange={(val) => setBulkFilters({ ...bulkFilters, department: val || '' })}
                    />

                    <Select
                        label="Должность"
                        placeholder="Выберите должность"
                        data={positions}
                        searchable
                        clearable
                        value={bulkFilters.position}
                        onChange={(val) => setBulkFilters({ ...bulkFilters, position: val || '' })}
                    />

                    <Select
                        label="Тег"
                        placeholder="Выберите тег"
                        data={uniqueTags}
                        searchable
                        clearable
                        value={bulkFilters.tags}
                        onChange={(val) => setBulkFilters({ ...bulkFilters, tags: val || '' })}
                    />

                    <Select
                        label="Выберите бота"
                        placeholder="Опрос / Онбординг..."
                        data={bots.map(b => ({ value: String(b.id), label: b.name }))}
                        value={selectedBotId}
                        onChange={setSelectedBotId}
                    />

                    <Button mt="md" fullWidth onClick={handleBulkAssign} disabled={!selectedBotId}>
                        Назначить
                    </Button>
                </Stack>
            </Modal>
        </Container >
    );
}
