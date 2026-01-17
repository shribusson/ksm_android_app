
import { create } from 'zustand';
import type { ReactFlowInstance } from '@xyflow/react';

interface AppState {
    currentBotId: number | null;
    setCurrentBotId: (id: number | null) => void;
    // React Flow instance for saving
    rfInstance: ReactFlowInstance | null;
    setRfInstance: (instance: ReactFlowInstance | null) => void;
}

export const useStore = create<AppState>((set) => ({
    currentBotId: null,
    setCurrentBotId: (id) => set({ currentBotId: id }),
    rfInstance: null,
    setRfInstance: (instance) => set({ rfInstance: instance }),
}));
