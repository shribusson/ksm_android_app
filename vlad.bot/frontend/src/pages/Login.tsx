import { useState, FormEvent } from 'react';
import { Container, Title, TextInput, PasswordInput, Button, Paper, Stack, Text } from '@mantine/core';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

export function Login() {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);

        try {
            const formData = new URLSearchParams();
            formData.append('username', username);
            formData.append('password', password);

            const response = await axios.post('http://localhost:8000/auth/login', formData, {
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
            });

            localStorage.setItem('token', response.data.access_token);
            navigate('/');
        } catch (err: any) {
            setError(err.response?.data?.detail || 'Login failed');
        } finally {
            setLoading(false);
        }
    };

    return (
        <Container size={420} my={40}>
            <Title ta="center" order={2} mb="lg">
                Вход в систему
            </Title>

            <Paper withBorder shadow="md" p={30} radius="md">
                <form onSubmit={handleSubmit}>
                    <Stack>
                        <TextInput
                            label="Логин"
                            placeholder="admin"
                            required
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                        />

                        <PasswordInput
                            label="Пароль"
                            placeholder="admin"
                            required
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                        />

                        {error && <Text c="red" size="sm">{error}</Text>}

                        <Button type="submit" fullWidth loading={loading}>
                            Войти
                        </Button>

                        <Text size="xs" c="dimmed" ta="center">
                            Default: admin / admin
                        </Text>
                    </Stack>
                </form>
            </Paper>
        </Container>
    );
}

export default Login;
