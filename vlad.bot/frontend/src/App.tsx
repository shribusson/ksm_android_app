import '@mantine/core/styles.css';
import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import { AppShell, Burger, Group, NavLink, Title, MantineProvider, createTheme } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconRobot, IconSettings, IconBriefcase, IconHierarchy2 } from '@tabler/icons-react';
import Login from './pages/Login';
import BotList from './pages/BotList';
import BotEditor from './pages/BotEditor';
import { EmployeeList } from './pages/EmployeeList'; // braces because named export
import Settings from './pages/Settings';
import BotResults from './pages/BotResults';

const theme = createTheme({});

function App() {
  const [opened, { toggle }] = useDisclosure();

  return (
    <MantineProvider theme={theme}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/bot/:botId" element={<BotEditor />} />
          <Route path="/bot/:botId/results" element={<BotResults />} />
          <Route path="/*" element={
            <AppShell
              header={{ height: 60 }}
              navbar={{ width: 300, breakpoint: 'sm', collapsed: { mobile: !opened } }}
              padding="md"
            >
              <AppShell.Header>
                <Group h="100%" px="md">
                  <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" />
                  <Title order={3}>HR Bot Platform</Title>
                </Group>
              </AppShell.Header>



              <AppShell.Navbar p="md">
                <NavLink label="Сценарии" leftSection={<IconHierarchy2 size={16} />} component={Link} to="/" />
                <NavLink label="Сотрудники" leftSection={<IconBriefcase size={16} />} component={Link} to="/employees" />
                <NavLink label="Настройки" leftSection={<IconSettings size={16} />} component={Link} to="/settings" />
              </AppShell.Navbar>

              <AppShell.Main>
                <Routes>
                  <Route path="/" element={<BotList />} />
                  <Route path="/employees" element={<EmployeeList />} />
                  <Route path="/settings" element={<Settings />} />
                </Routes>
              </AppShell.Main>
            </AppShell>
          } />


        </Routes>
      </BrowserRouter>
    </MantineProvider>
  );
}

export default App;
