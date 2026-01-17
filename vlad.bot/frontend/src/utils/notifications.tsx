import { notifications } from '@mantine/notifications';
import { IconCheck, IconX, IconInfoCircle, IconAlertTriangle } from '@tabler/icons-react';

export const showSuccess = (message: string, title = 'Успех') => {
    notifications.show({
        title,
        message,
        color: 'green',
        icon: <IconCheck size={ 18} />,
        autoClose: 3000,
  });
};

export const showError = (message: string, title = 'Ошибка') => {
    notifications.show({
        title,
        message,
        color: 'red',
        icon: <IconX size={ 18} />,
        autoClose: 5000,
  });
};

export const showInfo = (message: string, title = 'Информация') => {
    notifications.show({
        title,
        message,
        color: 'blue',
        icon: <IconInfoCircle size={ 18} />,
        autoClose: 4000,
  });
};

export const showWarning = (message: string, title = 'Внимание') => {
    notifications.show({
        title,
        message,
        color: 'yellow',
        icon: <IconAlertTriangle size={ 18} />,
        autoClose: 4000,
  });
};
