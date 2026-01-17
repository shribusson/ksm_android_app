import { Breadcrumbs, Anchor, Text, Group } from '@mantine/core';
import { Link } from 'react-router-dom';
import { IconChevronRight, IconHome } from '@tabler/icons-react';

interface BreadcrumbItem {
    title: string;
    href?: string;
}

interface BreadcrumbsNavProps {
    items: BreadcrumbItem[];
}

export function BreadcrumbsNav({ items }: BreadcrumbsNavProps) {
    return (
        <Breadcrumbs
            separator={<IconChevronRight size={14} style={{ opacity: 0.5 }} />}
            mb="md"
        >
            <Anchor component={Link} to="/" style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <IconHome size={14} />
            </Anchor>

            {items.map((item, index) => {
                const isLast = index === items.length - 1;

                return item.href && !isLast ? (
                    <Anchor key={index} component={Link} to={item.href} size="sm">
                        {item.title}
                    </Anchor>
                ) : (
                    <Text key={index} size="sm" c="dimmed">
                        {item.title}
                    </Text>
                );
            })}
        </Breadcrumbs>
    );
}
