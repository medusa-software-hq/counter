import { Center, Stack, Text, Title } from '@mantine/core';

// Google Sign-In was removed for the AWS port (#121). Auth is a dev pass-through
// for now, so this wall is not reached; kept as a placeholder until Cognito lands.
export function SignInWall() {
  return (
    <Center mih="100svh">
      <Stack align="center" gap="md">
        <Title order={1}>Sign in</Title>
        <Text c="dimmed">Sign-in is temporarily disabled.</Text>
      </Stack>
    </Center>
  );
}
