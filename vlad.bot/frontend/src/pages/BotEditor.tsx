import { useCallback, useEffect, useState, useMemo, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
    ReactFlow,
    Controls,
    Background,
    useNodesState,
    useEdgesState,
    addEdge,
    ReactFlowProvider,
    BackgroundVariant,
    applyNodeChanges,
    applyEdgeChanges,
    reconnectEdge,
    useReactFlow,
    MiniMap
} from '@xyflow/react';
import type {
    Node,
    Edge,
    NodeChange,
    EdgeChange,
    Connection,
    OnSelectionChangeParams
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { Button, Group, Title, Loader, ActionIcon, Text, Modal, Stack, TextInput, ScrollArea, Box } from '@mantine/core';
import { IconArrowLeft, IconDeviceFloppy, IconRocket, IconPlayerPlay, IconSend } from '@tabler/icons-react';
import { useDisclosure } from '@mantine/hooks';


import { BreadcrumbsNav } from '../components/BreadcrumbsNav';
import { EditorToolbar } from '../components/EditorToolbar';
import { NodePalette } from '../components/NodePalette';

import api, { scriptsApi } from '../api';
import { useStore } from '../store';
import { PropertyPanel } from '../components/PropertyPanel';
import { MessageNode } from '../nodes/MessageNode';
import { QuestionNode } from '../nodes/QuestionNode';
import { StartNode } from '../nodes/StartNode';
import { QuestionSingleChoiceNode } from '../nodes/QuestionSingleChoiceNode';
import { ConditionNode } from '../nodes/ConditionNode';
import { EndNode } from '../nodes/EndNode';

const nodeTypes = {
    message: MessageNode,
    question: QuestionNode,
    start: StartNode,
    single_choice: QuestionSingleChoiceNode,
    condition: ConditionNode,
    end: EndNode,
};

let nodeIdCounter = 1;

function BotEditorContent() {
    const { botId } = useParams();
    const navigate = useNavigate();
    const [nodes, setNodes] = useNodesState([]);
    const [edges, setEdges, onEdgesChange] = useEdgesState([]);
    const [loading, setLoading] = useState(true);
    const [selectedNode, setSelectedNode] = useState<Node | null>(null);
    const [paletteHovered, setPaletteHovered] = useState(false);

    // ReactFlow instance from hook
    const { zoomIn, zoomOut, fitView, screenToFlowPosition } = useReactFlow();

    // Store setters (legacy support if needed, but we use hooks now)
    const setRfInstance = useStore((state) => state.setRfInstance);

    const [reconnectingEdgeId, setReconnectingEdgeId] = useState<string | null>(null);

    // Test Mode State
    const [testOpened, { open: openTest, close: closeTest }] = useDisclosure(false);
    const [chatSessionId, setChatSessionId] = useState<number | null>(null);
    const [chatMessages, setChatMessages] = useState<any[]>([]);
    const [chatInput, setChatInput] = useState('');
    const [chatLoading, setChatLoading] = useState(false);
    const chatViewport = useRef<HTMLDivElement>(null);

    const scrollToBottom = () => {
        if (chatViewport.current) {
            chatViewport.current.scrollTo({ top: chatViewport.current.scrollHeight, behavior: 'smooth' });
        }
    };

    useEffect(() => {
        scrollToBottom();
    }, [chatMessages, testOpened]);

    // Load script
    useEffect(() => {
        if (botId) {
            scriptsApi.getLatest(Number(botId))
                .then((script) => {
                    if (script?.graph_data) {
                        let loadedNodes = script.graph_data.nodes || [];
                        const loadedEdges = script.graph_data.edges || [];

                        if (!loadedNodes.find((n: Node) => n.type === 'start')) {
                            const startNode: Node = {
                                id: 'start-1',
                                type: 'start',
                                position: { x: 400, y: 150 }, // Adjusted position for new layout
                                data: { label: 'Start' },
                                deletable: false,
                            };
                            loadedNodes = [startNode, ...loadedNodes];
                        }

                        setNodes(loadedNodes);
                        setEdges(loadedEdges);

                        const maxId = Math.max(
                            0,
                            ...loadedNodes.map((n: Node) => {
                                const match = n.id.match(/\d+$/);
                                return match ? parseInt(match[0]) : 0;
                            })
                        );
                        nodeIdCounter = maxId + 1;
                    } else {
                        setNodes([{
                            id: 'start-1',
                            type: 'start',
                            position: { x: 250, y: 50 },
                            data: { label: 'Start' },
                            deletable: false,
                        }]);
                    }
                    setLoading(false);
                })
                .catch((err) => {
                    console.error('Failed to load script:', err);
                    setLoading(false);
                });
        }
    }, [botId, setNodes, setEdges]);

    // Auto-save
    useEffect(() => {
        if (!botId || nodes.length === 0) return;
        const timer = setTimeout(() => {
            const graphData = { nodes, edges };
            scriptsApi.saveDraft(Number(botId), graphData).catch(console.error);
        }, 1500);
        return () => clearTimeout(timer);
    }, [nodes, edges, botId]);

    // Validation
    const onReconnectStart = useCallback((_: any, edge: Edge) => {
        setReconnectingEdgeId(edge.id);
    }, []);

    const onReconnectEnd = useCallback(() => {
        setReconnectingEdgeId(null);
    }, []);

    const isValidConnection = useCallback((connection: Connection) => {
        // Prevent self-connections
        if (connection.source === connection.target) return false;

        // Target validation: Allow only one incoming connection per handle
        const targetOccupied = edges.some(e =>
            e.target === connection.target &&
            e.targetHandle === connection.targetHandle &&
            e.id !== reconnectingEdgeId
        );
        if (targetOccupied) return false;

        // Source validation: Allow only one outgoing connection per handle for Choice nodes (or generally strictly 1:1 if desired)
        const sourceOccupied = edges.some(e =>
            e.source === connection.source &&
            e.sourceHandle === connection.sourceHandle &&
            e.id !== reconnectingEdgeId
        );

        return !sourceOccupied;
    }, [edges, reconnectingEdgeId]);

    const onReconnect = useCallback((oldEdge: Edge, newConnection: Connection) => {
        setEdges((els) => reconnectEdge(oldEdge, newConnection, els));
    }, [setEdges]);

    const onNodesChange = useCallback(
        (changes: NodeChange[]) => {
            setNodes((nds) => applyNodeChanges(changes, nds));
        },
        [setNodes]
    );

    const onConnect = useCallback(
        (params: Connection) => setEdges((eds) => addEdge(params, eds)),
        [setEdges],
    );

    const onSelectionChange = useCallback(({ nodes }: OnSelectionChangeParams) => {
        if (nodes.length === 1) {
            setSelectedNode(nodes[0]);
        } else {
            setSelectedNode(null);
        }
    }, []);

    const onNodeUpdate = useCallback((id: string, data: any) => {
        setNodes((nds) =>
            nds.map((node) => {
                if (node.id === id) {
                    const updated = { ...node, data };
                    if (selectedNode?.id === id) {
                        setSelectedNode(updated);
                    }
                    return updated;
                }
                return node;
            })
        );
    }, [setNodes, selectedNode]);

    const onSave = useCallback(async () => {
        if (botId) {
            try {
                const graphData = { nodes, edges };
                await scriptsApi.saveDraft(Number(botId), graphData);
            } catch (err) {
                console.error('Save failed:', err);
                alert('Ошибка сохранения');
            }
        }
    }, [botId, nodes, edges]);

    const onPublish = useCallback(async () => {
        if (botId && confirm("Опубликовать эту версию сценария?")) {
            try {
                await scriptsApi.publish(Number(botId));
                alert('Сценарий успешно опубликован!');
            } catch (err) {
                console.error('Publish failed:', err);
                alert('Ошибка публикации');
            }
        }
    }, [botId]);

    const onDragOver = useCallback((event: any) => {
        event.preventDefault();
        event.dataTransfer.dropEffect = 'move';
    }, []);

    const onNodeDragStart = useCallback((event: any, node: Node) => {
        if (event.altKey) {
            const newNode = {
                ...node,
                id: `${node.type}-${nodeIdCounter++}`,
                data: { ...node.data, label: `${node.data.label || ''} (Copy)` },
                selected: false,
            };
            setNodes((nds) => nds.concat(newNode));
        }
    }, [setNodes]);

    const onDrop = useCallback(
        (event: any) => {
            event.preventDefault();
            const type = event.dataTransfer.getData('application/reactflow');
            if (!type) return;

            // Use screenToFlowPosition for accurate coordinates regardless of zoom/pan
            const position = screenToFlowPosition({
                x: event.clientX,
                y: event.clientY,
            });

            const newNode: Node = {
                id: `${type}-${nodeIdCounter++}`,
                type,
                position,
                data: { text: '', variable: '' },
            };

            setNodes((nds) => nds.concat(newNode));
            setSelectedNode(newNode);
        },
        [setNodes, screenToFlowPosition],
    );

    // Test Logic
    const handleStartTest = async () => {
        if (!botId) return;
        setChatLoading(true);
        setChatMessages([]);
        try {
            await onSave();
            const res = await api.post(`/bots/${botId}/chat/start`, {});
            setChatSessionId(res.data.session_id);
            if (res.data.messages) {
                setChatMessages(res.data.messages);
            }
            openTest();
        } catch (err) {
            alert('Ошибка запуска теста');
        } finally {
            setChatLoading(false);
        }
    };

    const handleSendChat = async () => {
        if (!chatInput.trim() || !botId || !chatSessionId) return;
        const text = chatInput;
        setChatInput('');
        setChatMessages(prev => [...prev, { text, sender: 'user' }]);
        try {
            const res = await api.post(`/bots/${botId}/chat/message`, {
                session_id: chatSessionId,
                text
            });
            if (res.data.messages) {
                setChatMessages(prev => [...prev, ...res.data.messages]);
            }
        } catch (err) {
            console.error(err);
        }
    };

    if (loading) {
        return (
            <div style={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Loader size="xl" />
                <Text mt="md">Загрузка...</Text>
            </div>
        );
    }

    return (
        <div style={{ height: '100vh', display: 'flex', flexDirection: 'column', background: '#fff' }}>
            {/* Header */}
            <div style={{
                height: '60px',
                borderBottom: '1px solid #e0e0e0',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '0 20px',
                background: '#fff',
                zIndex: 10
            }}>
                <Group>
                    <BreadcrumbsNav items={[
                        { title: 'Сценарии', href: '/' },
                        { title: nodes.find(n => n.type === 'start')?.data?.label || 'Редактор' }
                    ]} />

                    <div style={{ marginLeft: 20 }}>
                        <EditorToolbar
                            onAddNode={() => {
                                const id = `message-${nodeIdCounter++}`;
                                const newNode = {
                                    id,
                                    type: 'message',
                                    position: {
                                        x: (nodes.length * 20) + 100,
                                        y: (nodes.length * 20) + 100
                                    },
                                    data: { text: '' },
                                };
                                setNodes((nds) => nds.concat(newNode));
                                setSelectedNode(newNode);
                            }}
                            onZoomIn={() => zoomIn()}
                            onZoomOut={() => zoomOut()}
                            onFitView={() => fitView()}
                        />
                    </div>
                </Group>

                <Group>
                    <Button variant="light" color="grape" leftSection={<IconPlayerPlay size={16} />} onClick={handleStartTest} loading={chatLoading}>
                        Тест
                    </Button>
                    <Button variant="default" leftSection={<IconDeviceFloppy size={16} />} onClick={onSave}>
                        Сохранить
                    </Button>
                    <Button color="green" leftSection={<IconRocket size={16} />} onClick={onPublish}>
                        Опубликовать
                    </Button>
                </Group>
            </div>

            {/* Main Workspace with Absolute Positioning for Panels */}
            <div style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>

                {/* React Flow Canvas - Always 100% width */}
                <div style={{ width: '100%', height: '100%' }}>

                    {/* Warning for multiple Start nodes */}
                    {nodes.filter(n => n.type === 'start').length > 1 && (
                        <div style={{
                            position: 'absolute',
                            top: 10,
                            left: '50%',
                            transform: 'translateX(-50%)',
                            zIndex: 100,
                            background: '#fff3cd',
                            border: '1px solid #ffc107',
                            borderRadius: 8,
                            padding: '8px 16px',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 8,
                        }}>
                            <span>⚠️</span>
                            <span style={{ fontSize: 13, color: '#856404' }}>
                                Несколько Start блоков! Будет использован только первый.
                            </span>
                        </div>
                    )}

                    <ReactFlow
                        nodes={nodes}
                        edges={edges}
                        onNodesChange={onNodesChange}
                        onEdgesChange={onEdgesChange}
                        onConnect={onConnect}
                        onReconnect={onReconnect}
                        onReconnectStart={onReconnectStart}
                        onReconnectEnd={onReconnectEnd}
                        isValidConnection={isValidConnection}
                        nodeTypes={nodeTypes}
                        onInit={setRfInstance}
                        onDrop={onDrop}
                        onDragOver={onDragOver}
                        onNodeDragStart={onNodeDragStart}
                        onSelectionChange={onSelectionChange}
                        // Default Viewport settings
                        minZoom={0.1}
                        maxZoom={2}
                        fitView
                        snapToGrid
                        panOnScroll={true}
                        selectionKeyCode="Shift"
                        edgesFocusable={true}
                        edgesUpdatable={true}
                        defaultEdgeOptions={{
                            type: 'default',
                            animated: false,
                            style: { strokeWidth: 2, stroke: '#b1b1b7' }
                        }}
                        deleteKeyCode={["Backspace", "Delete"]}
                        multiSelectionKeyCode={["Control", "Meta", "Shift"]}
                        style={{ width: '100%', height: '100%' }}
                    >
                        <Background variant={BackgroundVariant.Dots} gap={16} size={1} />
                        <Controls showInteractive={false} />
                        <MiniMap
                            pannable
                            zoomable
                            nodeColor={(n) => {
                                switch (n.type) {
                                    case 'message': return '#228be6';
                                    case 'question': return '#fd7e14';
                                    case 'single_choice': return '#15aabf';
                                    case 'start': return '#40c057';
                                    case 'end': return '#fa5252';
                                    default: return '#eee';
                                }
                            }}
                        />
                    </ReactFlow>
                </div>

                {/* Node Palette - Floating/Absolute on Left */}
                <div
                    style={{
                        position: 'absolute',
                        top: 20,
                        left: 20,
                        bottom: 20,
                        zIndex: 30,
                        width: paletteHovered ? 240 : 60,
                        transition: 'width 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                        overflow: 'hidden',
                        borderRadius: '12px',
                        boxShadow: paletteHovered ? '0 8px 16px rgba(0,0,0,0.1)' : '0 2px 4px rgba(0,0,0,0.05)',
                        backgroundColor: 'white' // Ensure background is opaque
                    }}
                    onMouseEnter={() => setPaletteHovered(true)}
                    onMouseLeave={() => setPaletteHovered(false)}
                >
                    <NodePalette collapsed={!paletteHovered} />
                </div>

                {/* Property Panel - Absolute Overlay on Right */}
                {selectedNode && (
                    <div style={{
                        position: 'absolute',
                        top: 20,
                        right: 20,
                        bottom: 20,
                        width: '320px',
                        zIndex: 30,
                        background: '#fff',
                        borderRadius: '12px',
                        boxShadow: '0 8px 24px rgba(0,0,0,0.12)',
                        border: '1px solid #e0e0e0',
                        overflow: 'hidden', // Contain panel
                        animation: 'slideInRight 0.2s ease-out'
                    }}>
                        <style>{`
                            @keyframes slideInRight {
                                from { transform: translateX(20px); opacity: 0; }
                                to { transform: translateX(0); opacity: 1; }
                            }
                        `}</style>
                        <PropertyPanel
                            selectedNode={selectedNode}
                            onChange={onNodeUpdate}
                            onClose={() => setSelectedNode(null)}
                        />
                    </div>
                )}
            </div>

            {/* Test Chat Modal */}
            <Modal opened={testOpened} onClose={closeTest} title="Тестирование бота" size="lg" padding={0}>
                <div style={{ display: 'flex', flexDirection: 'column', height: '500px' }}>
                    <ScrollArea style={{ flex: 1 }} viewportRef={chatViewport} p="md">
                        <Stack gap="sm">
                            {chatMessages.map((msg, idx) => (
                                <Box
                                    key={idx}
                                    style={{
                                        alignSelf: msg.sender === 'user' ? 'flex-end' : 'flex-start',
                                        maxWidth: '80%'
                                    }}
                                >
                                    <div
                                        style={{
                                            background: msg.sender === 'user' ? '#228be6' : '#f1f3f5',
                                            color: msg.sender === 'user' ? 'white' : 'black',
                                            padding: '8px 12px',
                                            borderRadius: '8px',
                                            borderBottomRightRadius: msg.sender === 'user' ? 0 : 8,
                                            borderBottomLeftRadius: msg.sender === 'bot' ? 0 : 8
                                        }}
                                    >
                                        <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>{msg.text}</Text>
                                    </div>
                                    {msg.options && (
                                        <Group mt={4} gap={4}>
                                            {msg.options.map((opt: any, i: number) => (
                                                <Button
                                                    key={i}
                                                    size="compact-xs"
                                                    variant="outline"
                                                    onClick={() => {
                                                        setChatMessages(prev => [...prev, { text: opt.label, sender: 'user' }]);
                                                        api.post(`/bots/${botId}/chat/message`, { session_id: chatSessionId, text: opt.label })
                                                            .then(res => {
                                                                if (res.data.messages) setChatMessages(prev => [...prev, ...res.data.messages]);
                                                            });
                                                    }}
                                                >
                                                    {opt.label}
                                                </Button>
                                            ))}
                                        </Group>
                                    )}
                                </Box>
                            ))}
                        </Stack>
                    </ScrollArea>
                    <div style={{ padding: '10px', borderTop: '1px solid #eee', background: '#f8f9fa' }}>
                        <Group gap={5}>
                            <TextInput
                                style={{ flex: 1 }}
                                placeholder="Текст сообщения..."
                                value={chatInput}
                                onChange={(e) => setChatInput(e.target.value)}
                                onKeyDown={(e) => { if (e.key === 'Enter') handleSendChat(); }}
                            />
                            <ActionIcon size="lg" variant="filled" color="blue" onClick={handleSendChat}>
                                <IconSend size={18} />
                            </ActionIcon>
                        </Group>
                    </div>
                </div>
            </Modal>
        </div>
    );
}

export function BotEditor() {
    return (
        <ReactFlowProvider>
            <BotEditorContent />
        </ReactFlowProvider>
    );
}

export default BotEditor;
