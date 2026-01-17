import { createTheme, rem } from '@mantine/core';

export const theme = createTheme({
    primaryColor: 'blue',

    fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, sans-serif',
    fontFamilyMonospace: 'Monaco, Courier, monospace',
    headings: { fontFamily: 'Inter, sans-serif', fontWeight: '600' },

    radius: {
        xs: rem(4),
        sm: rem(6),
        md: rem(8),
        lg: rem(12),
        xl: rem(16),
    },

    spacing: {
        xs: rem(8),
        sm: rem(12),
        md: rem(16),
        lg: rem(24),
        xl: rem(32),
    },

    shadows: {
        sm: '0 2px 4px rgba(0, 0, 0, 0.05)',
        md: '0 4px 12px rgba(0, 0, 0, 0.08)',
        lg: '0 8px 24px rgba(0, 0, 0, 0.12)',
        xl: '0 12px 32px rgba(0, 0, 0, 0.15)',
    },

    colors: {
        // Vibrant brand colors
        brand: [
            '#E3F2FD',
            '#BBDEFB',
            '#90CAF9',
            '#64B5F6',
            '#42A5F5',
            '#2196F3', // primary (500)
            '#1E88E5',
            '#1976D2',
            '#1565C0',
            '#0D47A1',
        ],
    },

    components: {
        Button: {
            defaultProps: {
                radius: 'md',
            },
            styles: {
                root: {
                    transition: 'all 0.2s ease',
                    '&:hover': {
                        transform: 'translateY(-1px)',
                    },
                },
            },
        },
        Card: {
            defaultProps: {
                shadow: 'md',
                radius: 'lg',
                withBorder: true,
            },
        },
        TextInput: {
            defaultProps: {
                radius: 'md',
            },
        },
        Select: {
            defaultProps: {
                radius: 'md',
            },
        },
        Modal: {
            defaultProps: {
                radius: 'lg',
                centered: true,
            },
        },
    },
});
