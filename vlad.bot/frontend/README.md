# Vlad Bot - HR Automation Platform 🤖

Vlad Bot is a modern web application designed to automate HR processes through customizable chat bots. It features a powerful visual editor, analytics dashboard, and seamless employee management integration.

## ✨ Key Features

- **Visual Bot Editor**: Drag-and-drop interface powered by React Flow to build complex conversation flows without code.
- **Interactive Nodes**:
  - 💬 **Message**: Send text, images, files, or videos.
  - ❓ **Question**: Collect user input and save to variables.
  - 🔘 **Choice**: Create interactive buttons for branching logic.
  - 🔀 **Condition**: Route users based on variables and logic.
- **Templates**: Pre-made templates for Onboarding, Surveys, and Training.
- **Analytics**: Track bot performance and user responses in real-time.
- **Employee Management**: Assign bots to employees and track completion.
- **Modern UI**: Built with Mantine UI, featuring dark mode support (future) and smooth animations.

## 🛠️ Tech Stack

- **Frontend**: React 18, TypeScript, Vite
- **UI Framework**: Mantine v7
- **State Management**: Zustand
- **Flow Editor**: React Flow (@xyflow/react)
- **Animations**: Framer Motion
- **Icons**: Tabler Icons
- **Routing**: React Router DOM

## 🚀 Getting Started

### Prerequisites

- Node.js (v18+)
- npm or yarn

### Installation

1. Clone the repository:

   ```bash
   git clone <repository-url>
   cd frontend
   ```

2. Install dependencies:

   ```bash
   npm install
   ```

3. Start the development server:

   ```bash
   npm run dev
   ```

## 📂 Project Structure

```
src/
├── api/            # API client and endpoints
├── components/     # Reusable UI components (EditorToolbar, NodePalette, etc.)
├── nodes/          # Custom React Flow nodes
├── pages/          # Main application pages (BotList, Editor, Settings)
├── store/          # Zustand store
├── theme.ts        # Mantine theme configuration
└── utils/          # Helper functions (animations, notifications)
```

## 🎨 UI & UX

The application focuses on a premium user experience with:

- **Micro-interactions**: Smooth hover effects and transitions.
- **Feedback**: Toast notifications for all actions.
- **Onboarding**: Welcome screen for new users.
- **Empty States**: Helpful guides when data is missing.

---

Built with ❤️ by the Vlad Bot Team.
