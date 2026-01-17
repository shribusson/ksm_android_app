import { useEffect, useState, useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
    Table, Container, Title, Button, Group, Badge, Paper, ScrollArea, Loader, Text,
    Card, SimpleGrid, ThemeIcon, Stack, SegmentedControl, TextInput
} from '@mantine/core';
import { IconArrowLeft, IconDownload, IconUsers, IconCheck, IconClock, IconSearch, IconChartBar } from '@tabler/icons-react';
import { botsApi } from '../api';
import { BreadcrumbsNav } from '../components/BreadcrumbsNav';
import { EmptyState } from '../components/EmptyState';

interface ResultRow {
    session_id: number;
    state: string;
    started_at: string;
    finished_at: string | null;
    employee: {
        id: number;
        name: string;
        position: string | null;
        department: string | null;
        tags: string[];
    } | null;
    channel: string;
    external_id: string;
    answers: Record<string, string>;
}

export default function BotResults() {
    const { botId } = useParams();
    const [results, setResults] = useState<ResultRow[]>([]);
    const [loading, setLoading] = useState(true);
    const [columns, setColumns] = useState<string[]>([]);
    const [view, setView] = useState<'table' | 'stats'>('table');
    const [search, setSearch] = useState('');

    useEffect(() => {
        if (botId) {
            botsApi.getResults(Number(botId))
                .then((data) => {
                    setResults(data);

                    // Extract all unique question keys for columns
                    const keys = new Set<string>();
                    data.forEach((row: any) => {
                        Object.keys(row.answers || {}).forEach(k => keys.add(k));
                    });
                    setColumns(Array.from(keys).sort());
                })
                .catch(console.error)
                .finally(() => setLoading(false));
        }
    }, [botId]);

    // Filter by search
    const filteredResults = useMemo(() => {
        if (!search) return results;
        const q = search.toLowerCase();
        return results.filter(r =>
            r.employee?.name?.toLowerCase().includes(q) ||
            r.employee?.department?.toLowerCase().includes(q) ||
            r.employee?.position?.toLowerCase().includes(q) ||
            Object.values(r.answers || {}).some(v => v?.toLowerCase().includes(q))
        );
    }, [results, search]);

    // Stats
    const stats = useMemo(() => {
        const total = results.length;
        const finished = results.filter(r => r.state === 'finished').length;
        const active = results.filter(r => r.state === 'active').length;
        const withEmployee = results.filter(r => r.employee).length;
        return { total, finished, active, withEmployee };
    }, [results]);

    const handleExport = () => {
        if (botId) {
            window.location.href = `http://localhost:8000/bots/${botId}/export`;
        }
    };

    if (loading) return <Container mt="xl"><Loader /></Container>;

    return (
        <Container size="xl" mt="xl" pb="xl">
            <Group justify="space-between" mb="lg">
                <Group>
                    <BreadcrumbsNav items={[
                        { title: 'Сценарии', href: '/' },
                        { title: `Результаты #${botId}`, href: `/bot/${botId}` },
                        { title: 'Аналитика' }
                    ]} />
                </Group>

                <Group>
                    <SegmentedControl
                        value={view}
                        onChange={(v) => setView(v as 'table' | 'stats')}
                        data={[
                            { label: 'Таблица', value: 'table' },
                            { label: 'Аналитика', value: 'stats' },
                        ]}
                    />
                    <Button leftSection={<IconDownload size={16} />} onClick={handleExport} color="green">
                        Excel
                    </Button>
                </Group>
            </Group>

            {/* Stats Cards */}
            <SimpleGrid cols={4} mb="lg">
                <Card withBorder padding="md">
                    <Group>
                        <ThemeIcon size="lg" variant="light" color="blue">
                            <IconUsers size={20} />
                        </ThemeIcon>
                        <div>
                            <Text size="xl" fw={700}>{stats.total}</Text>
                            <Text size="sm" c="dimmed">Всего ответов</Text>
                        </div>
                    </Group>
                </Card>
                <Card withBorder padding="md">
                    <Group>
                        <ThemeIcon size="lg" variant="light" color="green">
                            <IconCheck size={20} />
                        </ThemeIcon>
                        <div>
                            <Text size="xl" fw={700}>{stats.finished}</Text>
                            <Text size="sm" c="dimmed">Завершено</Text>
                        </div>
                    </Group>
                </Card>
                <Card withBorder padding="md">
                    <Group>
                        <ThemeIcon size="lg" variant="light" color="yellow">
                            <IconClock size={20} />
                        </ThemeIcon>
                        <div>
                            <Text size="xl" fw={700}>{stats.active}</Text>
                            <Text size="sm" c="dimmed">В процессе</Text>
                        </div>
                    </Group>
                </Card>
                <Card withBorder padding="md">
                    <Group>
                        <ThemeIcon size="lg" variant="light" color="grape">
                            <IconChartBar size={20} />
                        </ThemeIcon>
                        <div>
                            <Text size="xl" fw={700}>{stats.total > 0 ? Math.round(stats.finished / stats.total * 100) : 0}%</Text>
                            <Text size="sm" c="dimmed">Конверсия</Text>
                        </div>
                    </Group>
                </Card>
            </SimpleGrid>

            {view === 'table' && (
                <>
                    <TextInput
                        placeholder="Поиск по имени, отделу, ответам..."
                        leftSection={<IconSearch size={16} />}
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        mb="md"
                    />

                    <Paper withBorder shadow="sm" p={0} radius="md">
                        <ScrollArea>
                            <Table striped highlightOnHover>
                                <Table.Thead>
                                    <Table.Tr>
                                        <Table.Th>Сотрудник</Table.Th>
                                        <Table.Th>Отдел</Table.Th>
                                        <Table.Th>Должность</Table.Th>
                                        <Table.Th>Теги</Table.Th>
                                        <Table.Th>Статус</Table.Th>
                                        <Table.Th>Дата</Table.Th>
                                        {columns.map(col => (
                                            <Table.Th key={col}>{col}</Table.Th>
                                        ))}
                                    </Table.Tr>
                                </Table.Thead>
                                <Table.Tbody>
                                    {filteredResults.length === 0 ? (
                                        <Table.Tr>
                                            <Table.Td colSpan={6 + columns.length}>
                                                <EmptyState
                                                    icon={<IconSearch />}
                                                    title="Ничего не найдено"
                                                    description="Попробуйте изменить параметры поиска"
                                                />
                                            </Table.Td>
                                        </Table.Tr>
                                    ) : (
                                        filteredResults.map((row) => (
                                            <Table.Tr key={row.session_id}>
                                                <Table.Td>
                                                    <Text fw={500}>{row.employee?.name || row.external_id}</Text>
                                                </Table.Td>
                                                <Table.Td>{row.employee?.department || '-'}</Table.Td>
                                                <Table.Td>{row.employee?.position || '-'}</Table.Td>
                                                <Table.Td>
                                                    <Group gap={4}>
                                                        {row.employee?.tags?.map((tag, i) => (
                                                            <Badge key={i} size="xs" variant="light">{tag}</Badge>
                                                        )) || '-'}
                                                    </Group>
                                                </Table.Td>
                                                <Table.Td>
                                                    <Badge color={row.state === 'finished' ? 'green' : 'yellow'} size="sm">
                                                        {row.state === 'finished' ? 'Завершен' : 'Активен'}
                                                    </Badge>
                                                </Table.Td>
                                                <Table.Td>
                                                    <Text size="sm">{new Date(row.started_at).toLocaleDateString('ru-RU')}</Text>
                                                    <Text size="xs" c="dimmed">{new Date(row.started_at).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}</Text>
                                                </Table.Td>
                                                {columns.map(col => (
                                                    <Table.Td key={col}>
                                                        {row.answers?.[col] || '-'}
                                                    </Table.Td>
                                                ))}
                                            </Table.Tr>
                                        ))
                                    )}
                                </Table.Tbody>
                            </Table>
                        </ScrollArea>
                    </Paper>
                </>
            )}

            {view === 'stats' && (
                <Paper withBorder p="xl" radius="md">
                    <Title order={4} mb="md">Аналитика по ответам</Title>

                    {columns.length === 0 ? (
                        <EmptyState
                            icon={<IconChartBar />}
                            title="Нет данных для анализа"
                            description="Пока никто не проходил этот опрос"
                        />
                    ) : (
                        <Stack gap="lg">
                            {columns.map(col => {
                                // Count answer frequencies
                                const counts: Record<string, number> = {};
                                results.forEach(r => {
                                    const val = r.answers?.[col];
                                    if (val) {
                                        counts[val] = (counts[val] || 0) + 1;
                                    }
                                });

                                const sortedEntries = Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 5);
                                const total = Object.values(counts).reduce((a, b) => a + b, 0);

                                return (
                                    <Card key={col} withBorder padding="md">
                                        <Text fw={500} mb="sm">{col}</Text>
                                        <Stack gap={4}>
                                            {sortedEntries.map(([answer, count]) => (
                                                <Group key={answer} justify="space-between">
                                                    <Text size="sm" style={{ flex: 1 }}>{answer}</Text>
                                                    <Group gap="xs">
                                                        <div style={{
                                                            width: 100,
                                                            height: 8,
                                                            background: '#e9ecef',
                                                            borderRadius: 4,
                                                            overflow: 'hidden'
                                                        }}>
                                                            <div style={{
                                                                width: `${(count / total) * 100}%`,
                                                                height: '100%',
                                                                background: '#228be6',
                                                                borderRadius: 4
                                                            }} />
                                                        </div>
                                                        <Text size="sm" c="dimmed" w={40} ta="right">{Math.round((count / total) * 100)}%</Text>
                                                        <Badge size="sm" variant="light">{count}</Badge>
                                                    </Group>
                                                </Group>
                                            ))}
                                        </Stack>
                                    </Card>
                                );
                            })}
                        </Stack>
                    )}
                </Paper>
            )}
        </Container>
    );
}
